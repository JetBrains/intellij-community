// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath

import com.intellij.json.JsonFileType
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.impl.JsonRecursiveElementVisitor
import com.intellij.jsonpath.ui.JsonPathEvaluateManager
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.intellij.plugins.intelliLang.inject.TemporaryPlacesRegistry
import org.junit.Test
import java.util.function.Supplier

class JsonPathCompletionTest : LightPlatformCodeInsightFixture4TestCase() {
  @Test
  fun functionsCompletion() {
    myFixture.configureByText(JsonPathFileType.INSTANCE, "$.<caret>")
    assertCompletionVariants(
      "*",
      "avg()",
      "concat()",
      "keys()",
      "length()",
      "max()",
      "min()",
      "size()",
      "stddev()",
      "sum()"
    )
  }

  @Test
  fun operatorsCompletion() {
    myFixture.configureByText(JsonPathFileType.INSTANCE, "$[?(1 <caret>)]")
    assertCompletionVariants(
      "in",
      "nin",
      "subsetof",
      "anyof",
      "noneof",
      "size",
      "empty",
      "contains"
    )
  }

  @Test
  fun constValuesCompletion() {
    val constants = arrayOf("true", "false", "null")

    myFixture.configureByText(JsonPathFileType.INSTANCE, "$[?(1 == <caret>)]")
    assertCompletionVariants(*constants)

    myFixture.configureByText(JsonPathFileType.INSTANCE, "$[?(@.some !== <caret>)]")
    assertCompletionVariants(*constants)

    myFixture.configureByText(JsonPathFileType.INSTANCE, "$[?(@.some != {'a': <caret>})]")
    assertCompletionVariants(*constants)
  }

  @Test
  fun idsCompletionForInjectedFragments() {
    val root = "\$"
    val jsonFile = myFixture.configureByText(JsonFileType.INSTANCE, """
      {
        "a": "$root.demo",
        "b": "$root.some.second",
        "c": "@.<caret>"
      }
    """.trimIndent()) as JsonFile

    injectJsonPathToProperties(jsonFile, listOf("a", "b", "c"))
    assertCompletionVariants("demo", "some", "second")
  }

  @Test
  fun quotedNamesCompletionForInjectedFragments() {
    val root = "\$"
    val jsonFile = myFixture.configureByText(JsonFileType.INSTANCE, """
      {
        "a": "$root.demo",
        "b": "$root.some['second name']",
        "c": "@['<caret>']"
      }
    """.trimIndent()) as JsonFile

    injectJsonPathToProperties(jsonFile, listOf("a", "b", "c"))
    assertCompletionVariants("demo", "some", "second name")
  }

  @Test
  fun keysCompletionByJsonFile() {
    val jsonFile = myFixture.configureByText(JsonFileType.INSTANCE, """
      {
        "ok": 10,
        "demo": 20,
        "some name": null
      }
    """.trimIndent()) as JsonFile

    val fileWithId = myFixture.configureByText(JsonPathFileType.INSTANCE, "$.<caret>)]")
    fileWithId.putUserData(JsonPathEvaluateManager.JSON_PATH_EVALUATE_SOURCE_KEY, Supplier { jsonFile })

    assertCompletionVariants("ok", "demo")

    val fileWithQuot = myFixture.configureByText(JsonPathFileType.INSTANCE, "$.['<caret>'])]")
    fileWithQuot.putUserData(JsonPathEvaluateManager.JSON_PATH_EVALUATE_SOURCE_KEY, Supplier { jsonFile })

    assertCompletionVariants("ok", "demo", "some name")
  }

  @Test
  fun topLevelCompletion() {
    myFixture.configureByText(JsonPathFileType.INSTANCE, "<caret>")
    assertCompletionVariants(
      "*",
      "avg()",
      "concat()",
      "keys()",
      "length()",
      "max()",
      "min()",
      "size()",
      "stddev()",
      "sum()"
    )
  }

  private fun injectJsonPathToProperties(jsonFile: JsonFile, names: List<String>) {
    val languageInjectionSupport = TemporaryPlacesRegistry.getInstance(project).languageInjectionSupport
    jsonFile.accept(object : JsonRecursiveElementVisitor() {
      override fun visitProperty(o: JsonProperty) {
        super.visitProperty(o)

        if (names.contains(o.name)) {
          val host = o.value as PsiLanguageInjectionHost
          languageInjectionSupport.addInjectionInPlace(JsonPathLanguage.INSTANCE, host)
        }
      }
    })
  }

  private fun assertCompletionVariants(vararg expected: String) {
    myFixture.completeBasic()
    val lookupStrings = myFixture.lookupElementStrings
    assertNotNull(lookupStrings)
    assertContainsElements(lookupStrings!!, *expected)
  }
}