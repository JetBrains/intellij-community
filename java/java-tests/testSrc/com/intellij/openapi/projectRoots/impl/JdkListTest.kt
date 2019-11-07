// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.jdkDownloader.JdkItem
import com.intellij.jdkDownloader.JdkListDownloader
import com.intellij.jdkDownloader.JdkPredicate
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.util.BuildNumber
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.ListAssert
import org.assertj.core.api.ObjectAssert
import org.junit.Test
import java.io.File

class JdkListTest {
  private val om = ObjectMapper()
  private fun buildPredicate(build: String) = JdkPredicate(BuildNumber.fromString(build), "any")

  @Test
  fun `parse feed v1`() {
    val json = loadTestData("feed-v1.json")
    assertSingleItemForEachOS(json)
  }

  @Test
  fun `parse feed with products filter`() {
    val json = loadTestData("feed-v1.json")
    json.patchEveryPackage { it.putObject("filter").buildRange(since = "192.123") }
    assertSingleItemForEachOS(json)
  }

  @Test
  fun `parse feed with filter products empty`() {
    val json = loadTestData("feed-v1.json")
    json.patchEveryProduct { it.putObject("filter").buildRange(until = "192.123") }
    assertNoItemsForEachOS(json)
  }

  @Test
  fun `parse feed with filter products unknown`() {
    val json = loadTestData("feed-v1.json")
    json.patchEveryProduct { it.putObject("filter").put("type", "unknown") }
    assertNoItemsForEachOS(json)
  }

  @Test
  fun `parse feed with filter packages`() {
    val json = loadTestData("feed-v1.json")
    json.patchEveryPackage { it.putObject("filter").buildRange(since = "192.123") }
    assertSingleItemForEachOS(json)
  }

  @Test
  fun `parse feed with filter packages empty`() {
    val json = loadTestData("feed-v1.json")
    json.patchEveryPackage { it.putObject("filter").buildRange(until = "192.123") }
    assertNoItemsForEachOS(json)
  }

  @Test
  fun `parse feed with filter packages unknown`() {
    val json = loadTestData("feed-v1.json")
    json.patchEveryPackage { it.putObject("filter").put("type", "unknown") }
    assertNoItemsForEachOS(json)
  }

  @Test
  fun `parse feed with default bool`() {
    val json = loadTestData("feed-v1.json")
    json.patchEveryProduct { it.remove("default") }
    assertSingleItemForEachOS(json) { this.returns(false) { it.isDefaultItem } }
  }

  @Test
  fun `parse feed with default bool true`() {
    val json = loadTestData("feed-v1.json")
    json.patchEveryProduct { it.put("default", true) }
    assertSingleItemForEachOS(json) { this.returns(true) { it.isDefaultItem } }
  }

  @Test
  fun `parse feed with default bool false`() {
    val json = loadTestData("feed-v1.json")
    json.patchEveryProduct { it.put("default", false) }
    assertSingleItemForEachOS(json) { this.returns(false) { it.isDefaultItem } }
  }

  @Test
  fun `parse feed with default bool filter true`() {
    val json = loadTestData("feed-v1.json")
    json.patchEveryProduct { it.putObject("default").buildRange(since = "192.112") }
    assertSingleItemForEachOS(json) { this.returns(true) { it.isDefaultItem } }
  }

  @Test
  fun `parse feed with default bool filter false`() {
    val json = loadTestData("feed-v1.json")
    json.patchEveryProduct { it.putObject("default").buildRange(until = "192.112") }
    assertSingleItemForEachOS(json) { this.returns(false) { it.isDefaultItem } }
  }

  @Test
  fun `skip unknown package types`() {
    val json = loadTestData("feed-v1.json")
    json.patchEveryPackage { it.put("package_type", "unexpected_jet_jar") }
    assertNoItemsForEachOS(json)
  }

  private inline fun assertSingleItemForEachOS(json: ObjectNode, assert: ObjectAssert<JdkItem>.() -> Unit = {}) = assertForEachOS(json) {
    this.size().isOne
    this.first().assert()
  }

  private fun assertNoItemsForEachOS(json: ObjectNode) = assertForEachOS(json) {
    this.size().isZero
  }

  private inline fun assertForEachOS(json: ObjectNode, assert: ListAssert<JdkItem>.() -> Unit) {
    for (osType in listOf("windows", "linux", "macOS")) {
      val predicate = JdkPredicate(BuildNumber.fromString("201.123"), osType)
      val data = JdkListDownloader.parseJdkList(json, predicate)
      assertThat(data)
        .withFailMessage("should have items for $osType")
        .assert()
    }
  }

  private fun ObjectNode.buildRange(since: String? = null, until: String? = null) = apply {
    put("type", "build_number_range")
    since?.let { put("since", it) }
    until?.let { put("until", it) }
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

    assertPredicate(obj, "192.100", false)
    assertPredicate(obj, "192.123", false)
    assertPredicate(obj, "192.222", true)
    assertPredicate(obj, "192.444", false)
    assertPredicate(obj, "194.777", false)
  }

  @Test
  fun `filter and empty`() {
    val obj = om.createObjectNode()
    obj.put("type", "and")
    obj.putArray("items")

    assertPredicate(obj, "192.100", false)
  }

  @Test
  fun `filter or`() {
    val obj = om.createObjectNode()
    obj.put("type", "or")
    val items = obj.putArray("items")
    items.addObject().buildRange(since = "192.111", until = "192.333")
    items.addObject().buildRange(since = "193.444", until = "193.555")

    assertPredicate(obj, "192.100", false)
    assertPredicate(obj, "192.123", true)
    assertPredicate(obj, "193.000", false)
    assertPredicate(obj, "193.456", true)
    assertPredicate(obj, "194.777", false)
  }

  @Test
  fun `filter or empty`() {
    val obj = om.createObjectNode()
    obj.put("type", "or")
    obj.putArray("items")

    assertPredicate(obj, "192.100", false)
  }

  @Test
  fun `filter not`() {
    val obj = om.createObjectNode()
    obj.put("type", "not")
    obj.putObject("item").buildRange(since = "192.111", until = "192.333")

    assertPredicate(obj, "192.100", true)
    assertPredicate(obj, "192.123", false)
    assertPredicate(obj, "192.444", true)
  }

  @Test
  fun `filter true`() {
    assertPredicate(om.nodeFactory.booleanNode(true), "192.100", true)
    assertPredicate(om.nodeFactory.booleanNode(false), "192.100", false)
  }

  private fun assertPredicate(obj: JsonNode, ideBuild: String, expected: Boolean?) {
    val testPredicate = buildPredicate(ideBuild).testPredicate(obj)
    assertThat(testPredicate)
      .withFailMessage("Expected \"$expected\" but was \"$testPredicate\" with $ideBuild from:\n${obj.toPrettyString()}")
      .isEqualTo(expected)
  }

  // patch every JDK product
  private inline fun ObjectNode.patchEveryProduct(patchItem: (ObjectNode) -> Unit) = apply {
    this["jdks"].forEach { patchItem(it as ObjectNode) }
  }

  // patch every JDK package for every JDK item
  private inline fun ObjectNode.patchEveryPackage(patchPackage: (ObjectNode) -> Unit) = apply {
    patchEveryProduct { product -> product["packages"].forEach { patchPackage(it as ObjectNode) } }
  }

  private fun loadTestData(@Suppress("SameParameterValue") name: String): ObjectNode {
    val rawData = File(PathManagerEx.getTestDataPath("/jdkDownload/$name")).readBytes()
    return ObjectMapper().readTree(rawData) as? ObjectNode ?: error("Unexpected JSON data")
  }
}
