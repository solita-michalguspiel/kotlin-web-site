package builds.apiReferences.buildTypes

import jetbrains.buildServer.configs.kotlin.FailureAction
import jetbrains.buildServer.configs.kotlin.Template
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.buildSteps.placeholder
import jetbrains.buildServer.configs.kotlin.buildSteps.script

val rootTask = """
  tasks.named(\"dokkaHtmlMultiModule\") {
    pluginsMapConfiguration.set([\"org.jetbrains.dokka.base.DokkaBase\": \"\"\"{ \"templatesDir\": \"${"\\$"}{
      projectDir.toString().replace('\\\', '/')
    }/dokka-templates\" }\"\"\"])
  }
""".trimIndent()

object DokkaReferenceTemplate : Template({
  name = "Dokka Reference Template"

  artifactRules = "build/dokka/htmlMultiModule/** => pages.zip"

  steps {
    script {
      name = "Patch the root gradle script"
      scriptContent = """
        echo "$rootTask" >> build.gradle
      """.trimIndent()
    }

    placeholder {  }

    script {
      name = "Drop SNAPSHOT word for deploy"
      scriptContent = """
                #!/bin/bash
                if [ %teamcity.build.branch.is_default% == "true" ]; then
                  CURRENT_VERSION="$(sed -E s/^v?//g <<<%release.tag%)"
                	sed -i -E "s/^version=.+(-SNAPSHOT)?/version=${'$'}CURRENT_VERSION/gi" ./gradle.properties
                fi
            """.trimIndent()
      dockerImage = "debian"
    }

    gradle {
      name = "Build dokka html"
      tasks = "dokkaHtmlMultiModule"
    }
  }

  requirements {
    contains("docker.server.osType", "linux")
  }

  params {
    param("teamcity.vcsTrigger.runBuildInNewEmptyBranch", "true")
  }

  dependencies {
    dependency(PrepareCustomDokkaTemplates) {
      snapshot {
        onDependencyFailure = FailureAction.CANCEL
        onDependencyCancel = FailureAction.CANCEL
      }

      artifacts {
        artifactRules = "+:dokka-templates/** => dokka-templates"
      }
    }
  }
})
