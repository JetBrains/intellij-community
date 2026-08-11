// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema

import org.intellij.lang.annotations.Language

/**
 * Tests derived from real YouTrack user reports to ensure the networknt validator
 * handles these scenarios correctly. Some are regressions fixed by networknt
 * (the old IJ validator produced false positives/negatives), others are
 * compatibility tests (both validators pass).
 *
 * Verified against old IJ validator (2026-03-17):
 *  - Category 1 (keyword-as-property-name): all PASS with old validator too — compatibility tests
 *  - Category 2 (oneOf/anyOf): all FAIL with old validator — genuine regressions fixed by networknt
 *  - Category 3 (other bugs): IJPL-157612, IJPL-63702, IJPL-189322 FAIL with old validator — genuine regressions;
 *    IJPL-165566 PASSES with old validator — compatibility test
 */
class JsonSchemaUserRegressionNetworkntTest : JsonSchemaHighlightingNetworkntTest() {

  // region Category 1: Keyword-as-property-name (draft 2020-12)

  /** https://youtrack.jetbrains.com/issue/IJPL-181879 */
  fun testIJPL_181879_typeAsPropertyName() {
    @Language("JSON") val schema = """{"${"$"}schema":"https://json-schema.org/draft/2020-12/schema","type":"object","properties":{"type":{"enum":["One","Two"]}}}"""
    doTest(schema, """{"type":"One"}""")
  }

  /** https://youtrack.jetbrains.com/issue/IJPL-184297 */
  fun testIJPL_184297_multipleKeywordNamedProperties() {
    @Language("JSON") val schema = """{"${"$"}schema":"https://json-schema.org/draft/2020-12/schema","type":"object","properties":{"prop1":{"type":"string"},"type":{"type":"string"},"description":{"type":"string"},"title":{"type":"string"}}}"""
    doTest(schema, """{"prop1":"a","type":"b","description":"c","title":"d"}""")
  }

  /** https://youtrack.jetbrains.com/issue/IJPL-178641 */
  fun testIJPL_178641_readOnlyAsPropertyName() {
    @Language("JSON") val schema = """{"${"$"}schema":"https://json-schema.org/draft/2020-12/schema","properties":{"readOnly":{"type":"string"}}}"""
    doTest(schema, """{"readOnly":"obviously"}""")
  }

  /** https://youtrack.jetbrains.com/issue/IJPL-196556 — default keyword as property value */
  fun testIJPL_196556_defaultKeywordAsPropertyValue() {
    @Language("JSON") val schema = """{"${"$"}schema":"https://json-schema.org/draft/2020-12/schema","type":"object","properties":{"name":{"type":"string","default":"test"}}}"""
    doTest(schema, """{"name":"hello"}""")
  }

  /** https://youtrack.jetbrains.com/issue/IJPL-196556 — ${'$'}schema as property name */
  fun testIJPL_196556_dollarSchemaAsPropertyName() {
    @Language("JSON") val schema = """{"${"$"}schema":"https://json-schema.org/draft/2020-12/schema","type":"object","properties":{"${"$"}schema":{"enum":["./schema.json"]},"something":{"type":"string"}},"required":["${"$"}schema","something"]}"""
    doTest(schema, """{"${"$"}schema":"./schema.json","something":"value"}""")
  }

  // endregion

  // region Category 2: oneOf/anyOf false positives

  /** https://youtrack.jetbrains.com/issue/IJPL-63581 */
  fun testIJPL_63581_oneOfWrappingAnyOf() {
    @Language("JSON") val schema = """{"${"$"}schema":"http://json-schema.org/draft/2020-12/schema","oneOf":[{"anyOf":[{"required":["x"]},{"required":["y"]}]}]}"""
    // Old IJ validator incorrectly reported "validates to more than one variant"
    doTest(schema, """{"x":1,"y":2}""")
  }

  /** https://youtrack.jetbrains.com/issue/IJPL-63680 */
  fun testIJPL_63680_anyOfNestedInOneOfWithAdditionalProperties() {
    @Language("JSON") val schema = """
      {
        "${"$"}schema": "http://json-schema.org/draft-07/schema#",
        "type": "object",
        "properties": {
          "sample": {
            "oneOf": [
              {
                "type": "object",
                "anyOf": [{"required": ["a"]}, {"required": ["b"]}],
                "additionalProperties": false,
                "properties": {
                  "a": {"type": "string"},
                  "b": {"type": "string"}
                }
              },
              {
                "type": "object",
                "additionalProperties": false,
                "properties": {
                  "c": {"type": "string"}
                }
              }
            ]
          }
        }
      }"""
    doTest(schema, """{"sample":{"a":"This is","b":"a test"}}""")
  }

