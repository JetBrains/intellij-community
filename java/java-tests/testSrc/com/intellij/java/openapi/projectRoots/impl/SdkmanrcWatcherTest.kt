// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.openapi.projectRoots.impl

import com.intellij.openapi.projectRoots.impl.ExternalJavaConfigurationService
import com.intellij.openapi.projectRoots.impl.ReleaseDataMatching
import com.intellij.openapi.projectRoots.impl.SdkmanReleaseData
import com.intellij.openapi.projectRoots.impl.SdkmanrcConfigurationProvider
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.cancel
import org.jetbrains.jps.model.java.JdkVersionDetector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

@Disabled("AT-3959")
@TestApplication
class SdkmanrcWatcherHeavyTests {
  private val fixture by externalJavaConfigurationFixture(listOf(
    "GraalVM CE 23.1.2 - Java 21.0.2",
    "Oracle OpenJDK 11.0.2",
    "JetBrains Runtime 17.0.7",
  ))
  private val project get() = fixture.project

  @Test
  fun `test jdk suggestion after sdkmanrc change`() {
    timeoutRunBlocking {
      val scope = childScope("")
      try {
        val watcher = ExternalJavaConfigurationService(project, scope)
        val configProvider = SdkmanrcConfigurationProvider()

        assert(watcher.getReleaseData(configProvider) == null)

        fixture.checkSuggestion(watcher, configProvider, "java=21.0.2-graalce", "GraalVM CE 23.1.2 - Java 21.0.2")
        fixture.checkSuggestion(watcher, configProvider, "java=11.0.2-open", "Oracle OpenJDK 11.0.2")
        fixture.checkSuggestion(watcher, configProvider, "java=17.0.7-jbr", "JetBrains Runtime 17.0.7")
      }
      catch (_: Exception) {}
      finally {
        scope.cancel()
      }
    }
  }
}

class SdkmanrcWatcherLightTests {

  @Test
  fun `test candidates parsing`() {
    assertEquals(SdkmanReleaseData.parse("8"),
                 SdkmanReleaseData("8", "8", null, null))

    assertEquals(SdkmanReleaseData.parse("19-zulu"),
                 SdkmanReleaseData("19-zulu", "19", null, "zulu"))

    assertEquals(SdkmanReleaseData.parse("11.0.17-tem"),
                 SdkmanReleaseData("11.0.17-tem", "11.0.17", null, "tem"))

    assertEquals(SdkmanReleaseData.parse("11.0.9.fx-librca"),
                 SdkmanReleaseData("11.0.9.fx-librca", "11.0.9", "fx", "librca"))

    assertEquals(SdkmanReleaseData.parse("16.0.1.hs-adpt"),
                 SdkmanReleaseData("16.0.1.hs-adpt", "16.0.1", "hs", "adpt"))
  }

  /**
   * Identifiers as printed by `sdk list java`.
   */
  @Test
  fun `test identifiers parsing`() {
    assertEquals(SdkmanReleaseData.parse("21.0.12+1.1-tem"),
                 SdkmanReleaseData("21.0.12+1.1-tem", "21.0.12", "1.1", "tem"))

    assertEquals(SdkmanReleaseData.parse("28.0.0+ea.11-open"),
                 SdkmanReleaseData("28.0.0+ea.11-open", "28.0.0", "ea.11", "open"))

    assertEquals(SdkmanReleaseData.parse("26.0.2-fx+1.1-librca"),
                 SdkmanReleaseData("26.0.2-fx+1.1-librca", "26.0.2", "fx+1.1", "librca"))

    assertEquals(SdkmanReleaseData.parse("26.0.2.fx-zulu"),
                 SdkmanReleaseData("26.0.2.fx-zulu", "26.0.2", "fx", "zulu"))

    assertEquals(SdkmanReleaseData.parse("11.0.14.1-jbr"),
                 SdkmanReleaseData("11.0.14.1-jbr", "11.0.14.1", null, "jbr"))

    assertEquals(SdkmanReleaseData.parse("25.0.4+1.1.r25-nik"),
                 SdkmanReleaseData("25.0.4+1.1.r25-nik", "25.0.4", "1.1.r25", "nik"))

    assertEquals(SdkmanReleaseData.parse("8.0.504+1-librca"),
                 SdkmanReleaseData("8.0.504+1-librca", "8.0.504", "1", "librca"))

    assertNull(SdkmanReleaseData.parse("current"))
  }

  @Test
  fun `test identifiers versions and variants`() {
    for ((identifier, expected) in mapOf(
      "26.0.2-amzn" to (JdkVersionDetector.Variant.Corretto to 26),
      "25.2.4-graalce" to (JdkVersionDetector.Variant.GraalVMCE to 25),
      "25.0.4-graal" to (JdkVersionDetector.Variant.GraalVM to 25),
      "28.0.0+ea.11-open" to (JdkVersionDetector.Variant.Oracle to 28),
      "25.0.4-jbr" to (JdkVersionDetector.Variant.JBR to 25),
      "26.0.2-fx+1.1-librca" to (JdkVersionDetector.Variant.Liberica to 26),
      "25.0.4+1.1.r25-nik" to (JdkVersionDetector.Variant.Liberica to 25),
      "25.0.4+1-ms" to (JdkVersionDetector.Variant.Microsoft to 25),
      "26.0.2-oracle" to (JdkVersionDetector.Variant.Oracle to 26),
      "26.0.2+1-sapmchn" to (JdkVersionDetector.Variant.SapMachine to 26),
      "26.0.2-sem" to (JdkVersionDetector.Variant.Semeru to 26),
      "21.0.12+1.1-tem" to (JdkVersionDetector.Variant.Temurin to 21),
      "25.0.4+1-kona" to (JdkVersionDetector.Variant.Kona to 25),
      "26.0.2+1.1-zulu" to (JdkVersionDetector.Variant.Zulu to 26),
      "8.0.504+1-librca" to (JdkVersionDetector.Variant.Liberica to 8),
    )) {
      val releaseData = SdkmanReleaseData.parse(identifier)
      assertNotNull(releaseData, "$identifier is not parsed")
      val (variant, feature) = expected
      assertEquals(variant, releaseData!!.variant, "$identifier is not parsed as $variant")
      assertEquals(feature, releaseData.javaVersion?.feature, "$identifier is not parsed as Java $feature")
    }
  }

  @Test
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
      assertEquals(ReleaseDataMatching.EXACT_MATCH,
                   SdkmanReleaseData.parse(candidate)?.matchVersionString(version),
                   "$candidate doesn't exactly match $version")
    }
  }

  @Test
  fun `test exact match`() {
    assertEquals(ReleaseDataMatching.EXACT_MATCH,
                 SdkmanReleaseData.parse("17.0.7-tem")?.matchVersionString("Eclipse Temurin 17.0.7"))
  }

  @Test
  fun `test major match`() {
    assertEquals(ReleaseDataMatching.FEATURE_MATCH,
                 SdkmanReleaseData.parse("17.0.0-tem")?.matchVersionString("Eclipse Temurin 17.0.7"))
  }

  @Test
  fun `test no match on variant`() {
    assertEquals(ReleaseDataMatching.NO_MATCH,
                 SdkmanReleaseData.parse("17.0.7-tem")?.matchVersionString("Azul Zulu 17.0.7"))
  }

  @Test
  fun `test no match on version`() {
    assertEquals(ReleaseDataMatching.NO_MATCH,
                 SdkmanReleaseData.parse("17.0.7-tem")?.matchVersionString("Eclipse Temurin 21.0.2"))
  }
}