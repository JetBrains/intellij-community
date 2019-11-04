// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.jdkDownloader.JdkListDownloader
import com.intellij.openapi.application.ex.PathManagerEx
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert
import org.junit.Test
import java.io.File

class JdkListTest {
  private val osTypes = listOf("windows", "linux", "macOS")

  @Test
  fun `default model can be downloaded and parsed`() {
    val data = JdkListDownloader.downloadModel(null)
    Assert.assertTrue(data.isNotEmpty())
  }

  @Test
  fun `parse feed v1`() {
    val json = loadTestData("feed-v1.json")
    for (os in osTypes) {
      val data = JdkListDownloader.parseJdkList(json, os)
      assertThat(data).withFailMessage("should have items for $os").size().isOne()
    }
  }

  @Test
  fun `skip unknown package types`() {
    val json = loadTestData("feed-v1.json").patchEveryPackage { it.put("package_type", "unexpected_jet_jar") }
    for (os in osTypes) {
      val data = JdkListDownloader.parseJdkList(json, os)
      assertThat(data).withFailMessage("should have items for $os").isEmpty()
    }
  }

  // patch every JDK product
  private inline fun ObjectNode.patchEveryProduct(patchItem: (ObjectNode) -> Unit) = apply {
    this["jdks"].forEach { patchItem(it as ObjectNode) }
  }

  // patch every JDK package for every JDK item
  private inline fun ObjectNode.patchEveryPackage(patchPackage: (ObjectNode) -> Unit) = apply {
    patchEveryProduct { product -> product["packages"].forEach { patchPackage(it as ObjectNode) } }
  }

  private fun loadTestData(name: String): ObjectNode {
    val rawData = File(PathManagerEx.getTestDataPath("/jdkDownload/$name")).readBytes()
    return ObjectMapper().readTree(rawData) as? ObjectNode ?: error("Unexpected JSON data")
  }
}
