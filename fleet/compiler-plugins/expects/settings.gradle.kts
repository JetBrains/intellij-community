rootProject.name = "expects-compiler-plugin"

pluginManagement {
  // the compiler plugin will be built by this Kotlin compiler
  val KOTLIN_VERSION = "2.4.0-RC"

  plugins {
    kotlin("jvm") version KOTLIN_VERSION
  }
  resolutionStrategy {
    eachPlugin {
      repositories {
        gradlePluginPortal()
        // !! configuration for builds as a Kotlin user project
        //    please do not change it before discussing it in #ij-monorepo-kotlin
        maven("https://packages.jetbrains.team/maven/p/kt/bootstrap/") // periodic dev-builds of the Kotlin compiler (stable availability)
        maven("https://packages.jetbrains.team/maven/p/kt/dev/") // per-commit dev-builds of the Kotlin compiler (unpublished after 1-2 weeks)
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/") // special dev-builds of the Kotlin compiler for IntelliJ
        if ("SNAPSHOT" in KOTLIN_VERSION || KOTLIN_VERSION.count { it == '-' } > 1) { // e.g., X.Y.Z-SNAPSHOT, X.Y.Z-dev-1234, X.Y.Z-ReleaseN-1234
          mavenLocal()
        }
      }
    }
  }
  repositories {
    gradlePluginPortal()
    // !! configuration for builds as a Kotlin user project
    //    please do not change it before discussing it in #ij-monorepo-kotlin
    maven("https://packages.jetbrains.team/maven/p/kt/bootstrap/") // periodic dev-builds of the Kotlin compiler (stable availability)
    maven("https://packages.jetbrains.team/maven/p/kt/dev/") // per-commit dev-builds of the Kotlin compiler (unpublished after 1-2 weeks)
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/") // special dev-builds of the Kotlin compiler for IntelliJ
    if ("SNAPSHOT" in KOTLIN_VERSION || KOTLIN_VERSION.count { it == '-' } > 1) { // e.g., X.Y.Z-SNAPSHOT, X.Y.Z-dev-1234, X.Y.Z-ReleaseN-1234
      mavenLocal()
    }
  }
}
