// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema

import com.intellij.json.networknt.wrapper.NetworkntValidationBridgeImpl
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.replaceService
import com.jetbrains.jsonSchema.impl.NetworkntValidationBridge
import java.io.File

/**
 * Runs ALL tests from [JsonSchemaHighlightingTest] with the networknt validation engine enabled.
 *
 * This gives us ~90 test scenarios covering type errors, composition (oneOf/anyOf/allOf),
 * $ref, patternProperties, if/then/else, and many edge cases.
 */
open class JsonSchemaHighlightingNetworkntTest : JsonSchemaHighlightingTest() {

  override fun setUp() {
    super.setUp()
    Registry.get("json.schema.use.networknt.validation").setValue(true, testRootDisposable)
    project.replaceService(NetworkntValidationBridge::class.java, NetworkntValidationBridgeImpl(project), testRootDisposable)
  }

  // === Overrides for tests where networknt correctly produces different/additional warnings ===

  override fun testAcceptSchemaWithoutType() {
    // networknt correctly detects oneOf multi-match: "localhost" matches both format branches
    val schema = """
      {
        "properties": {
          "withFormat": {
            "oneOf": [
              { "format":"hostname" },
              { "format": "ip4" }
            ]
          }
        }
      }"""
    doTest(schema, """{"withFormat": <warning descr="Validates to more than one variant">"localhost"</warning>}""")
  }

  override fun testDoNotMarkOneOfThatDiffersWithFormat() {
    // networknt correctly detects oneOf multi-match: "localhost" matches both format branches
    val schema = """
      {
        "properties": {
          "withFormat": {
            "type": "string",
            "oneOf": [
              { "format":"hostname" },
              { "format": "ip4" }
            ]
          }
        }
      }"""
    doTest(schema, """{"withFormat": <warning descr="Validates to more than one variant">"localhost"</warning>}""")
  }

  override fun testOneOf1() {
    // networknt correctly detects oneOf multi-match: "parts" matches both partOne and partTwo branches
    val schemaText = FileUtil.loadFile(File(testDataPath + "/oneOfSchema.json"))
    val inputText = FileUtil.loadFile(File(testDataPath + "/networknt/oneOf1.json"))
    doTest(schemaText, inputText)
  }

  override fun testAllOfProperties() {
    // networknt is spec-correct: additionalProperties:false doesn't "see through" allOf branches
    val schema = """{"allOf": [{"type": "object", "properties": {"first": {}}}, {"properties": {"second": {"enum": [33,44]}}}], "additionalProperties": false}"""
    doTest(schema, """{<warning descr="Property 'first' is not allowed">"first"</warning>: {}, <warning descr="Property 'second' is not allowed">"second"</warning>: <warning descr="Value should be one of: 33, 44">null</warning>}""")
    doTest(schema, """{<warning descr="Property 'first' is not allowed">"first"</warning>: {}, <warning descr="Property 'second' is not allowed">"second"</warning>: 44, <warning descr="Property 'other' is not allowed">"other"</warning>: 15}""")
    doTest(schema, """{<warning descr="Property 'first' is not allowed">"first"</warning>: {}, <warning descr="Property 'second' is not allowed">"second"</warning>: <warning descr="Value should be one of: 33, 44">12</warning>}""")
  }

  override fun testAnyOneTypeSelection() {
    // networknt is spec-correct: additionalProperties:false at top level doesn't see anyOf branch properties
    val schemaText = FileUtil.loadFile(File(testDataPath + "/anyOneTypeSelectionSchema.json"))
    val inputText = FileUtil.loadFile(File(testDataPath + "/networknt/anyOneTypeSelection.json"))
    doTest(schemaText, inputText)
  }

  override fun testComplicatedConditions() {
    // networknt is spec-correct: subField's additionalProperties:false flags "name" properties
    val schemaText = FileUtil.loadFile(File(testDataPath + "/complicatedConditions_schema.json"))
    val inputText = FileUtil.loadFile(File(testDataPath + "/networknt/complicatedConditions.json"))
    doTest(schemaText, inputText)
  }

