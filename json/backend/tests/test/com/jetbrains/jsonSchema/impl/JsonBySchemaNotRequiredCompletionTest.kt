// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.jsonSchema.impl.JsonSchemaCompletionTestUtil.testBySchema
import org.junit.jupiter.api.Test

/** Describes all the behavior we cover have for common use cases that configure objects to have field only based on a condition. */
@TestApplication
class JsonBySchemaNotRequiredCompletionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `test not required x than it won't complete property x`() {
    assertThatSchema("""
      {
        "properties": {
            "x": { "type": "boolean" }
        },
        "not": { "required": [ "x" ] },
        "additionalProperties": false
      }
    """.trimIndent())
      .appliedToJsonFile("""
        {
          <caret>
        }
      """.trimIndent())
      .hasNoCompletionVariantsAtCaret()
  }

  @Test
  fun `test not required x, y and z, then it will not complete the last of the three fields`() {
    assertThatSchema("""
      {
        "properties": {
            "x": { "type": "boolean" },
            "y": { "type": "boolean" },
            "z": { "type": "boolean" }
        },
        "not": { "required": [ "x", "y", "z" ] },
        "additionalProperties": false
      }
    """.trimIndent())
      .appliedToJsonFile("""
        {
          <caret>
        }
      """.trimIndent())
      .hasCompletionVariantsAtCaret("x", "y", "z")

      .appliedToJsonFile("""
        {
          "x": true,
          <caret>
        }
      """.trimIndent())
      .hasCompletionVariantsAtCaret("y", "z")

      .appliedToJsonFile("""
        {
          "x": true,
          "y": true,
          <caret>
        }
      """.trimIndent())
      .hasNoCompletionVariantsAtCaret()

      .appliedToJsonFile("""
        {
          "x": true,
          <caret>
          "z": true,
        }
      """.trimIndent())
      .hasNoCompletionVariantsAtCaret()

      .appliedToJsonFile("""
        {
          <caret>
          "y": true,
          "z": true,
        }
      """.trimIndent())
      .hasNoCompletionVariantsAtCaret()
  }

  @Test
  fun `test with 'if x then not required y' only completes y given that x = false`() {
    assertThatSchema("""
      {
        "properties": {
            "foo": { "type": "boolean" },
            "bar": { "type": "boolean" }
        },
        "if": { "properties": { "foo": { "const": true } } },
        "then": { "not": { "required": [ "bar" ] } },
        "additionalProperties": false
      }
    """.trimIndent())
      .appliedToJsonFile("""
        {
          "foo": false,
          <caret>
        }
      """.trimIndent())
      .hasCompletionVariantsAtCaret("bar")

      .appliedToJsonFile("""
        {
          "foo": true,
          <caret>
        }
      """.trimIndent())
      .hasNoCompletionVariantsAtCaret()
  }

  @Test
  fun `test with 'if x then not required y' inside allOf only completes y given that x = false`() {
    assertThatSchema("""
      {
        "properties": {
            "foo": { "type": "boolean" },
            "bar": { "type": "boolean" }
        },
        "allOf": [
          {
            "if": { "properties": { "foo": { "const": true } } },
            "then": { "not": { "required": [ "bar" ] } }
          }
        ],
        "additionalProperties": false
      }
    """.trimIndent())
      .appliedToJsonFile("""
        {
          "foo": false,
          <caret>
        }
      """.trimIndent())
      .hasCompletionVariantsAtCaret("bar")

      .appliedToJsonFile("""
        {
          "foo": true,
          <caret>
        }
      """.trimIndent())
      .hasNoCompletionVariantsAtCaret()
  }

  @Test
  fun `test 'if a then not required b or if c then not required d'`() {
    assertThatSchema("""
      {
        "properties": {
            "a": { "type": "boolean" },
            "b": { "type": "boolean" },
            "c": { "type": "boolean" },
            "d": { "type": "boolean" }
        },
        "allOf": [
          {
            "if": { "properties": { "a": { "const": true } } },
            "then": { "not": { "required": [ "b" ] } }
          },
          {
            "if": { "properties": { "c": { "const": true } } },
            "then": { "not": { "required": [ "d" ] } }
          }
        ],
        "additionalProperties": false
      }
    """.trimIndent())
      .appliedToJsonFile("""
        {
          "a": false,
          "c": false,
          <caret>
        }
      """.trimIndent())
      .hasCompletionVariantsAtCaret("b", "d")

      .appliedToJsonFile("""
        {
          "a": true,
          "c": false,
          <caret>
        }
      """.trimIndent())
      .hasCompletionVariantsAtCaret("d")

      .appliedToJsonFile("""
        {
          "a": false,
          "c": true,
          <caret>
        }
      """.trimIndent())
      .hasCompletionVariantsAtCaret("b")

      .appliedToJsonFile("""
        {
          "a": true,
          "c": true,
          <caret>
        }
      """.trimIndent())
      .hasNoCompletionVariantsAtCaret()
  }

  @Test
  fun `test 'if b then required a or if d then required c'`() {
    assertThatSchema("""
      {
        "properties": {
            "a": { "type": "boolean" },
            "b": { "type": "boolean" },
            "c": { "type": "boolean" },
            "d": { "type": "boolean" }
        },
        "allOf": [
          {
            "if": { "required": [ "b" ] },
            "then": { "properties": { "a": { "const": true } } }
          },
          {
            "if": { "properties": { "c": { "const": true } } },
            "then": { "not": { "required": [ "d" ] } }
          }
        ],
        "additionalProperties": false
      }
    """.trimIndent())
      .appliedToJsonFile("""
        {
          "a": false,
          "c": false,
          <caret>
        }
      """.trimIndent())
      .hasCompletionVariantsAtCaret("b", "d")

      .appliedToJsonFile("""
        {
          "a": true,
          "c": false,
          <caret>
        }
      """.trimIndent())
      .hasCompletionVariantsAtCaret("d")

      .appliedToJsonFile("""
        {
          "a": false,
          "c": true,
          <caret>
        }
      """.trimIndent())
      .hasCompletionVariantsAtCaret("b")

      .appliedToJsonFile("""
        {
          "a": true,
          "c": true,
          <caret>
        }
      """.trimIndent())
      .hasNoCompletionVariantsAtCaret()
  }

  private fun JsonSchemaAppliedToJsonSetup.hasCompletionVariantsAtCaret(vararg expectedVariants: String): JsonSchemaSetup {
    testNestedCompletionsWithPredefinedCompletionsRoot(schemaSetup.predefinedNestedCompletionsRoot) {
      testBySchema(
        myFixture,
        schemaSetup.schemaJson,
        json,
        "someFile.json",
        LookupElement::getLookupString,
        CompletionType.SMART,
        *expectedVariants.map { "\"$it\"" }.toTypedArray(),
      )
    }
    return schemaSetup
  }

  private fun JsonSchemaAppliedToJsonSetup.hasNoCompletionVariantsAtCaret(): JsonSchemaSetup = hasCompletionVariantsAtCaret()
}