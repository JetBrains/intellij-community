package com.jetbrains.jsonSchema.impl

import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.jetbrains.jsonSchema.JsonSchemaHighlightingTest
import org.junit.Assert

/**
 * @author Irina.Chernushina on 10/1/2015.
 */
class JsonBySchemaCompletionTest : JsonBySchemaCompletionBaseTest() {
  @Throws(Exception::class)
  fun testTopLevel() {
    testImpl("""{"properties": {"prima": {}, "proto": {}, "primus": {}}}""", "{<caret>}", "\"prima\"", "\"primus\"", "\"proto\"")
  }

  @Throws(Exception::class)
  fun testTopLevelVariant() {
    testImpl("""{"properties": {"prima": {}, "proto": {}, "primus": {}}}""", "{\"pri<caret>\"}", "prima", "primus", "proto")
  }

  @Throws(Exception::class)
  fun testBoolean() {
    testImpl("""{"properties": {"prop": {"type": "boolean"}}}""", "{\"prop\": <caret>}", "false", "true")
  }

  @Throws(Exception::class)
  fun testEnum() {
    testImpl("""{"properties": {"prop": {"enum": ["prima", "proto", "primus"]}}}""",
             """{"prop": <caret>}""", "\"prima\"", "\"primus\"", "\"proto\"")
  }

  @Throws(Exception::class)
  fun testTopLevelAnyOfValues() {
    testImpl("""{"properties": {"prop": {"anyOf": [{"enum": ["prima", "proto", "primus"]},""" + "{\"type\": \"boolean\"}]}}}",
             """{"prop": <caret>}""", "\"prima\"", "\"primus\"", "\"proto\"", "false", "true")
  }

  @Throws(Exception::class)
  fun testTopLevelAnyOf() {
    testImpl(
      """{"anyOf": [ {"properties": {"prima": {}, "proto": {}, "primus": {}}},""" + "{\"properties\": {\"abrakadabra\": {}}}]}",
      """{<caret>}""", "\"abrakadabra\"", "\"prima\"", "\"primus\"", "\"proto\"")
  }

  @Throws(Exception::class)
  fun testSimpleHierarchy() {
    testImpl("""{"properties": {"top": {"properties": {"prima": {}, "proto": {}, "primus": {}}}}}""",
             """{"top": {<caret>}}""", "\"prima\"", "\"primus\"", "\"proto\"")
  }

  @Throws(Exception::class)
  fun testObjectsInsideArray() {
    val schema = """{"properties": {"prop": {"type": "array", "items":
      {"type": "object","properties": {"innerType":{}, "innerValue":{}}, "additionalProperties": false}}}}"""
    testImpl(schema, """{"prop": [{<caret>}]}""", "\"innerType\"", "\"innerValue\"")
  }

  @Throws(Exception::class)
  fun testObjectValuesInsideArray() {
    val schema = """{"properties": {"prop": {"type": "array", "items":
      {"type": "object","properties": {"innerType":{"enum": [115,117, "nothing"]}, "innerValue":{}}, "additionalProperties": false}}}}"""
    testImpl(schema, """{"prop": [{"innerType": <caret>}]}""", "\"nothing\"", "115", "117")
  }

  @Throws(Exception::class)
  fun testLowLevelOneOf() {
    val schema = """{"properties": {"prop": {"type": "array", "items":
      {"type": "object","properties": {"innerType":{"oneOf": [{"properties": {"a1": {}, "a2": {}}},
      {"properties": {"b1": {}, "b2": {}}}]}, "innerValue":{}}, "additionalProperties": false}}}}"""
    testImpl(schema, """{"prop": [{"innerType": {<caret>}}]}""", "\"a1\"", "\"a2\"", "\"b1\"", "\"b2\"")
  }

  @Throws(Exception::class)
  fun testArrayValuesInsideObject() {
    val schema = """{"properties": {"prop": {"type": "array","items": {"enum": [1,2,3]}}}}"""
    testImpl(schema, """{"prop": [<caret>]}""", "1", "2", "3")
  }

  @Throws(Exception::class)
  fun testAllOfTerminal() {
    val schema = """{"allOf": [{"type": "object", "properties": {"first": {}}}, {"properties": {"second": {"enum": [33,44]}}}]}"""
    testImpl(schema, """{"<caret>"}""", "first", "second")
  }

  @Throws(Exception::class)
  fun testAllOfInTheMiddle() {
    val schema = """{"allOf": [{"type": "object", "properties": {"first": {}}}, {"properties": {"second": {"enum": [33,44]}}}]}"""
    testImpl(schema, """{"second": <caret>}""", "33", "44")
  }

  @Throws(Exception::class)
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

  @Throws(Exception::class)
  fun testTopLevelArrayPropNameCompletion() {
    val schema = parcelShopSchema()
    testImpl(schema, "[{<caret>}]", "\"address\"")
    testImpl(schema, """[{"address": {<caret>}}]""", "\"fax\"", "\"houseNumber\"")
    testImpl(schema, """[{"address": {"houseNumber": <caret>}}]""", "1", "2")
  }

  @Throws(Exception::class)
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

  @Throws(Exception::class)
  fun testRootObjectRedefined() {
    testImpl(JsonSchemaHighlightingTest.rootObjectRedefinedSchema(), "{<caret>}", "\"r1\"", "\"r2\"")
  }

  @Throws(Exception::class)
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

  @Throws(Exception::class)
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

  @Throws(Exception::class)
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

  @Throws(Exception::class)
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

  @Throws(Exception::class)
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

  @Throws(Exception::class)
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

  @Throws(Exception::class)
  private fun testImpl(schema: String, text: String,
                       vararg variants: String) {
    testBySchema(schema, text, ".json", *variants)
  }
}
