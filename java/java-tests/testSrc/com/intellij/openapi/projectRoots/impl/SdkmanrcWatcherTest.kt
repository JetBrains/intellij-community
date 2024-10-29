// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking

class SdkmanrcWatcherHeavyTests : ExternalJavaConfigurationTest() {
  override val mockJdkVersions: List<String> = listOf(
    "GraalVM CE 23.1.2 - Java 21.0.2",
    "Oracle OpenJDK 11.0.2",
    "JetBrains Runtime 17.0.7",
  )

  fun `test jdk suggestion after sdkmanrc change`() {
    runBlocking {
      val scope = childScope("")
      try {
        val watcher = ExternalJavaConfigurationService(project, scope)
        val configProvider = SdkmanrcConfigurationProvider()

        assert(watcher.getReleaseData(configProvider) == null)

        checkSuggestion(watcher, configProvider, "java=21.0.2-graalce", "GraalVM CE 23.1.2 - Java 21.0.2")
        checkSuggestion(watcher, configProvider, "java=11.0.2-open", "Oracle OpenJDK 11.0.2")
        checkSuggestion(watcher, configProvider, "java=17.0.7-jbr", "JetBrains Runtime 17.0.7")
      }
      catch (_: Exception) {}
      finally {
        scope.cancel()
      }
    }
  }
}

class SdkmanrcWatcherLightTests : BasePlatformTestCase() {

  fun `test candidates parsing`() {
    assertEquals(SdkmanReleaseData.parse("8"),
                 SdkmanReleaseData("8", "8", null, ""))

    assertEquals(SdkmanReleaseData.parse("19-zulu"),
                 SdkmanReleaseData("19-zulu", "19", null, "zulu"))

    assertEquals(SdkmanReleaseData.parse("11.0.17-tem"),
                 SdkmanReleaseData("11.0.17-tem", "11.0.17", null, "tem"))

    assertEquals(SdkmanReleaseData.parse("11.0.9.fx-librca"),
                 SdkmanReleaseData("11.0.9.fx-librca", "11.0.9", "fx", "librca"))

    assertEquals(SdkmanReleaseData.parse("16.0.1.hs-adpt"),
                 SdkmanReleaseData("16.0.1.hs-adpt", "16.0.1", "hs", "adpt"))
  }

  fun `test candidates matching`() {
    for ((candidate, version) in mapOf(
      "11.0.11.hs-adpt" to "AdoptOpenJDK (HotSpot) 11.0.11",
      "8.0.275.j9-adpt" to "AdoptOpenJDK (OpenJ9) 8.0.275",
      "18.0.2-amzn" to "Amazon Corretto 18.0.2",
      "17.0.7-jbr" to "JetBrains Runtime 17.0.7",
      "17.0.3-librca" to "BellSoft Liberica 17.0.3",
      "17.0.1-oracle" to "Oracle OpenJDK 17.0.1",
      "17.0.2-open" to "Oracle OpenJDK 17.0.2",
      "17.0.1-sapmchn" to "SAP SapMachine 17.0.1",
      "11.0.12-sem" to "IBM Semeru 11.0.12",
      "17.0.7-tem" to "Eclipse Temurin 17.0.7",
      "8.0.352-zulu" to "Azul Zulu 8.0.352",

      "21.0.2-graalce" to "GraalVM CE 23.1.2 - Java 21.0.2",
      "17.0.10-graal" to "GraalVM 23.0.3 - Java 17.0.10",
      "22.ea.2-open" to "Oracle OpenJDK 22",
    )) {
      assert(
        SdkmanReleaseData.parse(candidate)?.matchVersionString(version) == true
      ) { "$candidate doesn't match $version" }
    }
  }
}