  override fun testMissingMultipleAltPropertySets() {
    // networknt reports type mismatch (string vs object) from Avro's union type, instead of IntelliJ's property set summary.
    // The leaked branch error comes through mapTypeError; per ADR-001 Variant 3 it must use the
    // composition form ("Required one of") even for a single collected type — so a $ref-resolved
    // branch type rejection reads as a composition mismatch rather than a plain `type:` failure.
    val schemaText = FileUtil.loadFile(File(testDataPath + "/avroSchema.json"))
    doTest(schemaText, """
      <warning descr="Incompatible types.
 Required one of: string. Actual: object.">{
       ${' '}
      }</warning>""")
  }

  override fun testCaseInsensitive() {
    // networknt doesn't support x-intellij-case-insensitive — all non-exact matches are flagged
    doTest("""
           {
             "${'$'}schema": "http://json-schema.org/draft-07/schema#",
             "additionalProperties": {
               "x-intellij-case-insensitive": true,
               "enum": ["aa", "bb"]
             }
           }""",
           """{"q": <warning descr="Value should be one of: \"aa\", \"bb\"">"aA"</warning>, "r": <warning descr="Value should be one of: \"aa\", \"bb\"">"Bb"</warning>, "s": <warning descr="Value should be one of: \"aa\", \"bb\"">"aB"</warning>}""")
  }

  override fun testOneOf() {
    // ADR-001 Variant 3: networknt collects types from all oneOf branches and reports merged message
    // TODO: target is Variant 1 (ADR-001) — restore old IJ format post-MVP
    val subSchemas = listOf("{\"type\": \"string\"}", "{\"type\": \"boolean\"}")
    val schema = schema("{\"oneOf\": [${subSchemas.joinToString(", ")}]}")
    doTest(schema, "{\"prop\": \"abc\"}")
    doTest(schema, "{\"prop\": true}")
    doTest(schema, "{\"prop\": <warning descr=\"Incompatible types.\n Required one of: boolean, string. Actual: integer.\">11</warning>}")
  }

  override fun testOneOfMultipleBranches() {
    // networknt picks the first branch (string) and reports a single-type error instead of a combined "one of" error
    // networknt uses a single space before "Required:" in multi-line descriptions, unlike IntelliJ's indentation-based format
    doTest("""
             {
             	"${'$'}schema": "http://json-schema.org/draft-04/schema#",

             	"type": "object",
             	"oneOf": [
             		{
             			"properties": {
             				"startTime": {
             					"type": "string"
             				}
             			}
             		},
             		{
             			"properties": {
             				"startTime": {
             					"type": "number"
             				}
             			}
             		}
             	]
             }""", """
             {
               "startTime": <warning descr="Incompatible types.
 Required: string. Actual: null.">null</warning>
             }""")
  }

  override fun testOneOfBestChoiceSchema() {
    // networknt picks the company branch (enum mismatch on type value) instead of the person branch (missing required props)
    val schemaText = FileUtil.loadFile(File(testDataPath + "/oneOfBestChoiceSchema.json"))
    val inputText = FileUtil.loadFile(File(testDataPath + "/networknt/oneOfBestChoice.json"))
    doTest(schemaText, inputText)
  }

  override fun testIntersectingHighlightingRanges() {
    // networknt merges type errors from the three oneOf branches that fail the type check at the root
    // (primitiveType + customTypeReference are `string`; avroUnion is `array`). The other branches
    // (avroRecord/Enum/Array/Map/Fixed) have `type: object` which matches the input, so they emit
    // required/enum errors that are suppressed in favour of the structural type merge.
    val schemaText = FileUtil.loadFile(File(testDataPath + "/avroSchema.json"))
    doTest(schemaText, """
      {
        <warning descr="Incompatible types.
 Required one of: array, string. Actual: object.">"type": "array"</warning>
      }""")
    doTest(schemaText, """
      {
        <warning descr="Incompatible types.
 Required one of: array, string. Actual: object.">"type": "array2"</warning>
      }""")
  }

