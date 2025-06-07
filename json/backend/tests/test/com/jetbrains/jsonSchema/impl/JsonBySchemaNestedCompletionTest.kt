// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.jetbrains.jsonSchema.impl.TestSchemas.open1ThenOpen2Then3Schema
import com.jetbrains.jsonSchema.impl.nestedCompletions.buildNestedCompletionsTree

class JsonBySchemaNestedCompletionTest : JsonBySchemaCompletionBaseTest() {
  fun `test simple nested completion`() {
    open1ThenOpen2Then3Schema
      .appliedToJsonFile("""
        {
          "one": {
            <caret>
          }
        }
      """.trimIndent())
      .hasCompletionVariantsAtCaret(
        "two.three",
        "\"two\"",
      )
  }

  fun `test does not complete existing key`() {
    open1ThenOpen2Then3Schema
      .appliedToJsonFile("""
        {
          <caret>
          "one": {
            "two": {
              "bla": false
            }
          }
        }
      """.trimIndent())
      .hasCompletionVariantsAtCaret(
        "one.two.three",
      )
  }

  fun `test that nodes that already exist aren't completed`() {
    open1ThenOpen2Then3Schema
      .appliedToJsonFile("""
        {
          "one": {
            <caret>
            "two": {
              "foo": "bar"
            }
          }
        }
      """.trimIndent())
      .hasCompletionVariantsAtCaret(
        "two.three",
      )
  }

  fun `test that nested nodes are not completed if it's not configured`() {
    assertThatSchema("""
      {
        "properties": {
          "one": {
            "properties": {
              "two": {
                "type": "boolean"
              }
            }
          }
        }
      }
    """.trimIndent())
      .appliedToJsonFile("""
        {
          <caret>
        }
      """.trimIndent())
      .hasCompletionVariantsAtCaret(
        "\"one\"",
      )
  }

  fun `test that nested nodes are not completed if the node is isolated`() {
    assertThatSchema("""
      {
        "properties": {
          "one": {
            "properties": {
              "two": {
                "type": "boolean"
              }
            }
          }
        }
      }
    """.trimIndent())
      .withConfiguration(
        buildNestedCompletionsTree {
          isolated("one") {}
        }
      )
      .appliedToJsonFile("""
        {
          <caret>
        }
      """.trimIndent())
      .hasCompletionVariantsAtCaret(
        "\"one\"",
      )
  }

  fun `test completions can start in isolated regex nodes`() {
    val twoThreePropertyJsonText = """{
            "properties": {
              "two": {
                "properties": {
                  "three": {
                    "type": "boolean"
                  }
                }
              }
            }
          }"""
    assertThatSchema("""
      {
        "properties": {
          "one@foo": $twoThreePropertyJsonText,
          "one@bar": $twoThreePropertyJsonText,
          "one@baz": $twoThreePropertyJsonText
        }
      }
    """.trimIndent())
      .withConfiguration(
        buildNestedCompletionsTree {
          isolated("one@(foo|bar)".toRegex()) {
            open("two")
          }
        }
      )
      .appliedToJsonFile("""
        {
          "one@foo": {
            <caret>
          }
        }
      """.trimIndent())
      .hasCompletionVariantsAtCaret(
        "two.three",
        "\"two\"",
      )

      .appliedToJsonFile("""
        {
          "one@bar": {
            <caret>
          }
        }
      """.trimIndent())
      .hasCompletionVariantsAtCaret(
        "two.three",
        "\"two\"",
      )

      .appliedToJsonFile("""
        {
          "one@baz": {
            <caret>
          }
        }
      """.trimIndent())
      .hasCompletionVariantsAtCaret(
        "\"two\"",
      )
  }

  fun `test nested completions stop at an isolated node`() {
    assertThatSchema("""
      {
        "properties": {
          "one": {
            "properties": {
              "two": {
                "properties": {
                  "three": {
                    "properties": {
                      "four": {
                        "type": "boolean"
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    """.trimIndent())
      .withConfiguration(
        buildNestedCompletionsTree {
          open("one") {
            isolated("two") {
              open("three")
            }
          }
        }
      )
      .appliedToJsonFile("""
        {
          <caret>
        }
      """.trimIndent())
      .hasCompletionVariantsAtCaret(
        "\"one\"",
        "one.two",
      )

      .appliedToJsonFile("""
        {
          "one": {
            <caret>
          }
        }
      """.trimIndent())
      .hasCompletionVariantsAtCaret(
        "\"two\"",
      )

      .appliedToJsonFile("""
        {
          "one": {
            "two": {
              <caret>
            }
          }
        }
      """.trimIndent())
      .hasCompletionVariantsAtCaret(
        "three.four",
        "\"three\"",
      )
  }

  private fun JsonSchemaAppliedToJsonSetup.hasCompletionVariantsAtCaret(vararg expectedVariants: String): JsonSchemaSetup {
    testNestedCompletionsWithPredefinedCompletionsRoot(schemaSetup.predefinedNestedCompletionsRoot) {
      testBySchema(
        schemaSetup.schemaJson,
        json,
        "someFile.json",
        { it.renderedText()!! },
        CompletionType.SMART,
        *expectedVariants,
      )
    }
    return schemaSetup
  }
}

private fun LookupElement.renderedText(): String? =
  LookupElementPresentation()
    .renderedBy(this)
    .itemText

private fun LookupElementPresentation.renderedBy(element: LookupElement): LookupElementPresentation =
  copied().also { element.renderInto(it) }

private fun LookupElementPresentation.copied(): LookupElementPresentation = LookupElementPresentation().also { this.copiedInto(it) }

private fun LookupElementPresentation.copiedInto(presentation: LookupElementPresentation) {
  copyFrom(presentation)
}

private fun LookupElement.renderInto(renderedPresentation: LookupElementPresentation) {
  renderElement(renderedPresentation)
}
