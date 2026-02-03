// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.jetbrains.jsonSchema.JsonSchemaHighlightingTest
import org.intellij.lang.annotations.Language
import org.junit.Assert

class JsonBySchemaCompletionTest : JsonBySchemaCompletionBaseTest() {
  fun testTopLevel() {
    testImpl("""{"properties": {"prima": {}, "proto": {}, "primus": {}}}""", "{<caret>}", "\"prima\"", "\"primus\"", "\"proto\"")
  }

  fun testTopLevelVariant() {
    testImpl("""{"properties": {"prima": {}, "proto": {}, "primus": {}}}""", "{\"pri<caret>\"}", "prima", "primus", "proto")
  }

  fun testBoolean() {
    testImpl("""{"properties": {"prop": {"type": "boolean"}}}""", "{\"prop\": <caret>}", "false", "true")
  }

  fun testEnum() {
    testImpl("""{"properties": {"prop": {"enum": ["prima", "proto", "primus"]}}}""",
             """{"prop": <caret>}""", "\"prima\"", "\"primus\"", "\"proto\"")
  }

  fun testEnumInArrayOfUniqueItems() {
    // don't suggest the same enum elements again if the parent array assumes unique items
    testImpl("""{"properties": {"prop": { "type": "array", "items": {"enum": ["prima", "proto", "primus"]}, "uniqueItems": true}}}""",
             """{"prop": ["prima", <caret>]}""", "\"primus\"", "\"proto\"")
  }

  fun testTopLevelAnyOfValues() {
    testImpl("""{"properties": {"prop": {"anyOf": [{"enum": ["prima", "proto", "primus"]},""" + "{\"type\": \"boolean\"}]}}}",
             """{"prop": <caret>}""", "\"prima\"", "\"primus\"", "\"proto\"", "false", "true")
  }

  fun testTopLevelAnyOf() {
    testImpl(
      """{"anyOf": [ {"properties": {"prima": {}, "proto": {}, "primus": {}}},""" + "{\"properties\": {\"abrakadabra\": {}}}]}",
      """{<caret>}""", "\"abrakadabra\"", "\"prima\"", "\"primus\"", "\"proto\"")
  }

  fun testSimpleHierarchy() {
    testImpl("""{"properties": {"top": {"properties": {"prima": {}, "proto": {}, "primus": {}}}}}""",
             """{"top": {<caret>}}""", "\"prima\"", "\"primus\"", "\"proto\"")
  }

  fun testObjectsInsideArray() {
    val schema = """{"properties": {"prop": {"type": "array", "items":
      {"type": "object","properties": {"innerType":{}, "innerValue":{}}, "additionalProperties": false}}}}"""
    testImpl(schema, """{"prop": [{<caret>}]}""", "\"innerType\"", "\"innerValue\"")
  }

  fun testObjectValuesInsideArray() {
    val schema = """{"properties": {"prop": {"type": "array", "items":
      {"type": "object","properties": {"innerType":{"enum": [115,117, "nothing"]}, "innerValue":{}}, "additionalProperties": false}}}}"""
    testImpl(schema, """{"prop": [{"innerType": <caret>}]}""", "\"nothing\"", "115", "117")
  }

  fun testLowLevelOneOf() {
    val schema = """{"properties": {"prop": {"type": "array", "items":
      {"type": "object","properties": {"innerType":{"oneOf": [{"properties": {"a1": {}, "a2": {}}},
      {"properties": {"b1": {}, "b2": {}}}]}, "innerValue":{}}, "additionalProperties": false}}}}"""
    testImpl(schema, """{"prop": [{"innerType": {<caret>}}]}""", "\"a1\"", "\"a2\"", "\"b1\"", "\"b2\"")
  }

  fun testArrayValuesInsideObject() {
    val schema = """{"properties": {"prop": {"type": "array","items": {"enum": [1,2,3]}}}}"""
    testImpl(schema, """{"prop": [<caret>]}""", "1", "2", "3")
  }

