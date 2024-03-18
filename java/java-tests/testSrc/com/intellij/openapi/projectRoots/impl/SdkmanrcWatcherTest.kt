// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.VfsTestUtil.createFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking

class SdkmanrcWatcherHeavyTests : HeavyPlatformTestCase() {
  override fun setUp() {
    super.setUp()

    val graal21 = IdeaTestUtil.createMockJdk("GraalVM CE 23.1.2 - Java 21.0.2", "")
    SdkConfigurationUtil.addSdk(graal21)

    val jbr17 = IdeaTestUtil.createMockJdk("JetBrains Runtime 17.0.7", "")
    SdkConfigurationUtil.addSdk(jbr17)

    val open11 = IdeaTestUtil.createMockJdk("Oracle OpenJDK 11.0.2", "")
    SdkConfigurationUtil.addSdk(open11)
  }

  fun `test jdk suggestion after sdkmanrc change`() {
    runBlocking {
      val scope = childScope()
      try {
        val watcher = SdkmanrcWatcherService(project, scope)

        assert(watcher.suggestSdkFromSdkmanrc() == null)

        createFile(getOrCreateProjectBaseDir(), ".sdkmanrc", "java=21.0.2-graalce")
        val suggestion1 = watcher.suggestSdkFromSdkmanrc()
        assert(suggestion1 is SdkmanrcWatcherService.SdkSuggestion.Jdk)
        assert((suggestion1 as SdkmanrcWatcherService.SdkSuggestion.Jdk).jdk.versionString == "GraalVM CE 23.1.2 - Java 21.0.2")

        createFile(getOrCreateProjectBaseDir(), ".sdkmanrc", "java=11.0.2-open")
        val suggestion2 = watcher.suggestSdkFromSdkmanrc()
        assert(suggestion2 is SdkmanrcWatcherService.SdkSuggestion.Jdk)
        assert((suggestion2 as SdkmanrcWatcherService.SdkSuggestion.Jdk).jdk.versionString == "Oracle OpenJDK 11.0.2")

        createFile(getOrCreateProjectBaseDir(), ".sdkmanrc", "java=17.0.7-jbr")
        val suggestion3 = watcher.suggestSdkFromSdkmanrc()
        assert(suggestion3 is SdkmanrcWatcherService.SdkSuggestion.Jdk)
        assert((suggestion3 as SdkmanrcWatcherService.SdkSuggestion.Jdk).jdk.versionString == "JetBrains Runtime 17.0.7")
      }
      catch (_: Exception) {}
      finally {
        scope.cancel()
      }
    }
  }

  override fun tearDown() {
    try {
      runWriteActionAndWait {
        ProjectJdkTable.getInstance().apply {
          allJdks.forEach { removeJdk(it) }
        }
      }
    } catch (e: Exception) {
      addSuppressedException(e)
    } finally {
      super.tearDown()
    }
  }
}

class SdkmanrcWatcherLightTests : BasePlatformTestCase() {

  fun `test candidates parsing`() {
    assertEquals(SdkmanCandidate.parse("8"),
                 SdkmanCandidate("8", "8", null, ""))

    assertEquals(SdkmanCandidate.parse("19-zulu"),
                 SdkmanCandidate("19-zulu", "19", null, "zulu"))

    assertEquals(SdkmanCandidate.parse("11.0.17-tem"),
                 SdkmanCandidate("11.0.17-tem", "11.0.17", null, "tem"))

    assertEquals(SdkmanCandidate.parse("11.0.9.fx-librca"),
                 SdkmanCandidate("11.0.9.fx-librca", "11.0.9", "fx", "librca"))

    assertEquals(SdkmanCandidate.parse("16.0.1.hs-adpt"),
                 SdkmanCandidate("16.0.1.hs-adpt", "16.0.1", "hs", "adpt"))
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
        SdkmanCandidate.parse(candidate)?.matchVersionString(version) == true
      ) { "$candidate doesn't match $version" }
    }
  }
}