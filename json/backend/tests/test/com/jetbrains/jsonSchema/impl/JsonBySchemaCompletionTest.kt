// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.jsonSchema.JsonSchemaHighlightingTest
import com.jetbrains.jsonSchema.impl.JsonSchemaCompletionTestUtil.testBySchema
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
class JsonBySchemaCompletionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun testTopLevel() {
    testImpl("""{"properties": {"prima": {}, "proto": {}, "primus": {}}}""", "{<caret>}", "\"prima\"", "\"primus\"", "\"proto\"")
  }

  @Test
  fun testTopLevelVariant() {
    testImpl("""{"properties": {"prima": {}, "proto": {}, "primus": {}}}""", "{\"pri<caret>\"}", "prima", "primus", "proto")
  }

  @Test
  fun testBoolean() {
    testImpl("""{"properties": {"prop": {"type": "boolean"}}}""", "{\"prop\": <caret>}", "false", "true")
  }

  @Test
  fun testEnum() {
    testImpl("""{"properties": {"prop": {"enum": ["prima", "proto", "primus"]}}}""",
             """{"prop": <caret>}""", "\"prima\"", "\"primus\"", "\"proto\"")
  }

  @Test
  fun testEnumInArrayOfUniqueItems() {
    // don't suggest the same enum elements again if the parent array assumes unique items
    testImpl("""{"properties": {"prop": { "type": "array", "items": {"enum": ["prima", "proto", "primus"]}, "uniqueItems": true}}}""",
             """{"prop": ["prima", <caret>]}""", "\"primus\"", "\"proto\"")
  }

  @Test
  fun testTopLevelAnyOfValues() {
    testImpl("""{"properties": {"prop": {"anyOf": [{"enum": ["prima", "proto", "primus"]},""" + "{\"type\": \"boolean\"}]}}}",
             """{"prop": <caret>}""", "\"prima\"", "\"primus\"", "\"proto\"", "false", "true")
  }

  @Test
  fun testTopLevelAnyOf() {
    testImpl(
      """{"anyOf": [ {"properties": {"prima": {}, "proto": {}, "primus": {}}},""" + "{\"properties\": {\"abrakadabra\": {}}}]}",
      """{<caret>}""", "\"abrakadabra\"", "\"prima\"", "\"primus\"", "\"proto\"")
  }

  @Test
  fun testSimpleHierarchy() {
    testImpl("""{"properties": {"top": {"properties": {"prima": {}, "proto": {}, "primus": {}}}}}""",
             """{"top": {<caret>}}""", "\"prima\"", "\"primus\"", "\"proto\"")
  }

  @Test
  fun testObjectsInsideArray() {
    val schema = """{"properties": {"prop": {"type": "array", "items":
      {"type": "object","properties": {"innerType":{}, "innerValue":{}}, "additionalProperties": false}}}}"""
    testImpl(schema, """{"prop": [{<caret>}]}""", "\"innerType\"", "\"innerValue\"")
  }

  @Test
  fun testObjectValuesInsideArray() {
    val schema = """{"properties": {"prop": {"type": "array", "items":
      {"type": "object","properties": {"innerType":{"enum": [115,117, "nothing"]}, "innerValue":{}}, "additionalProperties": false}}}}"""
    testImpl(schema, """{"prop": [{"innerType": <caret>}]}""", "\"nothing\"", "115", "117")
  }

  @Test
  fun testLowLevelOneOf() {
    val schema = """{"properties": {"prop": {"type": "array", "items":
      {"type": "object","properties": {"innerType":{"oneOf": [{"properties": {"a1": {}, "a2": {}}},
      {"properties": {"b1": {}, "b2": {}}}]}, "innerValue":{}}, "additionalProperties": false}}}}"""
    testImpl(schema, """{"prop": [{"innerType": {<caret>}}]}""", "\"a1\"", "\"a2\"", "\"b1\"", "\"b2\"")
  }

  @Test
  fun testArrayValuesInsideObject() {
    val schema = """{"properties": {"prop": {"type": "array","items": {"enum": [1,2,3]}}}}"""
    testImpl(schema, """{"prop": [<caret>]}""", "1", "2", "3")
  }

  @Test
  fun testAllOfTerminal() {
    val schema = """{"allOf": [{"type": "object", "properties": {"first": {}}}, {"properties": {"second": {"enum": [33,44]}}}]}"""
    testImpl(schema, """{"<caret>"}""", "first", "second")
  }

  @Test
  fun testAllOfInTheMiddle() {
    val schema = """{"allOf": [{"type": "object", "properties": {"first": {}}}, {"properties": {"second": {"enum": [33,44]}}}]}"""
    testImpl(schema, """{"second": <caret>}""", "33", "44")
  }

  @Test
  fun testValueCompletion() {
    val schema = """{
  "properties": {
    "top": {
      "enum": ["test", "me"]
    }
  }
}"""
    testImpl(schema, """{"top": <caret>}""", "\"me\"", "\"test\"")
  }

  @Test
  fun testTopLevelArrayPropNameCompletion() {
    val schema = parcelShopSchema()
    testImpl(schema, "[{<caret>}]", "\"address\"")
    testImpl(schema, """[{"address": {<caret>}}]""", "\"fax\"", "\"houseNumber\"")
    testImpl(schema, """[{"address": {"houseNumber": <caret>}}]""", "1", "2")
  }

  @Test
  fun testPatternPropertyCompletion() {
    val schema = """{
  "patternProperties": {
    "C": {
      "enum": ["test", "em"]
    }
  }
}"""
    testImpl(schema, """{"Cyan": <caret>}""", "\"em\"", "\"test\"")
  }

  @Test
  fun testRootObjectRedefined() {
    testImpl(JsonSchemaHighlightingTest.rootObjectRedefinedSchema(), "{<caret>}", "\"r1\"", "\"r2\"")
  }

  @Test
  fun testSimpleNullCompletion() {
    val schema = """{
  "properties": {
    "null": {
      "type": "null"
    }
  }
}"""
    testImpl(schema, """{"null": <caret>}""", "null")
  }

  @Test
  fun testNullCompletionInEnum() {
    val schema = """{
  "properties": {
    "null": {
      "type": ["null", "integer"],
      "enum": [null, 1, 2]
    }
  }
}"""
    testImpl(schema, """{"null": <caret>}""", "1", "2", "null")
  }

  @Test
  fun testNullCompletionInTypeVariants() {
    val schema = """{
  "properties": {
    "null": {
      "type": ["null", "boolean"]
    }
  }
}"""
    testImpl(schema, """{"null": <caret>}""", "false", "null", "true")
  }

  @Test
  fun testDescriptionFromDefinitionInCompletion() {
    val schema = """{
  "definitions": {
    "target": {
      "description": "Target description"
    }
  },
  "properties": {
    "source": {
      "${"$"}ref": "#/definitions/target"
    }
  }
}"""
    val items = testImpl(schema, "{<caret>}", "\"source\"")
    assertEquals(1, items.size)
    val presentation = LookupElementPresentation()
    runReadActionBlocking { items[0].renderElement(presentation) }
    assertEquals("Target description", presentation.typeText)
  }

  @Test
  fun testDescriptionFromTitleInCompletion() {
    val schema = """{
  "definitions": {
    "target": {
      "title": "Target title",
      "description": "Target description"
    }
  },
  "properties": {
    "source": {
      "${"$"}ref": "#/definitions/target"
    }
  }
}"""
    val items = testImpl(schema, "{<caret>}", "\"source\"")
    assertEquals(1, items.size)
    val presentation = LookupElementPresentation()
    runReadActionBlocking { items[0].renderElement(presentation) }
    assertEquals("Target title", presentation.typeText)
  }

  @Test
  fun testAnyOfInsideAllOfWithInnerProperties() {
    val schema = """
{
  "definitions": {
    "aaa": {
      "properties": {
        "aaa_prop": {}
      }
    },
    "bbb": {
      "properties": {
        "bbb_prop": {}
      }
    },
    "excl1": {
      "properties": {
        "excl1_prop": {}
      }
    },
    "excl2": {
      "properties": {
        "excl2_prop": {}
      }
    }
  },
  "allOf": [
    {"${"$"}ref": "#/definitions/aaa"},
    {"${"$"}ref": "#/definitions/bbb"},
    {
      "anyOf": [
        {"${"$"}ref": "#/definitions/excl1"},
        {"${"$"}ref": "#/definitions/excl2"}
      ]
    }
  ]
}"""
    testImpl(schema, "{<caret>}", "\"aaa_prop\"", "\"bbb_prop\"", "\"excl1_prop\"", "\"excl2_prop\"")
  }

  private fun parcelShopSchema(): String {
    return """{
  "${"$"}schema": "http://json-schema.org/draft-04/schema#",

  "title": "parcelshop search response schema",

  "definitions": {
    "address": {
      "type": "object",
      "properties": {
        "houseNumber": { "type": "integer", "enum": [1,2]},
        "fax": { "${"$"}ref": "#/definitions/phone" }
      }
    },
    "phone": {
      "type": "object",
      "properties": {
        "countryPrefix": { "type": "string" },
        "number": { "type": "string" }
      }
    }
  },

  "type": "array",

  "items": {
    "type": "object",
    "properties": {
      "address": { "${"$"}ref": "#/definitions/address" }
    }
  }
}"""
  }

  private fun testImpl(@Language("JSON") schema: String, text: String,
                       vararg variants: String): List<LookupElement> =
    testBySchema(myFixture, schema, text, "someFile.json", LookupElement::getLookupString, CompletionType.SMART, *variants)

  private val ifThenElseSchema: String
    get() {
      @Suppress("UnnecessaryVariable")
      @Language("JSON") val schema = """{
    "if": {
      "properties": {
        "a": {
          "type": "string"
        }
      },
      "required": ["a"]
    },
    "then": {
      "properties": {
        "b": {
          "type": "number",
          "description": "Target b description"
        }
      },
      "required": ["b"]
    },
    "else": {
      "properties": {
        "c": {
          "type": "boolean",
          "description": "Target c description"
        }
      },
      "required": ["c"]
    }
  }"""
      return schema
    }

  @Test
  fun testIfThenElseV7EmptyPropName() {
    val items = testImpl(ifThenElseSchema, "{<caret>}", "\"c\"")
    assertEquals(1, items.size)
    val presentation = LookupElementPresentation()
    runReadActionBlocking { items[0].renderElement(presentation) }
    assertEquals("Target c description", presentation.typeText)
  }

  @Test
  fun testIfThenElseV7ThenPropName() {
    val items = testImpl(ifThenElseSchema, """{"a": "a", <caret>}""", "\"b\"")
    assertEquals(1, items.size)
    val presentation = LookupElementPresentation()
    runReadActionBlocking { items[0].renderElement(presentation) }
    assertEquals("Target b description", presentation.typeText)
  }

  @Test
  fun testIfThenElseV7ElsePropName() {
    val items = testImpl(ifThenElseSchema, """{"a": 5, <caret>}""", "\"c\"")
    assertEquals(1, items.size)
    val presentation = LookupElementPresentation()
    runReadActionBlocking { items[0].renderElement(presentation) }
    assertEquals("Target c description", presentation.typeText)
  }

  @Test
  fun testIfThenElseV7ElsePropValue() {
    val items = testImpl(ifThenElseSchema, """{"a": 5, "c": <caret>}""", "false", "true")
    assertThat(items).hasSize(2)
  }

  @Test
  fun testNestedPropsMerging() {
    testImpl("""{
  "allOf": [
    {
      "properties": {
        "severity": {
          "type": "string",
          "enum": ["a", "b"]
        }
      }
    },
    {
      "properties": {
        "severity": {
        }
      }
    }
  ]
}""","""{
  "severity": <caret>
}""", "\"a\"", "\"b\"")
  }

  @Test
  fun testFirstSentenceInCompletion() {
    val schema = """{
  "properties": {
    "lint": {
      "type": "string",
      "description": "Run code quality tools, e.g. ESLint, TSLint, etc."
    },
    "lint2": {
      "type": "string",
      "description": "Run code quality tools. For example, ESLint, TSLint, etc."
    }
  }
}"""
    val items = testImpl(schema, "{<caret>}", "\"lint\"", "\"lint2\"")
    assertEquals(2, items.size)

    val actualCompletions = items.map { it.lookupString to renderPresentation(it).typeText }.toList()
    assertThat(actualCompletions).containsExactlyInAnyOrder("\"lint\"" to "Run code quality tools, e.g. ESLint, TSLint, etc.",
                                                            "\"lint2\"" to "Run code quality tools.")
  }

  private fun renderPresentation(lookupElement: LookupElement): LookupElementPresentation = LookupElementPresentation().also {
    runReadActionBlocking { lookupElement.renderElement(it) }
  }

  @Test
  fun testNoDefaultString() {
    val schema = """{
  "properties": {
    "prop": {
      "type": "string"
    }
  }
}"""
    testImpl(schema, """{"prop": <caret>}""")
  }

  @Test
  fun testDefaultEmptyString() {
    val schema = """{
  "properties": {
    "prop": {
      "type": "string",
      "default": ""
    }
  }
}"""
    testImpl(schema, """{"prop": <caret>}""", "\"\"")
  }

  @Test
  fun testPatternPropertiesWithAlternation() {
    val schema = """{
  "patternProperties": {
    "^v?\\d+(\\.\\d+){0,3}|^dev-": {
      "type": "object",
      "properties": {
        "enabled": {
          "type": "boolean"
        }
      }
    }
  }
}"""
    testImpl(schema, """{"1.0.0-dev": {<caret>}}""", "\"enabled\"")
    testImpl(schema, """{"dev-main": {<caret>}}""", "\"enabled\"")
    testImpl(schema, """{"invalid-version": {<caret>}}""")
  }
}
