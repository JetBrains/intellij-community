// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.intentions

import com.intellij.json.JsonBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.PsiFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.jsonSchema.JsonSchemaHighlightingTestFixture.Companion.registerJsonSchema
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

@TestApplication
class AddOptionalPropertiesIntentionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private fun doTest(@Language("JSON") before: String, @Language("JSON") after: String): Unit = timeoutRunBlocking {
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
    val intentionText =  JsonBundle.message("intention.add.not.required.properties.text")
    val intention = myFixture.getAvailableIntention(intentionText)!!

    val previewText = myFixture.getIntentionPreviewText(intention)
    assertEquals(after, previewText)

    withContext(Dispatchers.EDT) {
      myFixture.checkPreviewAndLaunchAction(intention)
    }
    myFixture.checkResult(after)
  }

  // Since intention now relies on CachedValue API, we have to make sure that no dependencies were updated before intention#isAvailable call
  private suspend fun ensureSchemaIsCached(json: PsiFile) {
    assertNotNull(readAction { JsonSchemaService.Impl.get(myFixture.project).getSchemaObject(json) })
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
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