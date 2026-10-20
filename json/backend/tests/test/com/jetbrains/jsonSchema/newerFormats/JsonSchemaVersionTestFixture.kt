// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.newerFormats

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.readText
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.replaceService
import com.jetbrains.jsonSchema.JsonSchemaHighlightingTestFixture
import com.jetbrains.jsonSchema.JsonSchemaTestProvider
import com.jetbrains.jsonSchema.JsonSchemaTestServiceImpl
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaComplianceInspection
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.IOException

enum class TestDataLanguage(val extension: String) {
  JSON("json"), YAML("yaml")
}

/**
 * Creates a [JsonSchemaVersionTestFixture] on [codeInsightFixture], and enables [JsonSchemaComplianceInspection].
 * It is the JUnit 5 replacement of the JUnit 3 base `JsonSchemaVersionTestBase`.
 *
 * Declare the result as an instance property, so that each test gets its own fixture.
 *
 * @param testDataLanguage the language of the tested file
 * @param schemaResourceDir the resource directory of [JsonSchemaVersionTestFixture.loadSchemaTextFromResources]
 */
@TestOnly
fun jsonSchemaVersionFixture(
  codeInsightFixture: TestFixture<CodeInsightTestFixture>,
  testDataLanguage: TestDataLanguage,
  schemaResourceDir: String = "jsonSchema/newerFormats",
): TestFixture<JsonSchemaVersionTestFixture> = testFixture("jsonSchemaVersionFixture") {
  val fixture = codeInsightFixture.init()
  (fixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)
  fixture.enableInspections(JsonSchemaComplianceInspection::class.java)
  initialized(JsonSchemaVersionTestFixture(fixture, testDataLanguage, schemaResourceDir)) {}
}

/**
 * JSON schema helpers for a JUnit 5 test of a newer schema version: the validation, the completion, and the navigation.
 *
 * Call a helper off the EDT.
 */
@TestOnly
class JsonSchemaVersionTestFixture internal constructor(
  val fixture: CodeInsightTestFixture,
  private val testDataLanguage: TestDataLanguage,
  private val schemaResourceDir: String,
) {
  private val testedFileName: String
    get() = "test.${testDataLanguage.extension}"

  fun loadSchemaTextFromResources(schemaName: String): String {
    val resource = javaClass.classLoader.getResource("$schemaResourceDir/$schemaName")
    assertNotNull(resource)
    return resource!!.readText()
  }

  fun doTestSchemaCompletion(
    schemaText: String,
    fileTextBefore: String,
    completionType: CompletionType,
    vararg expectedCompletionItems: String,
  ) {
    JsonSchemaHighlightingTestFixture.registerJsonSchema(fixture, schemaText, "json") { true }
    fixture.configureByText(testedFileName, fileTextBefore)
    assertSchemaIsAvailable(schemaText)

    val effectiveVariants = fixture.complete(completionType).orEmpty().map { it.lookupString }
    assertThat(effectiveVariants).contains(*expectedCompletionItems)
  }

  fun doTestSchemaValidation(mainSchemaText: String, vararg filesToValidate: String) {
    registerJsonSchema(mainSchemaText)
    checkHighlightingForAllFiles(mainSchemaText, *filesToValidate)
  }

  fun doTestValidationAgainstComplexSchema(mainSchemaText: String, additionalSchemes: List<String>, filesToValidate: List<String>) {
    registerJsonSchema(mainSchemaText, *additionalSchemes.toTypedArray())
    checkHighlightingForAllFiles(mainSchemaText, *filesToValidate.toTypedArray())
  }

  fun doTestGtdWithSingleTarget(
    schemaText: String,
    vararg filesToTargets: Pair<String, String>,
    test: (PsiElement) -> String,
  ) {
    registerJsonSchema(schemaText)

    for ((fileText, targetText) in filesToTargets) {
      fixture.configureByText(testedFileName, fileText)
      val targetElementText = runReadActionBlocking { test(fixture.elementAtCaret) }
      assertEquals(targetText, targetElementText)
    }
  }

  private fun checkHighlightingForAllFiles(mainSchemaText: String, vararg fileTexts: String) {
    for (text in fileTexts) {
      fixture.configureByText(testedFileName, text)
      assertSchemaIsAvailable(mainSchemaText)
      fixture.checkHighlighting()
    }
  }

  private fun assertSchemaIsAvailable(schemaText: String) {
    val schemaFiles = runReadActionBlocking {
      JsonSchemaService.Impl.get(fixture.project).getSchemaFilesForFile(fixture.file.virtualFile)
    }
    assertTrue(schemaFiles.any { it.readText().trimIndent() == schemaText },
               "Provided schema must be among schemas available for tested file")
  }

  private fun registerJsonSchema(mainSchemaText: String, vararg additionalSchemes: String) {
    val mainSchemaProvider = createProviderForSchema(mainSchemaText, true)
    val additionalSchemaProviders = additionalSchemes.map { createProviderForSchema(it, false) }.toTypedArray()

    JsonSchemaTestServiceImpl.setProviders(mainSchemaProvider, *additionalSchemaProviders)

    Disposer.register(fixture.testRootDisposable) { JsonSchemaTestServiceImpl.setProvider(null) }
    fixture.project.replaceService(JsonSchemaService::class.java,
                                   JsonSchemaTestServiceImpl(fixture.project),
                                   fixture.testRootDisposable)
  }

  private fun createProviderForSchema(schemaText: String, directlyAvailable: Boolean): JsonSchemaTestProvider {
    val path = "json_schema_test/schema-${schemaText.hashCode()}.json"
    fixture.tempDirFixture.getFile(path)?.let { file ->
      WriteAction.runAndWait<IOException> { file.delete(this) }
    }
    val schemaFile = fixture.addFileToProject(path, schemaText)
    return JsonSchemaTestProvider(schemaFile.virtualFile) { directlyAvailable }
  }
}
