// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.util.BuildNumber
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.ListAssert
import org.assertj.core.api.ObjectAssert
import org.intellij.lang.annotations.Language
import org.junit.Assert
import org.junit.Test
import java.nio.file.Files

class JdkListTest {
  private val om = ObjectMapper()

  private fun buildPredicate(build: String = "203.123", archs: Set<String> = setOf("x86_64")): JdkPredicate {
    return JdkPredicate(BuildNumber.fromString(build)!!, archs.map { JdkPlatform("any", it) }.toSet())
  }

  @Test
  fun `parse feed v1`() {
    val json = loadTestData("feed-v1.json")
    assertSingleItemForEachOS(json)
  }

  @Test
  fun `parse feed v2 lists aarch64 macos`() {
    val json = loadTestData("feed-v2.json")

    val predicate = JdkPredicate(ideBuildNumber = BuildNumber.fromString("203.123")!!,
                                 supportedPlatforms = setOf(JdkPlatform("macOS", "aarch64")),
    )

    val items = JdkListParser.parseJdkList(json, predicate)
    //there must be only M1 builds
    Assert.assertEquals(31, items.size)
  }

  @Test
  fun `parse feed v2 lists aarch64 windows`() {
    val json = loadTestData("feed-v2.json")

    val predicate = JdkPredicate(ideBuildNumber = BuildNumber.fromString("203.123")!!,
                                 supportedPlatforms = setOf(JdkPlatform("windows", "aarch64")),
    )

    val items = JdkListParser.parseJdkList(json, predicate)
    Assert.assertEquals("$items", 9, items.size)
  }

  @Test
  fun `parse feed v2 lists aarch64 linux`() {
    val json = loadTestData("feed-v2.json")

    val predicate = JdkPredicate(ideBuildNumber = BuildNumber.fromString("203.123")!!,
                                 supportedPlatforms = setOf(JdkPlatform("linux", "aarch64")),
    )

    val items = JdkListParser.parseJdkList(json, predicate)
    Assert.assertEquals("$items", 36, items.size)
  }

  @Test
  fun `parse feed v2 lists M1 and Intel`() {
    val json = loadTestData("feed-v2.json")

    val predicate = JdkPredicate(ideBuildNumber = BuildNumber.fromString("203.123")!!,
                                 supportedPlatforms = setOf(JdkPlatform("macOS", "aarch64"), JdkPlatform("macOS", "x86_64")),
    )

    val items = JdkListParser.parseJdkList(json, predicate)
    //there must be only M1 builds
    Assert.assertEquals("$items", 71, items.size)
  }

  @Test
  fun `parse feed with products filter`() {
    val json = loadTestData("feed-v1.json")
    assertSingleItemForEachOS(json.patchEveryPackage { it.putObject("filter").buildRange(since = "192.123") })
  }

  @Test
  fun `parse feed with filter products empty`() {
    val json = loadTestData("feed-v1.json")
    assertNoItemsForEachOS(json.patchEveryProduct { it.putObject("filter").buildRange(until = "192.123") })
  }

  @Test
  fun `parse feed with filter products unknown`() {
    val json = loadTestData("feed-v1.json")
    assertNoItemsForEachOS(json.patchEveryProduct { it.putObject("filter").put("type", "unknown") })
  }

  @Test
  fun `parse feed with filter packages`() {
    val json = loadTestData("feed-v1.json")
    assertSingleItemForEachOS(json.patchEveryPackage { it.putObject("filter").buildRange(since = "192.123") })
  }

  @Test
  fun `parse feed with filter packages empty`() {
    val json = loadTestData("feed-v1.json")
    assertNoItemsForEachOS(json.patchEveryPackage { it.putObject("filter").buildRange(until = "192.123") })
  }

  @Test
  fun `parse feed with filter packages unknown`() {
    val json = loadTestData("feed-v1.json")
    assertNoItemsForEachOS(json.patchEveryPackage { it.putObject("filter").put("type", "unknown") })
  }

  @Test
  fun `parse feed with default bool`() {
    val json = loadTestData("feed-v1.json")
    assertSingleItemForEachOS(json.patchEveryProduct { it.remove("default") }) { this.returns(false) { it.isDefaultItem } }
  }

  @Test
  fun `parse feed with default bool true`() {
    val json = loadTestData("feed-v1.json")
    assertSingleItemForEachOS(json.patchEveryProduct { it.put("default", true) }) { this.returns(true) { it.isDefaultItem } }
  }

  @Test
  fun `parse feed with default bool const true`() {
    val json = loadTestData("feed-v1.json")
    assertSingleItemForEachOS(json.patchEveryProduct { it.putObject("default").put("type", "const").put("value", true) }) {
      this.returns(true) { it.isDefaultItem }
    }
  }

  @Test
  fun `parse feed with default bool false`() {
    val json = loadTestData("feed-v1.json")
    assertSingleItemForEachOS(json.patchEveryProduct { it.put("default", false) }) { this.returns(false) { it.isDefaultItem } }
  }

