// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.newerFormats

import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.openapi.application.readAction
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectStorage
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@TestApplication
@RegistryKey(key = "json.schema.use.networknt.validation", value = "false")
internal open class JsonSchema2019FeaturesTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val codeInsightFixture = codeInsightModuleFixture(projectFixture)
  protected val versionFixture by jsonSchemaVersionFixture(codeInsightFixture, TestDataLanguage.JSON)

  protected val dollar = "$"

  @Test
  fun `test schema dependencies`() {
    versionFixture.doTestSchemaValidation(
      """
      {
          "${dollar}schema": "https://json-schema.org/draft/2019-09/schema",
          "dependentSchemas": {
              "bar": {
                  "properties": {
                      "foo": {"type": "integer"},
                      "bar": {"type": "integer"}
                  }
              }
          }
      }
      """.trimIndent(),
      """
      {
         "foo": 1,
         "bar": 2
      } 
      """.trimIndent(),
      """
      {
         "foo": <warning descr="Incompatible types.
       Required: integer. Actual: string.">"invalid"</warning>, 
          "bar": 2
      }
      """.trimIndent(),
      "[ \"foo\" ]",
      "12"
    )
  }

  @Test
  fun `test property dependencies`() {
    versionFixture.doTestSchemaValidation(
      """
      {
          "${dollar}schema": "https://json-schema.org/draft/2019-09/schema",
          "dependentRequired": {"quux": ["foo", "bar"]}
      }
      """.trimIndent(),
      """
      { "foo": 1, "bar": 2, "quux": 3 }
      """.trimIndent(),
      """
      {
          <warning descr="Dependency is violated: property 'bar' must be specified, since 'quux' is specified">"foo": 1</warning>,
          "quux": 3
      }
      """.trimIndent(),
      """
      {
          <warning descr="Dependency is violated: properties 'bar', 'foo' must be specified, since 'quux' is specified">"quux": 3</warning>
      }
      """.trimIndent(),
      "{}"
    )
  }

  @Test
  fun `test read external vocabulary`(): Unit = timeoutRunBlocking {
    val mockSchema2019 = LightVirtualFile("schema.json", """
      {
        "${dollar}schema": "https://json-schema.org/draft/2019-09/schema",
        "${dollar}id": "https://json-schema.org/draft/2019-09/schema",
        "${dollar}vocabulary": {
            "https://json-schema.org/draft/2019-09/vocab/core": true
        },
        "${dollar}dynamicAnchor": "meta",
    
        "title": "Core and Validation specifications meta-schema",
        "${dollar}ref": "meta/core"
      }
    """.trimIndent())
    val project = versionFixture.fixture.project
    val remoteVocabulary = readAction {
      val schemaRootObject = JsonSchemaObjectStorage.getInstance(project).getOrComputeSchemaRootObject(mockSchema2019)
      Assertions.assertNotNull(schemaRootObject)
      schemaRootObject!!.resolveRefSchema(JsonSchemaService.Impl.get(project))
    }
    Assertions.assertNotNull(remoteVocabulary)
    Assertions.assertEquals("Core vocabulary meta-schema", remoteVocabulary?.title)
  }

  @Test
  fun `test defs instead of definitions`() {
    versionFixture.doTestSchemaValidation(
      """
        {
            "${dollar}schema": "https://json-schema.org/draft/2019-09/schema",
            "properties": {
                "foo": {
                    "${dollar}ref": "#/${dollar}defs/bar"
                }
            },
            "${dollar}defs": {
                "bar": {
                    "type": "string"
                }
            }
        }
      """.trimIndent(),
      """
        {
          "foo": "bar"
        }
      """.trimIndent(),
      """
        {
          "foo": <warning descr="Incompatible types.
         Required: string. Actual: integer.">123</warning>
        }
      """.trimIndent()
    )
  }

  @Test
  fun `test recursiveRef resolve`() {
    versionFixture.doTestSchemaValidation(
      """
        {
          "${dollar}schema": "https://json-schema.org/draft/2019-09/schema",
          "properties": {
              "test": { "type": "integer" },
              "foo": { "${dollar}recursiveRef": "#" }
          }
        }
      """.trimIndent(),
      """
        {
          "foo": {
            "test": 123
          }
        }
      """.trimIndent(),
      """
        {
          "foo": {
            "test": <warning descr="Incompatible types.
         Required: integer. Actual: string.">"invalid"</warning>
          }
        }
      """.trimIndent()
    )
  }

  @Test
  open fun `test remote recursive ref based object validation`() {
    val baseSchema = """
      {
        "${dollar}schema": "https://json-schema.org/draft/2019-09/schema",
        "${dollar}id": "https://example.com/schemas/base-schema",
        "${dollar}recursiveAnchor": "branch",

        "type": "object",
        "properties": {
          "test1": {
            "${dollar}recursiveRef": "#branch"
          },
          "test3": {
            "type": "integer"
          }
        }
      }
    """.trimIndent()

    val extendedSchema = """
      {
        "${dollar}schema": "https://json-schema.org/draft/2019-09/schema",
        "${dollar}id": "https://example.com/schemas/extended-schema",
        "${dollar}recursiveAnchor": "branch",

        "${dollar}ref": "https://example.com/schemas/base-schema",

        "type": "object",
        "properties": {
          "test2": {
            "type": "integer"
          }
        }
      }
    """.trimIndent()

    versionFixture.doTestValidationAgainstComplexSchema(
      extendedSchema,
      listOf(baseSchema),
      listOf(
        """
          {
            "test1": {
              "test1": {
                "test2": <warning descr="Incompatible types.
           Required: integer. Actual: boolean.">true</warning>
              }
            }
          }
        """.trimIndent()
      )
    )
  }
}