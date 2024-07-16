// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.newerFormats

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.readText
import com.intellij.psi.PsiElement
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import com.jetbrains.jsonSchema.JsonSchemaHighlightingTestBase
import com.jetbrains.jsonSchema.JsonSchemaTestProvider
import com.jetbrains.jsonSchema.JsonSchemaTestServiceImpl
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaComplianceInspection
import org.junit.Assert

enum class TestDataLanguage(val extension: String) {
  JSON("json"), YAML("yaml")
}

abstract class JsonSchemaVersionTestBase : BasePlatformTestCase() {
  protected val dollar = "$"

  abstract val testDataLanguage: TestDataLanguage

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(JsonSchemaComplianceInspection::class.java)
  }

  protected open fun loadSchemaTextFromResources(schemaName: String): String {
    val resource = this.javaClass.classLoader.getResource("jsonSchema/newerFormats/$schemaName")
    Assert.assertNotNull(resource)
    return resource!!.readText()
  }

  protected fun doTestSchemaCompletion(schemaText: String,
                                       fileTextBefore: String,
                                       completionType: CompletionType,
                                       vararg expectedCompletionItems: String) {
    val testedFileName = "test.${testDataLanguage.extension}"
    JsonSchemaHighlightingTestBase.registerJsonSchema(myFixture, schemaText, "json") { true }
    myFixture.configureByText(testedFileName, fileTextBefore)
    Assert.assertTrue("Provided schema must be among schemas available for tested file",
                      JsonSchemaService.Impl.get(myFixture.project)
                        .getSchemaFilesForFile(myFixture.file.virtualFile)
                        .any { it.readText().trimIndent() == schemaText })

    val effectiveVariants = myFixture.complete(completionType).orEmpty().map { it.lookupString }
    UsefulTestCase.assertContainsElements(effectiveVariants, *expectedCompletionItems)
  }

  protected fun doTestSchemaValidation(mainSchemaText: String, vararg filesToValidate: String) {
    registerJsonSchema(mainSchemaText)
    checkHighlightingForAllFiles(mainSchemaText, *filesToValidate)
  }

  protected fun doTestValidationAgainstComplexSchema(mainSchemaText: String, additionalSchemes: List<String>, filesToValidate: List<String>) {
    registerJsonSchema(mainSchemaText, *additionalSchemes.toTypedArray())
    checkHighlightingForAllFiles(mainSchemaText, *filesToValidate.toTypedArray())
  }

  protected fun doTestGtdWithSingleTarget(schemaText: String,
                                          vararg filesToTargets: Pair<String, String>,
                                          test: (PsiElement) -> String) {
    registerJsonSchema(schemaText)

    for ((fileText, targetText) in filesToTargets) {
      val testedFileName = "test.${testDataLanguage.extension}"
      myFixture.configureByText(testedFileName, fileText)
      val elementAtCaret = myFixture.elementAtCaret
      val targetElementText = test(elementAtCaret)
      Assert.assertEquals(targetText, targetElementText)

      //element.asSafely<JsonProperty>()?.nameElement
      //val target = GotoDeclarationUtil.findTargetElementsFromProviders(elementAtCaret,
      //                                                                 myFixture.elementAtCaret.textOffset,
      //                                                                 myFixture.editor)
      //Assert.assertNotNull(target)
      //Assert.assertEquals(1, target!!.size)
      //val singleTargetParent = target.single().parent
      //Assert.assertTrue(singleTargetParent is JsonProperty)
      //singleTargetParent as JsonProperty
      //Assert.assertEquals(targetText, singleTargetParent.text)
    }
  }

  private fun checkHighlightingForAllFiles(mainSchemaText: String, vararg fileTexts: String) {
    for (text in fileTexts) {
      val testedFileName = "test.${testDataLanguage.extension}"
      myFixture.configureByText(testedFileName, text)
      Assert.assertTrue("Provided schema must be among schemas available for tested file",
                        JsonSchemaService.Impl.get(myFixture.project)
                          .getSchemaFilesForFile(myFixture.file.virtualFile)
                          .any { it.readText().trimIndent() == mainSchemaText })
      myFixture.checkHighlighting()
    }
  }

  private fun registerJsonSchema(mainSchemaText: String,
                                   vararg additionalSchemes: String) {
    val mainSchemaProvider = createProviderForSchema(mainSchemaText, true)
    val additionalSchemaProviders = additionalSchemes.map { createProviderForSchema(it, false) }.toTypedArray()

    JsonSchemaTestServiceImpl.setProviders(mainSchemaProvider, *additionalSchemaProviders)

    Disposer.register(myFixture.testRootDisposable) { JsonSchemaTestServiceImpl.setProvider(null) }
    myFixture.project.replaceService(JsonSchemaService::class.java,
                                     JsonSchemaTestServiceImpl(myFixture.project),
                                     myFixture.testRootDisposable)
  }

  private fun createProviderForSchema(schemaText: String, directlyAvailable: Boolean): JsonSchemaTestProvider {
    myFixture.findFileInTempDir("json_schema_test/schema-${schemaText.hashCode()}.json")?.delete(this)
    val schemaFile = myFixture.addFileToProject("json_schema_test/schema-${schemaText.hashCode()}.json", schemaText)
    return JsonSchemaTestProvider(schemaFile.virtualFile, { directlyAvailable })
  }
}