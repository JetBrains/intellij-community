rootProject.name = "expects-compiler-plugin"

pluginManagement {
  // the compiler plugin will be built by this Kotlin compiler
  val KOTLIN_VERSION = "2.2.20"

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
