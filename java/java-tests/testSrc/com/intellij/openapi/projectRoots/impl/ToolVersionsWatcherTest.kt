// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking

class ToolVersionsWatcherHeavyTests : ExternalJavaConfigurationTest() {
  override val mockJdkVersions: List<String> = listOf(
    "GraalVM CE 23.1.2 - Java 21.0.2",
    "Eclipse Temurin 17.0.11",
  )

  fun `test jdk suggestion after sdkmanrc change`() {
    runBlocking {
      val scope = childScope("")
      try {
        val watcher = ExternalJavaConfigurationService(project, scope)
        val configProvider = ToolVersionsConfigurationProvider()

        assert(watcher.getReleaseData(configProvider) == null)

        checkSuggestion(watcher, configProvider, "java graalvm-community-21.0.2", "GraalVM CE 23.1.2 - Java 21.0.2")
        checkSuggestion(watcher, configProvider,
                        """
                          # Configuration for asdf
                          java temurin-17.0.11+9
                          nodejs 20.15.1
                          """.trimIndent(), "Eclipse Temurin 17.0.11")
      }
      catch (_: Exception) {}
      finally {
        scope.cancel()
      }
    }
  }
}

class ToolVersionsWatcherLightTests : BasePlatformTestCase() {

  fun `test candidates parsing`() {
    assertEquals(AsdfReleaseData.parse("8"),
                 null)

    assertEquals(AsdfReleaseData.parse("temurin-20.0.0+36"),
                 AsdfReleaseData("temurin-20.0.0+36", "temurin", "20.0.0"))

    assertEquals(AsdfReleaseData.parse("sapmachine-17.0.7-snapshot.1"),
                 AsdfReleaseData("sapmachine-17.0.7-snapshot.1", "sapmachine", "17.0.7"))

    assertEquals(AsdfReleaseData.parse("zulu-11.66.15_1"),
                 AsdfReleaseData("zulu-11.66.15_1", "zulu", "11.66.15"))

    assertEquals(AsdfReleaseData.parse("liberica-18.0.2.1+1"),
                 AsdfReleaseData("liberica-18.0.2.1+1", "liberica", "18.0.2.1"))
  }

  fun `test candidates matching`() {
    for ((candidate, version) in mapOf(
      //"corretto-8.342.07.3" to "Amazon Corretto 1.8.0_342",
      //"liberica-8u292+10" to "BellSoft Liberica 1.8.0_292",
      //"zulu-8.66.0.15" to "Azul Zulu 8.0.352",
      "corretto-17.0.10.7.1" to "Amazon Corretto 17.0.10",
      "jetbrains-17.0.5b469.71" to "JetBrains Runtime 17.0.5",
      "liberica-17.0.3+7" to "BellSoft Liberica 17.0.3",
      "openjdk-17.0.1" to "Oracle OpenJDK 17.0.1",
      "sapmachine-17.0.1" to "SAP SapMachine 17.0.1",
      "semeru-openj9-17.0.4+8_openj9-0.33.0" to "IBM Semeru 17.0.4",
      "temurin-17.0.7+7" to "Eclipse Temurin 17.0.7",
      "adoptopenjdk-21.0.2+13.0.LTS" to "AdoptOpenJDK (HotSpot) 21.0.2",

      "graalvm-community-21.0.2" to "GraalVM CE 23.1.2 - Java 21.0.2",
    )) {
      assert(
        AsdfReleaseData.parse(candidate)?.matchVersionString(version) == true
      ) { "$candidate doesn't match $version" }
    }
  }
}