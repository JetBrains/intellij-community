// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.internal.SharedIndexMetadata.writeIndexMetadata
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.indexing.IndexInfrastructureVersion
import com.intellij.util.indexing.IndexInfrastructureVersion.globalVersion
import org.junit.Assert
import org.junit.Test

class SharedIndexMetadataTest : BasePlatformTestCase() {
  @Test
  fun testSelfVersionShouldMatch() = doMatchTest(true) { this }

  @Test
  fun testSelfVersionWithExtraKeyShouldNotMatch() = doMatchTest(false) { this.copy(addToBase = mapOf("jonnyzzz" to "42")) }

  @Test
  fun testSelfVersionWithMissingKeyShouldNotMatch() = doMatchTest(false) {this.copy(removeFromBase = setOf(baseIndexes.keys.first())) }

  @Test
  fun testEmptyIndexersVersionShouldNotMatch() = doMatchTest(false) { this.copy(file = emptyMap(), stub = emptyMap()) }

  @Test
  fun testNoBaseIndexesShouldNotMatch() = doMatchTest(false) { this.copy(base = emptyMap()) }

  @Test
  fun testNoFileIndexesShouldNotMatch() = doMatchTest(false) { this.copy(file = emptyMap()) }

  @Test
  fun testExtraFileIndexesShouldMatch() = doMatchTest(true) { this.copy(addToFile = mapOf("jonnyzzz" to "42")) }

  @Test
  fun testMissingFileIndexesShouldMatch() = doMatchTest(true) { this.copy(removeFromFile = setOf(fileBasedIndexVersions.keys.first())) }

  @Test
  fun testNoStubIndexesShouldNotMatch() = doMatchTest(false) { this.copy(stub = emptyMap()) }

  @Test
  fun testExtraStubIndexShouldNotMatch() = doMatchTest(false) { this.copy(addToStub = mapOf("jonnyzzz" to "42")) }

  @Test
  fun testMissingStubIndexShouldNotMatch() = doMatchTest(false) { this.copy(removeFromStub = setOf(stubIndexVersions.keys.first())) }

  @Test
  fun testSelfSerializationIsStable() {
    val (a,b) = List(2) {
      val selfVersion = globalVersion()
      writeIndexMetadata("mock1", "jdk", "123", selfVersion)
    }

    Assert.assertArrayEquals(a, b)
  }

  private fun doMatchTest(shouldBeEqual: Boolean,
                          tuneSelfVersion: IndexInfrastructureVersion.() -> IndexInfrastructureVersion) {
    val version = globalVersion()
    Assert.assertEquals(version, version.copy())

    val json = writeIndexMetadata("mock1", "jdk", "123", version.copy().tuneSelfVersion())
    val om = ObjectMapper()

    val selfVersion = version.copy()
    val info1 = SharedIndexInfo("http://mock", "123", om.readTree(json) as ObjectNode)
    if (shouldBeEqual) {
      Assert.assertEquals(info1, SharedIndexMetadata.selectBestSuitableIndex(selfVersion, listOf(info1))?.first)
    } else {
      Assert.assertNotEquals(info1, SharedIndexMetadata.selectBestSuitableIndex(selfVersion, listOf(info1))?.first)
    }
  }


  private fun IndexInfrastructureVersion.copy(
    removeFromBase: Set<String> = emptySet(),
    addToBase: Map<String, String> = emptyMap(),
    base: Map<String, String> = this.baseIndexes - removeFromBase + addToBase,

    removeFromFile: Set<String> = emptySet(),
    addToFile: Map<String, String> = emptyMap(),
    file: Map<String, String> = this.fileBasedIndexVersions - removeFromFile + addToFile,

    removeFromStub: Set<String> = emptySet(),
    addToStub: Map<String, String> = emptyMap(),
    stub: Map<String, String> = this.stubIndexVersions - removeFromStub + addToStub
  ) = IndexInfrastructureVersion(base, file, stub)
}