  @Test
  fun `parse feed with default bool const false`() {
    val json = loadTestData("feed-v1.json")
    assertSingleItemForEachOS(json.patchEveryProduct { it.putObject("default").put("type", "const").put("value", false) }) {
      this.returns(false) { it.isDefaultItem }
    }
  }

  @Test
  fun `parse feed with default bool filter true`() {
    val json = loadTestData("feed-v1.json")
    assertSingleItemForEachOS(json.patchEveryProduct { it.putObject("default").buildRange(since = "192.112") }) {
      this.returns(true) { it.isDefaultItem }
    }
  }

  @Test
  fun `parse feed with default bool filter false`() {
    val json = loadTestData("feed-v1.json")
    assertSingleItemForEachOS(json.patchEveryProduct { it.putObject("default").buildRange(until = "192.112") }) {
      this.returns(false) { it.isDefaultItem }
    }
  }

  @Test
  fun `skip unknown package types`() {
    val json = loadTestData("feed-v1.json")
    assertNoItemsForEachOS(json.patchEveryPackage { it.put("package_type", "unexpected_jet_jar") })
  }

  @Test
  fun `visible_for_ui is missing`() {
    val json = loadTestData("feed-v1.json")
    assertSingleItemForEachOS(json) { this.returns(true) { it.isVisibleOnUI } }
  }

  @Test
  fun `visible_for_ui is false`() {
    val json = loadTestData("feed-v1.json")
    assertSingleItemForEachOS(json.patchEveryProduct { it.put("listed", false) }) { this.returns(false) { it.isVisibleOnUI } }
  }

  @Test
  fun `visible_for_ui is true`() {
    val json = loadTestData("feed-v1.json")
    assertSingleItemForEachOS(json.patchEveryProduct { it.put("listed", true) }) { this.returns(true) { it.isVisibleOnUI } }
  }

  @Test
  fun `visible_for_ui is const true`() {
    val json = loadTestData("feed-v1.json")
    assertSingleItemForEachOS(json.patchEveryProduct { it.putObject("listed").put("type", "const").put("value", true) }) {
      this.returns(true) { it.isVisibleOnUI }
    }
  }

  @Test
  fun `visible_for_ui is buildRange`() {
    val json = loadTestData("feed-v1.json")
    assertSingleItemForEachOS(json.patchEveryProduct { it.putObject("listed").buildRange(since = "192.1") }) {
      this.returns(true) { it.isVisibleOnUI }
    }
  }

  @Test
  fun `visible_for_ui is !buildRange`() {
    val json = loadTestData("feed-v1.json")
    assertSingleItemForEachOS(json.patchEveryProduct { it.putObject("listed").buildRange(until = "192.1") }) {
      this.returns(false) { it.isVisibleOnUI }
    }
  }

  private inline fun assertSingleItemForEachOS(json: JsonObject, assert: ObjectAssert<JdkItem>.() -> Unit = {}) = assertForEachOS(json) {
    this.size().isOne
    this.first().assert()
  }

  private fun assertNoItemsForEachOS(json: JsonObject) = assertForEachOS(json) {
    this.size().isZero
  }

  private inline fun assertForEachOS(json: JsonObject, assert: ListAssert<JdkItem>.() -> Unit) {
    for (osType in listOf("windows", "linux", "macOS")) {
      val archs: Set<JdkPlatform> = setOf(JdkPlatform(osType,"x86_64"))
      val predicate = JdkPredicate(BuildNumber.fromString("201.123")!!, archs)
      val data = JdkListParser.parseJdkList(json, predicate)
      assertThat(data)
        .withFailMessage("should have items for $osType")
        .assert()
    }
  }

  private fun ObjectNode.buildRange(since: String? = null, until: String? = null): JsonObject {
    put("type", "build_number_range")
    since?.let { put("since", it) }
    until?.let { put("until", it) }
    return Json.decodeFromString(om.writeValueAsString(this))
  }

  @Test
  fun `filter by build range bi`() {
    val obj = om.createObjectNode().buildRange("192.123", "193.333")
    assertPredicate(obj, "192.100", false)
    assertPredicate(obj, "192.122", false)
    assertPredicate(obj, "192.123", true)
    assertPredicate(obj, "192.222", true)
    assertPredicate(obj, "193.222", true)
    assertPredicate(obj, "193.333", true)
    assertPredicate(obj, "193.334", false)
    assertPredicate(obj, "201.334", false)
  }

  @Test
  fun `filter by build range left`() {
    val obj = om.createObjectNode().buildRange(since = "192.123")

    assertPredicate(obj, "192.100", false)
    assertPredicate(obj, "192.122", false)
    assertPredicate(obj, "192.123", true)
    assertPredicate(obj, "193.222", true)
    assertPredicate(obj, "201.334", true)
  }

  @Test
  fun `filter by build range right`() {
    val obj = om.createObjectNode().buildRange(until = "193.333")
    assertPredicate(obj, "192.123", true)
    assertPredicate(obj, "192.222", true)
    assertPredicate(obj, "193.222", true)
    assertPredicate(obj, "193.333", true)
    assertPredicate(obj, "193.334", false)
    assertPredicate(obj, "201.334", false)
  }

