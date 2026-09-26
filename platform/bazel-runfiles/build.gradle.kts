// This file is used by Jewel Gradle script, check community/platform/jewel

plugins {
  kotlin("jvm")
}

val jdkLevel =
  (project.findProperty("jdk.level") as? String)?.toIntOrNull()
    ?: error("jdk.level must be provided by the Jewel root build")

kotlin {
  jvmToolchain(jdkLevel)
}

sourceSets {
  main {
    kotlin {
      setSrcDirs(listOf("src"))
    }
  }
}

dependencies {
  implementation(kotlin("stdlib"))
  implementation("org.jetbrains:annotations:26.0.2")
}
