// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.intentions

import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.json.JsonTestCase
import com.jetbrains.jsonSchema.JsonSchemaHighlightingTestBase.registerJsonSchema
import com.jetbrains.jsonSchema.impl.fixes.AddOptionalPropertiesIntention
import org.intellij.lang.annotations.Language
import org.junit.Assert

class AddOptionalPropertiesIntentionTest : JsonTestCase() {
  private fun doTest(@Language("JSON") before: String, @Language("JSON") after: String) {
    registerJsonSchema(
      myFixture,
      """
        {
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
              "type": "object"
            }
          }
        }
      """.trimIndent(),
      "json"
    ) { true }
    myFixture.configureByText("test.json", before)
    val intention = myFixture.getAvailableIntention(AddOptionalPropertiesIntention().text)!!
    ShowIntentionActionsHandler.chooseActionAndInvoke(myFixture.file, myFixture.editor, intention, intention.text)
    myFixture.checkResult(after)
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


  fun `test intention unavailable if all properties are present`() {
    registerJsonSchema(myFixture, """
      {
        "properties": {
          "a" : {}
        }
      }
    """.trimIndent(), "json") { true }
    myFixture.configureByText("test.json", """
      {
        "a": 123
        <caret>
      }
    """.trimIndent())
    Assert.assertNull(myFixture.getAvailableIntention (AddOptionalPropertiesIntention().text))
  }
}