  @Test
  fun `filter and`() {
    val obj = om.createObjectNode()
    obj.put("type", "and")
    val items = obj.putArray("items")
    items.addObject().buildRange(since = "192.111", until = "192.333")
    items.addObject().buildRange(since = "192.222", until = "192.655")

    val n = Json.decodeFromString<JsonElement>(om.writeValueAsString(obj))
    assertPredicate(n, "192.100", false)
    assertPredicate(n, "192.123", false)
    assertPredicate(n, "192.222", true)
    assertPredicate(n, "192.444", false)
    assertPredicate(n, "194.777", false)
  }

  @Test
  fun `filter and empty`() {
    assertPredicate(data = """
        {
          "type": "and",
          "items": []
        }
      """.trimIndent(), ideBuild = "192.100", expected = false)
  }

  @Test
  fun `filter or`() {
    val obj = om.createObjectNode()
    obj.put("type", "or")
    val items = obj.putArray("items")
    items.addObject().buildRange(since = "192.111", until = "192.333")
    items.addObject().buildRange(since = "193.444", until = "193.555")

    val n = Json.decodeFromString<JsonElement>(om.writeValueAsString(obj))
    assertPredicate(n, "192.100", false)
    assertPredicate(n, "192.123", true)
    assertPredicate(n, "193.000", false)
    assertPredicate(n, "193.456", true)
    assertPredicate(n, "194.777", false)
  }

  @Test
  fun `filter or empty`() {
    assertPredicate("""
    {
      "type": "or",
      "items": []
    }
    """.trimIndent(), "192.100", false)
  }

  @Test
  fun `filter not`() {
    val obj = om.createObjectNode()
    obj.put("type", "not")
    obj.putObject("item").buildRange(since = "192.111", until = "192.333")

    val n = Json.decodeFromString<JsonElement>(om.writeValueAsString(obj))
    assertPredicate(n, "192.100", true)
    assertPredicate(n, "192.123", false)
    assertPredicate(n, "192.444", true)
  }

  @Test
  fun `filter true`() {
    assertPredicate("""true""", "192.100", true)
    assertPredicate("""false""", "192.100", false)
  }

  @Test
  fun `filter const true`() {
    assertPredicate(data = """{"type": "const", "value":  true}""", "192.100", true)
    assertPredicate(data = """{"type": "const", "value":  false}""", "192.100", false)
  }

  @Test
  fun `filter unknown type`() {
    assertPredicate(data = """{"type": "wtf"}""", "192.100", null)
  }

  @Test
  fun `filter array`() {
    assertPredicate(data = """["type"]""", ideBuild = "192.100", expected = null)
  }

  @Test
  fun `filter obj`() {
    assertPredicate(data = """{"x": "type"}""", ideBuild = "192.100", expected = null)
  }

  private fun assertPredicate(@Language("JSON") data: String, ideBuild: String, expected: Boolean?) {
    assertPredicate(obj = Json.decodeFromString(data), ideBuild = ideBuild, expected = expected)
  }

  private fun assertPredicate(obj: JsonElement, ideBuild: String, expected: Boolean?) {
    val testPredicate = buildPredicate(ideBuild).testPredicate(obj)
    @Suppress("JSON_FORMAT_REDUNDANT")
    assertThat(testPredicate)
      .withFailMessage("Expected \"$expected\" but was \"$testPredicate\" with $ideBuild from:\n${Json { prettyPrint = true }.encodeToString(obj)}")
      .isEqualTo(expected)
  }

  // patch every JDK product
  private inline fun ObjectNode.patchEveryProduct(patchItem: (ObjectNode) -> Unit): ObjectNode {
    return apply {
      this["jdks"].forEach { patchItem(it as ObjectNode) }
    }
  }

  private inline fun JsonObject.patchEveryProduct(patchItem: (ObjectNode) -> Unit): JsonObject {
    val jacksonNode = om.readTree(Json.encodeToString(this)) as ObjectNode
    jacksonNode["jdks"].forEach { patchItem(it as ObjectNode) }
    return Json.decodeFromString(om.writeValueAsString(jacksonNode))
  }

  // patch every JDK package for every JDK item
  private inline fun ObjectNode.patchEveryPackage(patchPackage: (ObjectNode) -> Unit): ObjectNode {
    return apply {
      patchEveryProduct { product -> product["packages"].forEach { patchPackage(it as ObjectNode) } }
    }
  }

  // patch every JDK package for every JDK item
  private inline fun JsonObject.patchEveryPackage(patchPackage: (ObjectNode) -> Unit): JsonObject {
    val jacksonNode = om.readTree(Json.encodeToString(this)) as ObjectNode
    jacksonNode.patchEveryProduct { product -> product["packages"].forEach { patchPackage(it as ObjectNode) } }
    return Json.decodeFromString(om.writeValueAsString(jacksonNode))
  }

  private fun loadTestData(@Suppress("SameParameterValue") name: String): JsonObject {
    val rawData = Files.readString(PathManagerEx.findFileUnderCommunityHome("platform/lang-impl/testData/jdkDownload/$name").toPath())
    return Json.decodeFromString<JsonElement>(rawData).jsonObject
  }
}
