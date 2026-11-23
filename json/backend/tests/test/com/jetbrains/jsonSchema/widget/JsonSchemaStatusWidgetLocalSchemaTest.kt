// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.widget

import com.intellij.json.JsonBundle
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
class JsonSchemaStatusWidgetLocalSchemaTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)

    private const val SCHEMA_WITHOUT_SCHEMA_PROPERTY = """{"type": "object", "properties": {"alpha": {"type": "string"}}}"""
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)
  private val project get() = projectFixture.get()

  @Test
  fun testFileUrlToSchemaWithoutSchemaPropertyShowsSchemaName() {
    val schemaFile = myFixture.addFileToProject("local.schema.json", SCHEMA_WITHOUT_SCHEMA_PROPERTY).virtualFile
    assertWidgetShowsSchema(schemaFile, schemaFile.toNioPath().toUri().toString())
  }

  @Test
  fun testRelativePathToSchemaWithoutSchemaPropertyShowsSchemaName() {
    val schemaFile = myFixture.addFileToProject("local.schema.json", SCHEMA_WITHOUT_SCHEMA_PROPERTY).virtualFile
    assertWidgetShowsSchema(schemaFile, "./local.schema.json")
  }

  private fun assertWidgetShowsSchema(schemaFile: VirtualFile, reference: String) {
    val dataFile = myFixture.configureByText("data.json", $$"""{"$schema": "$$reference"}""").virtualFile
    val state = runReadActionBlocking { JsonSchemaStatusWidget.widgetStateForTests(project, dataFile, true) }
    assertEquals(JsonBundle.message("schema.widget.prefix.json.files") + schemaFile.nameWithoutExtension, state.text)
  }
}
