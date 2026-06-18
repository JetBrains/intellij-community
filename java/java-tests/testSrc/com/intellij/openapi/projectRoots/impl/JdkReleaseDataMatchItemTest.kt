// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkItem
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkListParser
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkPlatform
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkPredicate
import com.intellij.testFramework.UsefulTestCase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files

/**
 * Tests for [JdkReleaseData.matchAgainstItem] for each [ExternalJavaConfigurationProvider] release data type.
 * JDK items are loaded from the real feed JSON (same as [com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkItemTest]).
 *
 * @see SdkmanrcWatcherLightTests for [SdkmanReleaseData.matchVersionString] tests
 */
class JdkReleaseDataMatchItemTest : UsefulTestCase() {

  private lateinit var allItems: List<JdkItem>

  override fun setUp() {
    super.setUp()
    val json = loadFeed("feed-v2-08-24.json")
    allItems = JdkListParser.parseJdkList(json, JdkPredicate(null, setOf(JdkPlatform("windows", "x86_64"))))
  }

  // region SdkmanReleaseData

  fun `test sdkman exact match against item`() {
    val item = findItem("Eclipse", "Temurin", "17.0.12")
    assertEquals(ReleaseDataMatching.EXACT_MATCH, SdkmanReleaseData.parse("17.0.12-tem")?.matchAgainstItem(item))
  }

  fun `test sdkman feature match against item`() {
    val item = findItem("Eclipse", "Temurin", "17.0.12")
    assertEquals(ReleaseDataMatching.FEATURE_MATCH, SdkmanReleaseData.parse("17.0.0-tem")?.matchAgainstItem(item))
  }

  fun `test sdkman no match on variant against item`() {
    val item = findItem("Azul", "Zulu Community™", "17.0.12")
    assertEquals(ReleaseDataMatching.NO_MATCH, SdkmanReleaseData.parse("17.0.12-tem")?.matchAgainstItem(item))
  }

  fun `test sdkman no match on version against item`() {
    val item = findItem("Eclipse", "Temurin", "21.0.4")
    assertEquals(ReleaseDataMatching.NO_MATCH, SdkmanReleaseData.parse("17.0.12-tem")?.matchAgainstItem(item))
  }

  fun `test sdkman graalce exact match against item`() {
    val item = findItem("GraalVM", "Community Edition", "21.0.2")
    assertEquals(ReleaseDataMatching.EXACT_MATCH, SdkmanReleaseData.parse("21.0.2-graalce")?.matchAgainstItem(item))
  }

  fun `test sdkman graal exact match against item`() {
    val item = findItem("Oracle", "GraalVM", "17.0.12")
    assertEquals(ReleaseDataMatching.EXACT_MATCH, SdkmanReleaseData.parse("17.0.12-graal")?.matchAgainstItem(item))
  }

  fun `test sdkman candidates matching against items`() {
    for ((candidate, triple) in mapOf(
      "18.0.2-amzn" to Triple("Amazon", "Corretto", "18.0.2"),
      "17.0.11-jbr" to Triple("JetBrains", "Runtime", "17.0.11"),
      "17.0.12-librca" to Triple("BellSoft", "Liberica JDK", "17.0.12"),
      "22.0.2-sapmchn" to Triple("SAP", "SapMachine", "22.0.2"),
      "17.0.9-sem" to Triple("IBM", "Semeru", "17.0.9"),
      "17.0.12-tem" to Triple("Eclipse", "Temurin", "17.0.12"),
      "17.0.12-zulu" to Triple("Azul", "Zulu Community™", "17.0.12"),
      "21.0.2-graalce" to Triple("GraalVM", "Community Edition", "21.0.2"),
      "17.0.12-graal" to Triple("Oracle", "GraalVM", "17.0.12"),
    )) {
      val (vendor, product, version) = triple
      val item = findItem(vendor, product, version)
      assertEquals("$candidate doesn't exactly match $vendor $product $version",
                   ReleaseDataMatching.EXACT_MATCH,
                   SdkmanReleaseData.parse(candidate)?.matchAgainstItem(item))
    }
  }

  // endregion

  // region AsdfReleaseData

  fun `test asdf exact match against item`() {
    val item = findItem("Eclipse", "Temurin", "17.0.12")
    assertEquals(ReleaseDataMatching.EXACT_MATCH, AsdfReleaseData.parse("temurin-17.0.12")?.matchAgainstItem(item))
  }

  fun `test asdf feature match against item`() {
    val item = findItem("Eclipse", "Temurin", "17.0.12")
    assertEquals(ReleaseDataMatching.FEATURE_MATCH, AsdfReleaseData.parse("temurin-17")?.matchAgainstItem(item))
  }

  fun `test asdf no match on variant against item`() {
    val item = findItem("Eclipse", "Temurin", "17.0.12")
    assertEquals(ReleaseDataMatching.NO_MATCH, AsdfReleaseData.parse("zulu-17.0.12")?.matchAgainstItem(item))
  }

  fun `test asdf no match on version against item`() {
    val item = findItem("Eclipse", "Temurin", "21.0.4")
    assertEquals(ReleaseDataMatching.NO_MATCH, AsdfReleaseData.parse("temurin-17.0.12")?.matchAgainstItem(item))
  }

  fun `test asdf graalvm community exact match against item`() {
    val item = findItem("GraalVM", "Community Edition", "21.0.2")
    assertEquals(ReleaseDataMatching.EXACT_MATCH, AsdfReleaseData.parse("graalvm-community-21.0.2")?.matchAgainstItem(item))
  }

  fun `test asdf candidates matching against items`() {
    for ((candidate, triple) in mapOf(
      "corretto-18.0.2" to Triple("Amazon", "Corretto", "18.0.2"),
      "jetbrains-17.0.11" to Triple("JetBrains", "Runtime", "17.0.11"),
      "liberica-17.0.12" to Triple("BellSoft", "Liberica JDK", "17.0.12"),
      "sapmachine-22.0.2" to Triple("SAP", "SapMachine", "22.0.2"),
      "semeru-openj9-17.0.9" to Triple("IBM", "Semeru", "17.0.9"),
      "temurin-17.0.12" to Triple("Eclipse", "Temurin", "17.0.12"),
      "zulu-17.0.12" to Triple("Azul", "Zulu Community™", "17.0.12"),
      "graalvm-community-21.0.2" to Triple("GraalVM", "Community Edition", "21.0.2"),
      "oracle-graalvm-17.0.12" to Triple("Oracle", "GraalVM", "17.0.12"),
    )) {
      val (vendor, product, version) = triple
      val item = findItem(vendor, product, version)
      assertEquals("$candidate doesn't exactly match $vendor $product $version",
                   ReleaseDataMatching.EXACT_MATCH,
                   AsdfReleaseData.parse(candidate)?.matchAgainstItem(item))
    }
  }

  // endregion

  /**
   * Finds the first [JdkItem] from the feed matching the given [vendor], [product], and [version].
   */
  private fun findItem(vendor: String, product: String, version: String): JdkItem {
    return allItems.first { it.product.vendor == vendor && it.product.product == product && it.jdkVersion == version }
  }

  private fun loadFeed(name: String): JsonObject {
    val rawData = Files.readString(PathManagerEx.findFileUnderCommunityHome("platform/lang-impl/testData/jdkDownload/$name").toPath())
    return Json.decodeFromString<JsonElement>(rawData).jsonObject
  }
}
