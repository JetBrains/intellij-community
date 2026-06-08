// This file is used by Jewel gradle script, check community/platform/jewel

plugins {
  kotlin("jvm")
  alias(libs.plugins.kotlinx.serialization)
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

  test {
    kotlin {
      setSrcDirs(listOf("test"))
    }
  }
}

dependencies {
  api("org.jetbrains:annotations:26.0.2")
  api(project(":jb-icons-api"))
  api(project(":jb-icons-api-rendering"))
  api("org.jetbrains.kotlinx:kotlinx-serialization-core:1.11.0")
  api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}
