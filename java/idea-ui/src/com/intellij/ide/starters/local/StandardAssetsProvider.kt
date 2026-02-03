// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starters.local

import java.nio.file.attribute.PosixFilePermission

class StandardAssetsProvider {
  val gradleWrapperPropertiesLocation: String
    get() = "gradle/wrapper/gradle-wrapper.properties"

  val mavenWrapperPropertiesLocation: String
    get() = ".mvn/wrapper/maven-wrapper.properties"

  fun getGradlewAssets(): List<GeneratorAsset> {
    return listOf(
      GeneratorResourceFile(
        relativePath = "gradle/wrapper/gradle-wrapper.jar",
        resource = javaClass.getResource("/assets/gradlew/gradle-wrapper.jar.bin")!!
      ),
      GeneratorResourceFile(
        permissions = setOf(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_EXECUTE),
        relativePath = "gradlew",
        resource = javaClass.getResource("/assets/gradlew/gradlew.bin")!!
      ),
      GeneratorResourceFile(
        relativePath = "gradlew.bat",
        resource = javaClass.getResource("/assets/gradlew/gradlew.bat.bin")!!
      )
    )
  }

  fun getGradleIgnoreAssets(): List<GeneratorAsset> {
    return listOf(
      GeneratorResourceFile(
        relativePath = ".gitignore",
        resource = javaClass.getResource("/assets/ignore/gradle.gitignore.txt")!!
      )
    )
  }

  fun getIntelliJIgnoreAssets(): List<GeneratorAsset> {
    return listOf(
      GeneratorResourceFile(
        relativePath = ".gitignore",
        resource = javaClass.getResource("/assets/ignore/intellij.gitignore.txt")!!
      )
    )
  }

  fun getMvnwAssets(): List<GeneratorAsset> {
    return listOf(
      GeneratorResourceFile(
        relativePath = ".mvn/wrapper/maven-wrapper.jar",
        resource = javaClass.getResource("/assets/mvnw/maven-wrapper.jar.bin")!!
      ),
      GeneratorResourceFile(
        permissions = setOf(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_EXECUTE),
        relativePath = "mvnw",
        resource = javaClass.getResource("/assets/mvnw/mvnw.bin")!!
      ),
      GeneratorResourceFile(
        relativePath = "mvnw.cmd",
        resource = javaClass.getResource("/assets/mvnw/mvnw.cmd.bin")!!
      )
    )
  }

  fun getMavenIgnoreAssets(): List<GeneratorAsset> {
    return listOf(
      GeneratorResourceFile(
        relativePath = ".gitignore",
        resource = javaClass.getResource("/assets/ignore/maven.gitignore.txt")!!
      )
    )
  }
}