// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.intentions

import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.json.JsonTestCase
import com.intellij.psi.PsiFile
import com.jetbrains.jsonSchema.JsonSchemaHighlightingTestBase.registerJsonSchema
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.fixes.AddOptionalPropertiesIntention
import org.intellij.lang.annotations.Language
import org.junit.Assert

class AddOptionalPropertiesIntentionTest : JsonTestCase() {
  private fun doTest(@Language("JSON") before: String, @Language("JSON") after: String) {
    registerJsonSchema(
      myFixture,
      """
        {
          "${"$"}schema":"http://json-schema.org/draft-04/schema#", 
          "properties": {
            "a": {
              "type": "object",
              "properties": {
                "b" : {
                  "type": "object",
                  "properties": {
                    "c": {
                      "type": "string"
                    },
                    "d": {
                      "type": "integer"
                    }
                  }
                }
              }
            },
            "e": {
              "type": "object",
              "example": {
                "f": {
                  "g": 12345
                }
              },
              "properties": {
                "f": {
                  "type": "object",
                  "properties": {
                    "g": {
                      "type": "integer"
                    }
                  }
                }
              }
            }
          }
        }
      """.trimIndent(),
      "json"
    ) { true }
    val json = myFixture.configureByText("test.json", before)

    ensureSchemaIsCached(json)
    val intention = myFixture.getAvailableIntention(AddOptionalPropertiesIntention().text)!!
    ShowIntentionActionsHandler.chooseActionAndInvoke(myFixture.file, myFixture.editor, intention, intention.text)

    ensureSchemaIsCached(json)
    val previewText = myFixture.getIntentionPreviewText(intention)
    Assert.assertEquals(after, previewText)
    myFixture.checkResult(after)
  }

  // Since intention now relies on CachedValue API, we have to make sure that no dependencies were updated before intention#isAvailable call
  private fun ensureSchemaIsCached(json: PsiFile) {
    Assert.assertNotNull(JsonSchemaService.Impl.get(myFixture.project).getSchemaObject(json))
  }

  fun `test insert properties in empty object`() {
    doTest(
      """
        {<caret>}
      """.trimIndent(),
      """
        {
          "a": {},
          "e": {}
        }
      """.trimIndent()
    )
  }

  fun `test insert properties in non-empty object`() {
    doTest(
      """
        {
          "a": {},
          <caret>
        }
      """.trimIndent(),
      """
        {
          "a": {},
          "e": {}
        }
      """.trimIndent()
    )
  }

  fun `test insert properties in deep object`() {
    doTest(
      """
        {
          "a": {
            "b": {
              <caret>
            }
          }
        }
      """.trimIndent(),
      """
        {
          "a": {
            "b": {
              "c": "",
              "d": 0
            }
          }
        }
      """.trimIndent()
    )
  }

  fun `test add example from schema as default value`() {
    doTest(
      """
        {
          "e": {
            <caret>
          }
        }
      """.trimIndent(),
      """
        {
          "e": {
            "f": {
              "g": 12345
            }
          }
        }
      """.trimIndent()
    )
  }
}