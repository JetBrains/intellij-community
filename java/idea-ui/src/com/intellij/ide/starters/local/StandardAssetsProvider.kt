// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starters.local

class StandardAssetsProvider {
  val gradleWrapperPropertiesLocation: String
    get() = "gradle/wrapper/gradle-wrapper.properties"

  val mavenWrapperPropertiesLocation: String
    get() = ".mvn/wrapper/maven-wrapper.properties"

  fun getGradlewAssets(): List<GeneratorAsset> {
    return listOf(
      GeneratorResourceFile("gradle/wrapper/gradle-wrapper.jar", javaClass.getResource("/assets/gradlew/gradle-wrapper.jar.bin")!!),
      GeneratorResourceFile("gradlew", javaClass.getResource("/assets/gradlew/gradlew.bin")!!),
      GeneratorResourceFile("gradlew.bat", javaClass.getResource("/assets/gradlew/gradlew.bat.bin")!!)
    )
  }

  fun getGradleIgnoreAssets(): List<GeneratorAsset> {
    return listOf(
      GeneratorResourceFile(".gitignore", javaClass.getResource("/assets/ignore/gradle.gitignore.txt")!!)
    )
  }

  fun getIntelliJIgnoreAssets(): List<GeneratorAsset> {
    return listOf(
      GeneratorResourceFile(".gitignore", javaClass.getResource("/assets/ignore/intellij.gitignore.txt")!!)
    )
  }

  fun getMvnwAssets(): List<GeneratorAsset> {
    return listOf(
      GeneratorResourceFile(".mvn/wrapper/maven-wrapper.jar", javaClass.getResource("/assets/mvnw/maven-wrapper.jar.bin")!!),
      GeneratorResourceFile("mvnw", javaClass.getResource("/assets/mvnw/mvnw.bin")!!),
      GeneratorResourceFile("mvnw.cmd", javaClass.getResource("/assets/mvnw/mvnw.cmd.bin")!!)
    )
  }

  fun getMavenIgnoreAssets(): List<GeneratorAsset> {
    return listOf(
      GeneratorResourceFile(".gitignore", javaClass.getResource("/assets/ignore/maven.gitignore.txt")!!)
    )
  }
}