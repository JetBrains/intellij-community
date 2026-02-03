rootProject.name = "rhizomedb-compiler-plugin"

pluginManagement {
  // the compiler plugin will be built by this Kotlin compiler
  val KOTLIN_VERSION = "2.3.10-RC"

  plugins {
    kotlin("jvm") version KOTLIN_VERSION
  }
  resolutionStrategy {
    eachPlugin {
      repositories {
        gradlePluginPortal()
        if ("SNAPSHOT" in KOTLIN_VERSION || "dev" in KOTLIN_VERSION) {
          maven("https://packages.jetbrains.team/maven/p/kt/bootstrap")
          mavenLocal()
        }
      }
    }
  }
  repositories {
    gradlePluginPortal()
    if ("SNAPSHOT" in KOTLIN_VERSION || "dev" in KOTLIN_VERSION) {
      maven("https://packages.jetbrains.team/maven/p/kt/bootstrap")
      mavenLocal()
    }
  }
}
