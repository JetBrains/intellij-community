// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.openapi.application.ex.PathManagerEx
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jetbrains.jps.model.java.JdkVersionDetector
import org.junit.Test
import java.nio.file.Files

class JdkItemTest {
  @Test
  fun `test variant detection`() {
    val json = loadTestData("feed-v2-08-24.json")
    val jdks = JdkListParser
      .parseJdkList(json, JdkPredicate(null, setOf(JdkPlatform("windows", "x86_64"))))
      .map { it.fullPresentationWithVendorText }
      .toSet()

    for (jdk in jdks) {
      val variant = JdkItem.detectVariant(jdk)
      assert(variant != JdkVersionDetector.Variant.Unknown) { "Unknown variant detected for $jdk" }
    }
  }

  private fun loadTestData(@Suppress("SameParameterValue") name: String): JsonObject {
    val rawData = Files.readString(PathManagerEx.findFileUnderCommunityHome("platform/lang-impl/testData/jdkDownload/$name").toPath())
    return Json.decodeFromString<JsonElement>(rawData).jsonObject
  }
}