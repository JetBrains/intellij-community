package com.intellij.ide.starters.local.gradle

import com.intellij.ide.starters.local.GeneratorAsset
import com.intellij.ide.starters.local.GeneratorResourceFile

class GradleResourcesProvider {
  fun getGradlewResources(): List<GeneratorAsset> {
    return listOf(
      GeneratorResourceFile("gradle/wrapper/gradle-wrapper.jar", javaClass.getResource("/assets/gradlew/gradle-wrapper.jar.bin")!!),
      GeneratorResourceFile("gradlew", javaClass.getResource("/assets/gradlew/gradlew.bin")!!),
      GeneratorResourceFile("gradlew.bat", javaClass.getResource("/assets/gradlew/gradlew.bat.bin")!!)
    )
  }
}