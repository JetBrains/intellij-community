// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema

import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonValue
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.PsiFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaRefReferenceInspection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Path

@TestApplication
class JsonSchemaFileUrlReferenceTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)

    private const val SCHEMA_TEXT = """
      {
        "type": "object",
        "properties": {
          "alpha": { "type": "string" },
          "beta": { "type": "integer" }
        }
      }
    """.trimIndent()
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun testFileUrlResolvesToSchemaFile() {
    val schemaFile = myFixture.addFileToProject("local.schema.json", SCHEMA_TEXT)
    myFixture.configureByText("data.json", $$"""{"$schema": "$${fileUrl(schemaFile)}"}""")

    val resolved = runReadActionBlocking { schemaPropertyValue().references.last().resolve() }
    assertEquals(schemaFile, resolved)
  }

  @Test
  fun testFileUrlHasNoUnresolvedReferenceWarnings() {
    val schemaFile = myFixture.addFileToProject("local.schema.json", SCHEMA_TEXT)
    myFixture.enableInspections(JsonSchemaRefReferenceInspection::class.java)
    myFixture.configureByText("data.json", $$"""{"$schema": "$${fileUrl(schemaFile)}"}""")

    myFixture.checkHighlighting()
  }

  @Test
  fun testMissingFileUrlHasOneWarning() {
    val dirUrl = Path.of(myFixture.tempDirPath).toUri().toString()
    myFixture.enableInspections(JsonSchemaRefReferenceInspection::class.java)
    myFixture.configureByText("data.json",
                              $$"""{"$schema": "$${dirUrl}<warning descr="File 'missing.json' not found">missing.json</warning>"}""")

    myFixture.checkHighlighting()
  }

  @Test
  fun testFileUrlSchemaGivesCompletion() {
    val schemaFile = myFixture.addFileToProject("local.schema.json", SCHEMA_TEXT)
    myFixture.configureByText("data.json", $$"""{"$schema": "$${fileUrl(schemaFile)}", "<caret>"}""")

    val variants = myFixture.completeBasic().orEmpty().map { it.lookupString }
    assertThat(variants).contains("alpha", "beta")
  }

  @Test
  fun testPercentEncodedFileUrlHasNoUnresolvedReferenceWarnings() {
    val schemaFile = myFixture.addFileToProject("my schemas/local.schema.json", SCHEMA_TEXT)
    myFixture.enableInspections(JsonSchemaRefReferenceInspection::class.java)
    myFixture.configureByText("data.json", $$"""{"$schema": "$${percentEncodedFileUrl(schemaFile)}"}""")

    myFixture.checkHighlighting()
  }

  @Test
  fun testPercentEncodedFileUrlSchemaGivesCompletion() {
    val schemaFile = myFixture.addFileToProject("my schemas/local.schema.json", SCHEMA_TEXT)
    myFixture.configureByText("data.json", $$"""{"$schema": "$${percentEncodedFileUrl(schemaFile)}", "<caret>"}""")

    val variants = myFixture.completeBasic().orEmpty().map { it.lookupString }
    assertThat(variants).contains("alpha", "beta")
  }

  @Test
  fun testRenameSchemaFileUpdatesFileUrl(): Unit = timeoutRunBlocking {
    val schemaFile = myFixture.addFileToProject("schemas/local.schema.json", SCHEMA_TEXT)
    val url = fileUrl(schemaFile)
    myFixture.configureByText("data.json", $$"""{"$schema": "$$url"}""")

    withContext(Dispatchers.EDT) { myFixture.renameElement(schemaFile, "renamed.schema.json") }

    myFixture.checkResult($$"""{"$schema": "$${url.replace("local.schema.json", "renamed.schema.json")}"}""")
  }

  @Test
  fun testRenameSchemaDirectoryUpdatesFileUrl(): Unit = timeoutRunBlocking {
    val schemaFile = myFixture.addFileToProject("schemas/local.schema.json", SCHEMA_TEXT)
    val url = fileUrl(schemaFile)
    myFixture.configureByText("data.json", $$"""{"$schema": "$$url"}""")

    withContext(Dispatchers.EDT) { myFixture.renameElement(schemaFile.parent!!, "renamed") }

    myFixture.checkResult($$"""{"$schema": "$${url.replace("/schemas/", "/renamed/")}"}""")
  }

  @Test
  fun testBindToOtherSchemaFileUpdatesFileUrl(): Unit = timeoutRunBlocking {
    val schemaFile = myFixture.addFileToProject("schemas/local.schema.json", SCHEMA_TEXT)
    val movedFile = myFixture.addFileToProject("moved/local.schema.json", SCHEMA_TEXT)
    val url = fileUrl(schemaFile)
    myFixture.configureByText("data.json", $$"""{"$schema": "$$url"}""")

    withContext(Dispatchers.EDT) {
      WriteCommandAction.runWriteCommandAction(myFixture.project) {
        schemaPropertyValue().references.last().bindToElement(movedFile)
      }
    }

    myFixture.checkResult($$"""{"$schema": "$${url.replace("/schemas/", "/moved/")}"}""")
  }

  private fun fileUrl(file: PsiFile): String = file.virtualFile.toNioPath().toUri().toString()

  private fun percentEncodedFileUrl(file: PsiFile): String = fileUrl(file).also { assertThat(it).contains("my%20schemas") }

  private fun schemaPropertyValue(): JsonValue {
    val root = (myFixture.file as JsonFile).topLevelValue as JsonObject
    return root.findProperty($$"$schema")!!.value!!
  }
}
