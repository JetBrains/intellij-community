// This file is used by Jewel gradle script, check community/platform/jewel

plugins {
  jewel
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
  api(libs.kotlinx.serialization.core)
  api(libs.kotlinx.coroutines.core)
}
