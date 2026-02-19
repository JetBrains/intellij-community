// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.newerFormats

import com.intellij.testFramework.LightVirtualFile
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectStorage
import org.junit.Assert

internal class JsonSchema2019FeaturesTest : JsonSchemaVersionTestBase() {
  override val testDataLanguage: TestDataLanguage
    get() = TestDataLanguage.JSON

  fun `test schema dependencies`() {
    doTestSchemaValidation(
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

  fun `test property dependencies`() {
    doTestSchemaValidation(
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

  fun `test read external vocabulary`() {
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
    val schemaRootObject = JsonSchemaObjectStorage.getInstance(project).getOrComputeSchemaRootObject(mockSchema2019)
    Assert.assertNotNull(schemaRootObject)
    val remoteVocabulary = schemaRootObject!!.resolveRefSchema(JsonSchemaService.Impl.get(project))
    Assert.assertNotNull(remoteVocabulary)
    Assert.assertEquals("Core vocabulary meta-schema", remoteVocabulary?.title)
  }

  fun `test defs instead of definitions`() {
    doTestSchemaValidation(
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

  fun `test recursiveRef resolve`() {
    doTestSchemaValidation(
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

  fun `test remote recursive ref based object validation`() {
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

    doTestValidationAgainstComplexSchema(
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