  fun testAllOfTerminal() {
    val schema = """{"allOf": [{"type": "object", "properties": {"first": {}}}, {"properties": {"second": {"enum": [33,44]}}}]}"""
    testImpl(schema, """{"<caret>"}""", "first", "second")
  }

  fun testAllOfInTheMiddle() {
    val schema = """{"allOf": [{"type": "object", "properties": {"first": {}}}, {"properties": {"second": {"enum": [33,44]}}}]}"""
    testImpl(schema, """{"second": <caret>}""", "33", "44")
  }

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

  fun testTopLevelArrayPropNameCompletion() {
    val schema = parcelShopSchema()
    testImpl(schema, "[{<caret>}]", "\"address\"")
    testImpl(schema, """[{"address": {<caret>}}]""", "\"fax\"", "\"houseNumber\"")
    testImpl(schema, """[{"address": {"houseNumber": <caret>}}]""", "1", "2")
  }

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

  fun testRootObjectRedefined() {
    testImpl(JsonSchemaHighlightingTest.rootObjectRedefinedSchema(), "{<caret>}", "\"r1\"", "\"r2\"")
  }

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
    testImpl(schema, "{<caret>}", "\"source\"")
    Assert.assertEquals(1, myItems.size.toLong())
    val presentation = LookupElementPresentation()
    myItems[0].renderElement(presentation)
    Assert.assertEquals("Target description", presentation.typeText)
  }

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
    testImpl(schema, "{<caret>}", "\"source\"")
    Assert.assertEquals(1, myItems.size.toLong())
    val presentation = LookupElementPresentation()
    myItems[0].renderElement(presentation)
    Assert.assertEquals("Target title", presentation.typeText)
  }

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
                       vararg variants: String) {
    testBySchema(schema, text, "someFile.json", LookupElement::getLookupString, CompletionType.SMART, *variants)
  }

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

  fun testIfThenElseV7EmptyPropName() {
    testImpl(ifThenElseSchema, "{<caret>}", "\"c\"")
    Assert.assertEquals(1, myItems.size.toLong())
    val presentation = LookupElementPresentation()
    myItems[0].renderElement(presentation)
    Assert.assertEquals("Target c description", presentation.typeText)
  }

  fun testIfThenElseV7ThenPropName() {
    testImpl(ifThenElseSchema, """{"a": "a", <caret>}""", "\"b\"")
    Assert.assertEquals(1, myItems.size.toLong())
    val presentation = LookupElementPresentation()
    myItems[0].renderElement(presentation)
    Assert.assertEquals("Target b description", presentation.typeText)
  }

  fun testIfThenElseV7ElsePropName() {
    testImpl(ifThenElseSchema, """{"a": 5, <caret>}""", "\"c\"")
    Assert.assertEquals(1, myItems.size.toLong())
    val presentation = LookupElementPresentation()
    myItems[0].renderElement(presentation)
    Assert.assertEquals("Target c description", presentation.typeText)
  }

  fun testIfThenElseV7ElsePropValue() {
    testImpl(ifThenElseSchema, """{"a": 5, "c": <caret>}""", "false", "true")
    assertThat(myItems).hasSize(2)
  }

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
    testImpl(schema, "{<caret>}", "\"lint\"", "\"lint2\"")
    Assert.assertEquals(2, myItems.size.toLong())

    val actualCompletions = myItems.map { it.lookupString to renderPresentation(it).typeText }.toList()
    UsefulTestCase.assertSameElements(actualCompletions,
                                      listOf("\"lint\"" to "Run code quality tools, e.g. ESLint, TSLint, etc.",
                                             "\"lint2\"" to "Run code quality tools."))
  }

  private fun renderPresentation(lookupElement: LookupElement): LookupElementPresentation = LookupElementPresentation().also {
    lookupElement.renderElement(it)
  }

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
}
