// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.newerFormats

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.navigation.action.GotoDeclarationUtil
import com.intellij.json.psi.JsonProperty
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.asSafely
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.JsonSchemaType
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectBackedByJacksonBase
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectStorage
import org.junit.Assert
import org.junit.Assume

internal class JsonSchema2020FeaturesTest : JsonSchemaVersionTestBase() {
  override val testDataLanguage: TestDataLanguage
    get() = TestDataLanguage.JSON

  fun `test dynamic anchor resolve`() {
    Assume.assumeTrue(Registry.`is`("json.schema.object.v2"))
    val schemaFile = LightVirtualFile("schema.json", """
      {
        "${dollar}schema": "https://json-schema.org/draft/2020-12/schema",
        "test": {
          "${dollar}dynamicAnchor": "CustomAnchor",
          "type": "array"
        }
      }
    """.trimIndent())
    val schemaRootObject = JsonSchemaObjectStorage.getInstance(project).getOrComputeSchemaRootObject(schemaFile) as? JsonSchemaObjectBackedByJacksonBase
    Assert.assertNotNull(schemaRootObject)
    val resolvedNodePointer = schemaRootObject!!.getRootSchemaObject().resolveDynamicAnchor("CustomAnchor")
    Assert.assertEquals("/test", resolvedNodePointer)
    val relativeDefinition = schemaRootObject.getRootSchemaObject().findRelativeDefinition(resolvedNodePointer!!)
    Assert.assertNotNull(relativeDefinition)
    Assert.assertEquals(JsonSchemaType._array, relativeDefinition?.type)
  }

  fun `test local dynamic ref based validation`() {
    doTestSchemaValidation(
      """
        {
          "${dollar}schema": "https://json-schema.org/draft/2020-12/schema",
          "${dollar}id": "https://test.json-schema.org/dynamicRef-dynamicAnchor-same-schema/root",
          "type": "object",
          "properties": {
            "test": {
              "type": "array",
              "items": { "${dollar}dynamicRef": "#items" }
            }
          },
          "${dollar}defs": {
            "foo": {
              "${dollar}dynamicAnchor": "items",
              "type": "string"
            }
          }
        }
      """.trimIndent(),
      """
        {
          "test": ["foo", "bar"]
        }
      """.trimIndent(),
      """
        {
          "test": ["foo", <warning descr="Incompatible types.
         Required: string. Actual: integer.">42</warning>]
        }
      """.trimIndent()
    )
  }