  override fun testProhibitAdditionalPropsAlternateBranches() {
    // networknt selects "second" branch for discriminator:"first" case, reporting multiple errors including
    // a top-level "Does not match any of the allowed schemas" and inner property violations.
    // For discriminator:"second" with extra "first" property, networknt also flags the discriminator value
    // itself (enum mismatch "second" not in ["first"]) and "second" property as not allowed.
    val schemaText = FileUtil.loadFile(File(testDataPath + "/prohibitedAlternateBranchesSchema.json"))
    val input1 = FileUtil.loadFile(File(testDataPath + "/networknt/prohibitedAlternateBranches1.json"))
    doTest(schemaText, input1)
    doTest(schemaText, """
      {
        "subject": {
          "discriminator": <warning descr="Value should be one of: \"first\"">"second"</warning>,
          <warning descr="Property 'first' is not allowed">"first"</warning>: false,
          <warning descr="Property 'second' is not allowed">"second"</warning>: false
        }
      }""")
    doTest(schemaText, """
      {
        "subject": {
          "discriminator": "second",
          "second": false
        }
      }""")
    doTest(schemaText, """
      {
        "subject": {
          "discriminator": "first",
          "first": false
        }
      }""")
  }

  override fun testAnyOnePropertySelection() {
    // networknt reports "Validates to more than one variant" at the object level (anyOf multi-match)
    // instead of "Property 'ccc' is not allowed" at the property level
    val schemaText = FileUtil.loadFile(File(testDataPath + "/anyOnePropertySelectionSchema.json"))
    val inputText = FileUtil.loadFile(File(testDataPath + "/networknt/anyOnePropertySelection.json"))
    doTest(schemaText, inputText)
  }

  // === Override for ECMA-262 regex: Joni handles ^[]$ correctly (Java regex can't) ===

  override fun testPropertyValueAlsoHighlightedIfPatternIsInvalid() {
    // With Joni (ECMA-262 compliant), ^[]$ is a valid pattern (empty character class matching nothing).
    // networknt reports "String violates the pattern" instead of old IJ's "Cannot check the string by pattern
    // because of an error: Unclosed character class" which was based on java.util.regex failure.
    @Suppress("JsonSchemaCompliance")
    val schema = """
      {
        "properties": {
          "withPattern": {
            "pattern": "^[]$"
          }
        }
      }"""
    doTest(schema, """{"withPattern": <warning descr="String violates the pattern: '^[]$'">"(124)555-4216"</warning>}""")
  }

  // === Overrides for "#/" $ref tests (RFC 6901 compliance) ===

  override fun testWithRootRefCycledSchema() {
    // networknt treats "$ref": "#/" as a JSON Pointer to the empty-string key "" (RFC 6901 section 5),
    // NOT as root. Since no such key exists, the $ref is unresolvable — and per json-schema-spec#1276,
    // unresolvable $ref errors are silently filtered (result is indeterminate, not invalid).
    // Without the resolved allOf branch, "testProp" is flagged by additionalProperties:false at root.
    // The old IJ validator resolved "#/" as root ("#"), so "testProp" matched and wasn't flagged.
    val schemaText = FileUtil.loadFile(File(testDataPath + "/cycledWithRootRefSchema.json"))
    val inputText = FileUtil.loadFile(File(testDataPath + "/networknt/testCycledWithRootRefSchema.json"))
    doTest(schemaText, inputText)
  }

