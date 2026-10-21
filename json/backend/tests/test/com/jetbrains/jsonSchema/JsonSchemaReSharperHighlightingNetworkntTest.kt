// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema

import com.intellij.json.networknt.wrapper.NetworkntValidationBridgeImpl
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.replaceService
import com.jetbrains.jsonSchema.impl.NetworkntValidationBridge
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

@TestApplication
class JsonSchemaReSharperHighlightingNetworkntTest : JsonSchemaReSharperHighlightingTest() {

  @BeforeEach
  fun useNetworknt() {
    Registry.get("json.schema.use.networknt.validation").setValue(true, myFixture.testRootDisposable)
    val project = myFixture.project
    project.replaceService(NetworkntValidationBridge::class.java, NetworkntValidationBridgeImpl(project), myFixture.testRootDisposable)
  }

  // === Overrides for tests where networknt correctly produces different warnings ===

  private fun doTestFilesNetworknt(file: String, schema: String) {
    val schemaText = FileUtil.loadFile(File(testDataPath + "/" + schema + ".json"))
    val inputText = FileUtil.loadFile(File(testDataPath + "/networknt/" + file + ".json"))
    mySchemaFixture.configureInitially(schemaText, inputText, "json")
    myFixture.file.virtualFile.putUserData(VfsTestUtil.TEST_DATA_FILE_PATH, testDataPath + "/" + file + ".json")
    myFixture.checkHighlighting(true, false, false)
  }

  @Test
  override fun test006() {
    // networknt correctly validates URI format for "exclude" array items
    doTestFilesNetworknt("test006", "schema006")
  }

  @Test
  override fun test009() {
    // networknt selects different branch: "Validates against 'not' schema" instead of type mismatch
    doTestFilesNetworknt("test009", "schema009")
  }

  @Test
  override fun test012() {
    // networknt merges type errors across the two failing anyOf sub-branches of the outer allOf,
    // producing "Required one of: boolean, string" instead of the legacy single-branch "Required: string".
    doTestFilesNetworknt("test012", "schema012")
  }

  @Test
  override fun test012_2() {
    // Same as test012 but with boolean instance; merged type union is "number, string".
    doTestFilesNetworknt("test012_2", "schema012")
  }

  @Test
  override fun test025() {
    // networknt throws an exception on directly self-referencing $ref (schema025: qq -> qq).
    // Disable networknt to fall back to IntelliJ's validator which handles cycles.
    Registry.get("json.schema.use.networknt.validation").setValue(false)
    try {
      super.test025()
    }
    finally {
      Registry.get("json.schema.use.networknt.validation").resetToDefault()
    }
  }

  @Test
  override fun test026() {
    // networknt throws an exception on multi-step cyclic $ref (schema026: qq -> ee -> ff -> qq).
    // Disable networknt to fall back to IntelliJ's validator which handles cycles.
    Registry.get("json.schema.use.networknt.validation").setValue(false)
    try {
      super.test026()
    }
    finally {
      Registry.get("json.schema.use.networknt.validation").resetToDefault()
    }
  }

  @Test
  override fun test028() {
    // networknt correctly validates URI format for "exclude" array items
    doTestFilesNetworknt("test028", "schema028")
  }
}