  /** https://youtrack.jetbrains.com/issue/IJPL-156378 */
  fun testIJPL_156378_oneOfWithConst() {
    @Language("JSON") val schema = """
      {
        "${"$"}schema": "http://json-schema.org/draft-07/schema#",
        "type": "object",
        "properties": {
          "data": {
            "type": "array",
            "items": {
              "type": "object",
              "properties": {
                "compareOperator": {"type": "string", "enum": ["EQ", "isNull"]},
                "sourcePath": {"type": "string"}
              },
              "oneOf": [
                {"required": ["sourcePath"]},
                {"properties": {"compareOperator": {"type": "string", "const": "isNull"}}}
              ],
              "required": ["compareOperator"]
            }
          }
        }
      }"""
    doTest(schema, """{"data":[{"compareOperator":"EQ","sourcePath":"id"}]}""")
  }

  // endregion

  // region Category 3: Other validation bugs

  /** https://youtrack.jetbrains.com/issue/IJPL-157612 */
  fun testIJPL_157612_multipleOfFloatPrecision() {
    @Language("JSON") val schema = """{"${"$"}schema":"http://json-schema.org/draft-07/schema#","type":"object","properties":{"value":{"type":"number","multipleOf":0.01}}}"""
    // Old IJ validator incorrectly reported a multipleOf violation due to floating-point precision issues
    doTest(schema, """{"value":10.01}""")
  }

  /** https://youtrack.jetbrains.com/issue/IJPL-165566 */
  fun testIJPL_165566_regexPatternWithEscapes() {
    @Language("JSON") val schema = """{"${"$"}schema":"http://json-schema.org/draft-07/schema","type":"object","required":["test"],"properties":{"test":{"type":"string","pattern":"^[\\da-f]{8}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{12}$"}},"additionalProperties":false}"""
    // Valid UUID — old IJ validator incorrectly flagged the regex pattern as invalid
    doTest(schema, """{"test":"5b306cba-9c71-49db-96c3-d17ca2379c4d"}""")
  }

  /**
   * https://youtrack.jetbrains.com/issue/IJPL-63702
   *
   * FALSE NEGATIVE test: the old IJ validator MISSED this error.
   * networknt correctly catches that "foo" (empty array) violates the patternProperties
   * rule "f.o" which requires minItems:2.
   */
  fun testIJPL_63702_patternPropertiesMinItemsViolation() {
    @Language("JSON") val schema = """
      {
        "${"$"}schema": "http://json-schema.org/draft-07/schema#",
        "type": "object",
        "properties": {
          "foo": {"type": "array", "maxItems": 3},
          "bar": {"type": "array"}
        },
        "patternProperties": {
          "f.o": {"minItems": 2}
        },
        "additionalProperties": {"type": "integer"}
      }"""
    // "foo" matches the "f.o" pattern, requiring minItems:2, but the array is empty — networknt correctly reports this
    doTest(schema, """{"foo":<warning descr="Array is shorter than 2">[]</warning>}""")
  }

  /**
   * https://youtrack.jetbrains.com/issue/IJPL-189322
   *
   * Missing required property "f1" — the instance is an empty object so the required
   * constraint must fire regardless of the if/then branch.
   */
  fun testIJPL_189322_ifThenWithRequiredAndPropertyNames() {
    @Language("JSON") val schema = """
      {
        "${"$"}schema": "http://json-schema.org/draft-07/schema#",
        "type": "object",
        "additionalProperties": false,
        "properties": {
          "f1": {"type": "string"},
          "f2": {"type": "string", "enum": ["q", "w", "other"]},
          "f3": {"type": "string"},
          "f4": {"type": "string"}
        },
        "required": ["f1"],
        "if": {"properties": {"f2": {"const": "q"}}},
        "then": {"propertyNames": {"enum": ["f1", "f2", "f3"]}}
      }"""
    // Empty object is missing required "f1"
    doTest(schema, """<warning descr="Missing required property 'f1'">{}</warning>""")
  }

  // endregion
}