  fun `test remote dynamic ref based object validation`() {
    val baseSchema = """
      {
        "${dollar}schema": "https://json-schema.org/draft/2020-12/schema",
        "${dollar}id": "https://example.com/schemas/base-schema",
        "${dollar}dynamicAnchor": "branch",

        "type": "object",
        "properties": {
          "test1": {
            "${dollar}dynamicRef": "#branch"
          },
          "test3": {
            "type": "integer"
          }
        }
      }
    """.trimIndent()

    val extendedSchema = """
      {
        "${dollar}schema": "https://json-schema.org/draft/2020-12/schema",
        "${dollar}id": "https://example.com/schemas/extended-schema",
        "${dollar}dynamicAnchor": "branch",

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

  fun `test remote dynamic ref based array validation`() {
    val baseSchema = """
      {
        "${dollar}schema": "https://json-schema.org/draft/2020-12/schema",
        "${dollar}id": "https://example.com/schemas/base-string-tree",
        "${dollar}dynamicAnchor": "branch",

        "type": "array",
        "items": {
          "anyOf": [
            { "type": "string" },
            { "${dollar}dynamicRef": "#branch" }
          ]
        }
      }
    """.trimIndent()
    val extendedSchema = """
      {
        "${dollar}schema": "https://json-schema.org/draft/2020-12/schema",
        "${dollar}id": "https://example.com/schemas/bounded-string-tree",
        "${dollar}dynamicAnchor": "branch",

        "${dollar}ref": "https://example.com/schemas/base-string-tree",
        "maxItems": 2
      }
    """.trimIndent()

    doTestValidationAgainstComplexSchema(
      extendedSchema,
      listOf(baseSchema),
      listOf(
        """
          [<warning descr="Array is longer than 2">"a"</warning>, "b", "c", "d"]
        """.trimIndent(),
      )
    )
  }

  fun `test read external vocabulary`() {
    val mockSchema2020 = LightVirtualFile("schema.json", """
      {
        "${dollar}schema": "https://json-schema.org/draft/2020-12/schema",
        "${dollar}id": "https://json-schema.org/draft/2020-12/schema",
        "${dollar}vocabulary": {
            "https://json-schema.org/draft/2020-12/vocab/core": true
        },
        "${dollar}dynamicAnchor": "meta",
    
        "title": "Core and Validation specifications meta-schema",
        "${dollar}ref": "meta/core"
      }
    """.trimIndent())
    val schemaRootObject = JsonSchemaObjectStorage.getInstance(project).getOrComputeSchemaRootObject(mockSchema2020)
    Assert.assertNotNull(schemaRootObject)
    val remoteVocabulary = schemaRootObject!!.resolveRefSchema(JsonSchemaService.Impl.get(project))
    Assert.assertNotNull(remoteVocabulary)
    Assert.assertEquals("Core vocabulary meta-schema", remoteVocabulary?.title)
  }

  fun `test array items validation`() {
    doTestSchemaValidation(
      """
        {
            "${dollar}schema": "https://json-schema.org/draft/2020-12/schema",
            "properties": {
              "foo": {
                "type": "array",
                "items": { "type": "integer" }
              }
            }
        }
      """.trimIndent(),
      """
        {
          "foo": [1, 2, 3]
        }
      """.trimIndent(),
      """
        {
          "foo": [1, 2, <warning descr="Incompatible types.
         Required: integer. Actual: string.">"invalid"</warning>]
        }
      """.trimIndent(),
      """
        { "buz" : "bar" }
      """.trimIndent()
    )
  }

  fun `test array items validation against always invalid schema`() {
    doTestSchemaValidation(
      """
        {
            "${dollar}schema": "https://json-schema.org/draft/2020-12/schema",
            "properties": {
              "foo": {
                "type": "array",
                "items": false
              }
            }
        }
      """.trimIndent(),
      """
        {
          "foo": [<warning descr="Additional items are not allowed">1</warning>, 2, 3]
        }
      """.trimIndent(),
      """
        {
          "foo": []
        }
      """.trimIndent()
    )
  }

  fun `test array items validation against always valid schema`() {
    doTestSchemaValidation(
      """
        {
            "${dollar}schema": "https://json-schema.org/draft/2020-12/schema",
            "properties": {
              "foo": {
                "type": "array",
                "items": true
              }
            }
        }
      """.trimIndent(),
      """
        {
          "foo": [1, 2, 3]
        }
      """.trimIndent(),
      """
        {
          "foo": [1, 2, "validAnyway"]
        }
      """.trimIndent(),
      """
        {
          "foo": []
        }
      """.trimIndent()
    )
  }

  fun `test prefixItems positional validation`() {
    doTestSchemaValidation(
      """
        {
            "${dollar}schema": "https://json-schema.org/draft/2020-12/schema",
            "prefixItems": [
                {"type": "integer"},
                {"type": "string"}
            ]
        }
      """.trimIndent(),
      """
        [ 1, "two" ]
      """.trimIndent(),
      """
        [ 1, "two", true ]
      """.trimIndent(),
      """
        [ 1, <warning descr="Incompatible types.
         Required: string. Actual: integer.">2</warning> ]
      """.trimIndent()
    )
  }

  fun `test complete properties from if-else schema with reference`() {
    // check that detailed resolve is called before completion to ensure all necessary properties are considered
    val ifThenElseSchemaWithReferenceInElseBranch = """
        {
          "${dollar}schema": "https://json-schema.org/draft/2020-12/schema",
          "if": {
            "type": "object",
            "required": [ "foo" ]
          },
          "then": {
            "properties": {
              "foo_then_1": {
                "type": "string"
              },
              "foo_then_2": {
                "type": "string"
              }
            }
          },
          "else": {
            "${dollar}ref": "#/${dollar}defs/bar"
          },
          "${dollar}defs": {
            "bar": {
              "properties": {
                "foo_else_1": {
                  "type": "integer"
                },
                "foo_else_2": {
                  "type": "integer"
                }
              }
            }
          }
        }
      """.trimIndent()

    // check completion taken from 'then' branch because 'if' evaluates to 'true'
    doTestSchemaCompletion(
      ifThenElseSchemaWithReferenceInElseBranch,
      """
        {
          "foo": {},
          <caret>
        }
      """.trimIndent(),
       CompletionType.SMART,
      "\"foo_then_1\"", "\"foo_then_2\""
    )

    // check completion taken from 'else' branch because 'if' evaluates to 'false'
    doTestSchemaCompletion(
      ifThenElseSchemaWithReferenceInElseBranch,
      """
        {
          <caret>
        }
      """.trimIndent(),
      CompletionType.SMART,
      "\"foo_else_1\"", "\"foo_else_2\""
    )
  }

  fun `test validation by if then else`() {
    doTestSchemaValidation(
      """
        {
          "${dollar}schema": "https://json-schema.org/draft/2020-12/schema",
          "properties": {
            "foo": {
              "${dollar}ref": "#/${dollar}defs/schema-or-reference"
            }
          },
          "${dollar}defs": {
            "schema-or-reference": {
              "if": {
                "type": "object",
                "required": [ "${dollar}ref" ]
              },
              "then": {
                "${dollar}ref": "#/${dollar}defs/reference"
              },
              "else": {
                "${dollar}ref": "#/${dollar}defs/schema"
              }
            },
            "reference": {
              "additionalProperties": false,
              "type": "object",
              "properties": {
                "${dollar}ref": {
                  "type": "string",
                  "format": "uri-reference"
                },
                "summary": {
                  "type": "string"
                },
                "description": {
                  "type": "string"
                }
              }
            },
            "schema": {
              "required": [ "schemaId" ]
            }
          }
        }
      """.trimIndent(),
      """
        {
          "foo": {
            "${dollar}ref": "..."
          }
        }
      """.trimIndent(),
      """
        {
          "foo": {
            "schemaId": 123
          }
        }
      """.trimIndent(),
      """
        {
          "foo": {
            "${dollar}ref": "...",
            <warning descr="Property 'prohibitedAdditionalProperty' is not allowed">"prohibitedAdditionalProperty"</warning>: 123
          }
        }
      """.trimIndent(),
      """
        {
          "foo": <warning descr="Missing required property 'schemaId'">{}</warning>
        }
      """.trimIndent(),
    )
  }

  private fun getResolvedElementText(elementAtCaret: PsiElement): String {
    val propertyKeyAtCaret = elementAtCaret.asSafely<JsonProperty>()?.nameElement
    val target = GotoDeclarationUtil.findTargetElementsFromProviders(propertyKeyAtCaret,
                                                                     myFixture.elementAtCaret.textOffset,
                                                                     myFixture.editor)
    Assert.assertNotNull(target)
    Assert.assertEquals(1, target!!.size)
    val singleTargetParent = target.single().parent
    Assert.assertTrue(singleTargetParent is JsonProperty)
    return singleTargetParent.text
  }

  fun `test gtd from json to json schema's if-then-else branch`() {
    doTestGtdWithSingleTarget(
      loadSchemaTextFromResources("IfElseSchemaWithDepth1.json"),

      """
        {
          "buz": {
            "bar_required": 123,
            "bar_<caret>additional": ""
          }
        }
      """.trimIndent()
        to
        """
        "bar_additional": {
                  "type": "boolean"
                }
      """.trimIndent(),

      """
        {
          "buz": {
            "foo_required": 123,
            "foo_<caret>additional": ""
          }
        }
      """.trimIndent()
        to
        """
        "foo_additional": {
                  "type": "string"
                }
      """.trimIndent()
    ) {
      getResolvedElementText(it)
    }
  }

  fun `test gtd from json to nested if-then-else branch`() {
    doTestGtdWithSingleTarget(
      loadSchemaTextFromResources("IfElseSchemaWithDepth2.json"),

      """
        {
          "buz": {
            "a":  123,
            "fo<caret>o": 123
          }
        }
      """.trimIndent()
        to
        """
        "foo": {
                  "type": "integer"
                }
      """.trimIndent(),

      """
        {
          "buz": {
            "b":  123,
            "fo<caret>o": 123
          }
        }
      """.trimIndent()
        to
        """
        "foo": {
                  "type": "boolean"
                }
      """.trimIndent(),

      """
        {
          "buz": {
            "a":  123,
            "ba<caret>r": 123
          }
        }
      """.trimIndent()
        to
        """
        "bar": {
                  "type": "string"
                }
      """.trimIndent(),

      """
        {
          "buz": {
            "b":  123,
            "ba<caret>r": 123
          }
        }
      """.trimIndent()
        to
        """
        "bar": {
                  "type": "array"
                }
      """.trimIndent()) {
      getResolvedElementText(it)
    }
  }

  fun `test unevaluatedProperties constant support`() {
    doTestSchemaValidation(
      """
        {
          "${dollar}schema": "https://json-schema.org/draft/2020-12/schema",
  
          "type": "object",
          "properties": {
            "test1": {
              "type": "boolean"
            }
          },
          "unevaluatedProperties": false
        }
      """.trimIndent(),
      """
        {
          "test1": true
        }
      """.trimIndent(),
      """
        {
          "test1": true,
          <warning descr="Property is not allowed">"test2": true</warning>
        }
      """.trimIndent()
    )
  }

  fun `test unevaluatedProperties combined with additionalProperties support`() {
    doTestSchemaValidation(
      """
        {
          "${dollar}schema": "https://json-schema.org/draft/2020-12/schema",
  
          "type": "object",
          "properties": {
            "test1": {
              "type": "boolean"
            }
          },
          "additionalProperties": true,
          "unevaluatedProperties": false
        }
      """.trimIndent(),
      """
        {
          "test1": true
        }
      """.trimIndent(),
      """
        {
          "test1": true,
          "test2": true
        }
      """.trimIndent()
    )
  }

  fun `test unevaluatedProperties with pattern`() {
    doTestSchemaValidation(
      """
        {
            "${dollar}schema": "https://json-schema.org/draft/2020-12/schema",
            "type": "object",
            "patternProperties": {
                "^foo": { "type": "string" }
            },
            "unevaluatedProperties": false
        }
      """.trimIndent(),
      """
        {
            "foo": "foo"
        }
      """.trimIndent(),
      """
        {
            "foo": "foo",
            <warning descr="Property is not allowed">"bar": "bar"</warning>
        }
      """.trimIndent()
    )
  }

  fun `test unevaluatedProperties schema support`() {
    doTestSchemaValidation(
      """
        {
          "${dollar}schema": "https://json-schema.org/draft/2020-12/schema",
  
          "type": "object",
          "properties": {
            "test1": {
              "type": "boolean"
            }
          },
          "unevaluatedProperties": {
            "required": ["test3"]
          }
        }
      """.trimIndent(),
      """
        {
          "test1": true
        }
      """.trimIndent(),
      """
        {
          "test1": true,
          "test2": <warning descr="Missing required property 'test3'">{
            "test4": true
          }</warning>
        }
      """.trimIndent()
    )
  }

  fun `test unevaluatedProperties with adjacent nested properties`() {
    doTestSchemaValidation(
      """
        {
            "${dollar}schema": "https://json-schema.org/draft/2020-12/schema",
            "type": "object",
            "properties": {
                "foo": { "type": "string" }
            },
            "allOf": [
                {
                    "properties": {
                        "bar": { "type": "string" }
                    }
                }
            ],
            "unevaluatedProperties": false
        }
      """.trimIndent(),
      """
        {
            "foo": "foo",
            "bar": "bar"
        }
      """.trimIndent(),
      """
        {
            "foo": "foo",
            "bar": "bar",
            <warning descr="Property is not allowed">"baz": "baz"</warning>
        }
      """.trimIndent()
    )
  }

  fun `test unevaluatedItems constant schema validation`() {
    doTestSchemaValidation(
      """
        {
            "${dollar}schema": "https://json-schema.org/draft/2020-12/schema",
            "unevaluatedItems": false
        }
      """.trimIndent(),
      """
        []
      """.trimIndent(),
      """
        [ <warning descr="Unevaluated items are not allowed">"foo"</warning> ]   
      """.trimIndent()
    )
  }

  fun `test unevaluatedItems schema validation`() {
    doTestSchemaValidation(
      """
        {
            "${dollar}schema": "https://json-schema.org/draft/2020-12/schema",
            "unevaluatedItems": { "type": "string" }
        }
      """.trimIndent(),
      """
        []
      """.trimIndent(),
      """
        [ "foo" ]
      """.trimIndent(),
      """
        [ <warning descr="Incompatible types.
         Required: string. Actual: integer.">123</warning> ]     
      """.trimIndent()
    )
  }

  fun `test unevaluatedItems and prefixItems compound validation`() {
    doTestSchemaValidation(
      """
        {
            "${dollar}schema": "https://json-schema.org/draft/2020-12/schema",
            "prefixItems": [
                { "type": "string" }
            ],
            "unevaluatedItems": false
        }
      """.trimIndent(),
      """
        ["foo"]
      """.trimIndent(),
      """
        ["foo", <warning descr="Unevaluated items are not allowed">"bar"</warning>]
      """.trimIndent()
    )
  }

  fun `test if-else basic completion considers all available branches`() {
    doTestSchemaCompletion(
      """
        {
          "${dollar}schema": "https://json-schema.org/draft/2020-12/schema",
          "if": {
            "type": "object",
            "required": [ "foo" ]
          },
          "then": {
            "properties": {
              "bar": {
                "type": "string"
              },
              "biz": {
                "type": "string"
              }
            }
          },
          "else": {
            "properties": {
              "buz": {
                "type": "string"
              }
            }
          }
        }
      """.trimIndent(),
      """
        {
          "foo": "hello",
          <caret>
        }
      """.trimIndent(),
      CompletionType.BASIC,
      "\"bar\"", "\"biz\"", "\"buz\""
    )
  }

  fun `test if-else smart completion considers only valid branch`() {
    doTestSchemaCompletion(
      """
        {
          "${dollar}schema": "https://json-schema.org/draft/2020-12/schema",
          "if": {
            "type": "object",
            "required": [ "foo" ]
          },
          "then": {
            "properties": {
              "bar": {
                "type": "string"
              },
              "biz": {
                "type": "string"
              }
            }
          },
          "else": {
            "properties": {
              "buz": {
                "type": "string"
              }
            }
          }
        }
      """.trimIndent(),
      """
        {
          "foo": "hello",
          <caret>
        }
      """.trimIndent(),
      CompletionType.SMART,
      "\"bar\"", "\"biz\""
    )
  }

  fun `test if-then-else schema with adjacent properties completion`() {
    // todo implement inheritanceStrategy for if-else, oneOf/anyOF/allOf
    // and generic cases and write a test with inherited required properties as well
    doTestSchemaCompletion(
      """
        {
          "${dollar}schema": "https://json-schema.org/draft/2020-12/schema",
          "type": "object",
          
          "if": {
            "type": "object",
            "required": [ "foo" ]
          },
          "then": {
            "properties": {
              "bar": {
                "type": "string"
              }
            }
          },
          "else": {
            "properties": {
              "wrong": {
                "type": "string"
              }
            }
          },
          "properties": {
            "buz": {
              "type": "string"
            }
          }
        }
      """.trimIndent(),
      """
        {
          "foo": "hello"
          <caret>
        }
      """.trimIndent(),
      CompletionType.SMART,
      "\"bar\"", "\"buz\""
    )
  }
}