  override fun testCycledWithRootRefInNotSchema() {
    // networknt treats "$ref": "#/" as a JSON Pointer to the empty-string key "" (RFC 6901 section 5),
    // NOT as root. Since no such key exists, the $ref is unresolvable — and per json-schema-spec#1276,
    // unresolvable $ref errors are silently filtered. Without the resolved anyOf branch,
    // "testProp" is flagged by additionalProperties:false at root. The nested "alala" inside the
    // unresolvable branch is not flagged (unlike the old IJ validator which resolved "#/" as root).
    val schemaText = FileUtil.loadFile(File(testDataPath + "/cycledWithRootRefInNotSchema.json"))
    val inputText = FileUtil.loadFile(File(testDataPath + "/networknt/testCycledWithRootRefInNotSchema.json"))
    doTest(schemaText, inputText)
  }

  override fun testCycledSchema() {
    // networknt ignores "additionalProperties": "false" (string value, not boolean) — it does not treat
    // the string as a disabling schema. As a result, "s" (not in properties) is NOT flagged.
    // The old IJ validator also ignored the string value, so both produce no additionalProperties error.
    // networknt does still report the type mismatch on "ccc".
    val schemaText = FileUtil.loadFile(File(testDataPath + "/cycledSchema.json"))
    doTest(schemaText, """
{
  "aaa": 1,
  "bbb": true,
  "s": 1,
  "ccc": <warning descr="Incompatible types.
 Required: integer. Actual: string.">"3"</warning>
}
""")
  }

  // === Override for B2: PSI-to-JsonNode limitation on incomplete input ===

  override fun testOneOfWithEmptyPropertyValue() {
    // networknt reports a false-positive missing-required-property error because PsiToJsonNodeConverter
    // drops the incomplete "type": property (no value due to syntax error), so Jackson sees {"building":"1"}.
    //
    // The result is non-deterministic: networknt validates both oneOf branches in parallel and the first
    // required-error to call putIfAbsent on the client_address PSI element wins:
    //   - office_address branch (required: ["type","building"]) → building is present, emits "type" missing
    //   - home_address  branch (required: ["type","street_address","city","state"]) → may emit any of its
    //     missing properties first, depending on networknt's internal iteration order
    // Both outcomes are equally valid false-positives caused by the PSI-to-JsonNode limitation (B2).
    val schemaText = FileUtil.loadFile(File(testDataPath + "/oneOfSchema.json"))
    val instanceText = """
{
  "client_address": <warning descr="PLACEHOLDER">{
    "building": "1",
    "type":<EOLError descr="<literal>, IDENTIFIER, '[' or '{' expected, got '}'"></EOLError>
  }</warning>
}"""
    val acceptableProperties = setOf("type", "street_address", "city", "state")
    for (missingProp in acceptableProperties) {
      try {
        doTest(schemaText, instanceText.replace("PLACEHOLDER", "Missing required property '$missingProp'"))
        return
      }
      catch (_: AssertionError) {
        // try next candidate
      }
    }
    fail("Expected a 'Missing required property' warning for one of $acceptableProperties but none matched")
  }

  // === Override for B3: Jackson duplicate-key dedup (last-wins) ===

  override fun testNumberOfSameNamedPropertiesCorrectlyChecked() {
    // networknt only flags the LAST occurrence of duplicate keys because Jackson's ObjectNode deduplicates
    // keys with last-wins semantics. The first "a": 1 is invisible to networknt; only "a": 5 is validated.
    // Similarly, maxProperties sees only 3 unique keys (a, b, c) instead of 4, so the maxProperties:3
    // violation is not reported.
    val schema = """
      {
        "properties": {
          "size": {
            "type": "object",
            "minProperties": 2,
            "maxProperties": 3,
            "properties": {
              "a": {
                "type": "boolean"
              }
            }
          }
        }
      }"""
    doTest(schema, """
      {
        "size": {
          "a": 1, "b":3, "c": 4, "a": <warning descr="Incompatible types.
 Required: boolean. Actual: integer.">5</warning>
        }
      }""")
    doTest(schema, """
      {
        "size": {
          "a": true, "b":3, "c": 4, "a": false
        }
      }""")
  }

  // broken $ref tests: networknt now filters unresolvable $ref errors (json-schema-spec#1276),
  // so behavior matches the old IJ validator — no overrides needed.
}
