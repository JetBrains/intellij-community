// This file is used by Jewel gradle script, check community/platform/jewel

plugins {
  kotlin("jvm")
  kotlin("plugin.serialization") version "1.9.0"
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
  api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}