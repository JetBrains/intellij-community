// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.networknt.wrapper

import com.intellij.json.JsonFileType
import com.intellij.json.psi.JsonFile
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker
import com.jetbrains.jsonSchema.impl.JsonValidationError
import com.jetbrains.jsonSchema.impl.JsonValidationError.FixableIssueKind
import com.networknt.schema.SpecificationVersion
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language

/**
 * Integration tests for [NetworkntErrorMapper].
 *
 * Each test: schema + instance → networknt validate → ErrorMapper.mapErrors → assert FixableIssueKind + IssueData.
 */
class NetworkntErrorMapperTest : BasePlatformTestCase() {

  private fun validateAndMap(
    @Language("JSON") schema: String,
    @Language("JSON") instance: String,
  ): Map<PsiElement, JsonValidationError> {
    val psiFile = myFixture.configureByText(JsonFileType.INSTANCE, instance) as JsonFile
    val rootElement = psiFile.topLevelValue!!
    val walker = JsonLikePsiWalker.getWalker(rootElement)!!
    val locationIndex = PsiLocationIndex.build(walker, rootElement)

    val validator = NetworkntSchemaValidator(SpecificationVersion.DRAFT_2020_12)
    val errors = validator.validate(schema, instance)

    val mapper = NetworkntErrorMapper(locationIndex)
    return mapper.mapErrors(errors)
  }

  fun `test valid instance produces no errors`() {
    val schema = """{"type": "object", "properties": {"name": {"type": "string"}}}"""
    val mapped = validateAndMap(schema, """{"name": "Alice"}""")
    assertThat(mapped).isEmpty()
  }

  fun `test type mismatch produces ProhibitedType`() {
    val schema = """{"type": "object", "properties": {"value": {"type": "string"}}}"""
    val mapped = validateAndMap(schema, """{"value": 123}""")

    assertThat(mapped).isNotEmpty
    val error = mapped.values.first()
    assertThat(error.fixableIssueKind).isEqualTo(FixableIssueKind.ProhibitedType)
    assertThat(error.issueData).isInstanceOf(JsonValidationError.TypeMismatchIssueData::class.java)
  }

  fun `test required property produces MissingProperty`() {
    val schema = """{"type": "object", "properties": {"name": {"type": "string"}}, "required": ["name"]}"""
    val mapped = validateAndMap(schema, """{}""")

    assertThat(mapped).isNotEmpty
    val error = mapped.values.first()
    assertThat(error.fixableIssueKind).isEqualTo(FixableIssueKind.MissingProperty)
    assertThat(error.issueData).isInstanceOf(JsonValidationError.MissingMultiplePropsIssueData::class.java)
  }

  fun `test additional property produces ProhibitedProperty`() {
    val schema = """{"type": "object", "properties": {"name": {"type": "string"}}, "additionalProperties": false}"""
    val mapped = validateAndMap(schema, """{"name": "Alice", "extra": 1}""")

    assertThat(mapped).isNotEmpty
    val error = mapped.values.first()
    assertThat(error.fixableIssueKind).isEqualTo(FixableIssueKind.ProhibitedProperty)
    assertThat(error.issueData).isInstanceOf(JsonValidationError.ProhibitedPropertyIssueData::class.java)
  }

  fun `test enum violation produces NonEnumValue`() {
    val schema = """{"type": "object", "properties": {"status": {"enum": ["active", "inactive"]}}}"""
    val mapped = validateAndMap(schema, """{"status": "unknown"}""")

    assertThat(mapped).isNotEmpty
    val error = mapped.values.first()
    assertThat(error.fixableIssueKind).isEqualTo(FixableIssueKind.NonEnumValue)
  }

  fun `test const violation`() {
    val schema = """{"type": "object", "properties": {"version": {"const": "1.0"}}}"""
    val mapped = validateAndMap(schema, """{"version": "2.0"}""")

    assertThat(mapped).isNotEmpty
    val error = mapped.values.first()
    assertThat(error.fixableIssueKind).isEqualTo(FixableIssueKind.NonEnumValue)
  }

  fun `test pattern violation`() {
    val schema = """{"type": "object", "properties": {"code": {"type": "string", "pattern": "^\\d+$"}}}"""
    val mapped = validateAndMap(schema, """{"code": "abc"}""")

    assertThat(mapped).isNotEmpty
    val error = mapped.values.first()
    assertThat(error.fixableIssueKind).isEqualTo(FixableIssueKind.None)
    assertThat(error.message).contains("\\d+")
  }

  fun `test minimum violation`() {
    val schema = """{"type": "object", "properties": {"age": {"type": "integer", "minimum": 18}}}"""
    val mapped = validateAndMap(schema, """{"age": 5}""")

    assertThat(mapped).isNotEmpty
    val error = mapped.values.first()
    assertThat(error.fixableIssueKind).isEqualTo(FixableIssueKind.None)
    assertThat(error.message).contains("18")
  }

  fun `test maxLength violation`() {
    val schema = """{"type": "object", "properties": {"name": {"type": "string", "maxLength": 3}}}"""
    val mapped = validateAndMap(schema, """{"name": "abcdef"}""")

    assertThat(mapped).isNotEmpty
    val error = mapped.values.first()
    assertThat(error.fixableIssueKind).isEqualTo(FixableIssueKind.None)
    assertThat(error.message).contains("3")
  }

  fun `test minItems violation`() {
    val schema = """{"type": "object", "properties": {"items": {"type": "array", "minItems": 2}}}"""
    val mapped = validateAndMap(schema, """{"items": [1]}""")

    assertThat(mapped).isNotEmpty
    val error = mapped.values.first()
    assertThat(error.fixableIssueKind).isEqualTo(FixableIssueKind.None)
    assertThat(error.message).contains("2")
  }

  fun `test uniqueItems violation produces DuplicateArrayItem`() {
    val schema = """{"type": "object", "properties": {"tags": {"type": "array", "uniqueItems": true}}}"""
    val mapped = validateAndMap(schema, """{"tags": ["a", "b", "a"]}""")

    assertThat(mapped).isNotEmpty
    val error = mapped.values.first()
    assertThat(error.fixableIssueKind).isEqualTo(FixableIssueKind.DuplicateArrayItem)
    assertThat(error.issueData).isInstanceOf(JsonValidationError.DuplicateArrayItemIssueData::class.java)
  }

  fun `test not violation`() {
    val schema = """{"type": "object", "properties": {"value": {"not": {"type": "string"}}}}"""
    val mapped = validateAndMap(schema, """{"value": "hello"}""")

    assertThat(mapped).isNotEmpty
    val error = mapped.values.first()
    assertThat(error.fixableIssueKind).isEqualTo(FixableIssueKind.None)
  }

  fun `test multiple errors in one instance`() {
    val schema = """
      {
        "type": "object",
        "properties": {
          "name": {"type": "string"},
          "age": {"type": "integer"}
        },
        "required": ["name"]
      }
    """.trimIndent()
    val mapped = validateAndMap(schema, """{"age": "not a number"}""")

    // Should have at least one error (required for "name" and type for "age")
    assertThat(mapped).isNotEmpty
    assertThat(mapped.size).isGreaterThanOrEqualTo(1)
  }

  fun `test union type mismatch`() {
    val schema = """{"type": "object", "properties": {"value": {"type": ["string", "number"]}}}"""
    val mapped = validateAndMap(schema, """{"value": true}""")

    assertThat(mapped).isNotEmpty
    val error = mapped.values.first()
    assertThat(error.fixableIssueKind).isEqualTo(FixableIssueKind.ProhibitedType)
  }

  fun `test top-level type mismatch`() {
    val schema = """{"type": "object"}"""
    val mapped = validateAndMap(schema, """[1, 2, 3]""")

    assertThat(mapped).isNotEmpty
    val error = mapped.values.first()
    assertThat(error.fixableIssueKind).isEqualTo(FixableIssueKind.ProhibitedType)
  }

  fun `test minProperties violation`() {
    val schema = """{"type": "object", "minProperties": 2}"""
    val mapped = validateAndMap(schema, """{"a": 1}""")

    assertThat(mapped).isNotEmpty
    val error = mapped.values.first()
    assertThat(error.fixableIssueKind).isEqualTo(FixableIssueKind.None)
    assertThat(error.message).contains("2")
  }

  fun `test oneOf no-match produces an error`() {
    // Child type errors from individual branches map successfully via the child-error path.
    // The important invariant is that we always get at least one error, never null.
    val schema = """{"oneOf": [{"type": "string"}, {"type": "integer"}]}"""
    val mapped = validateAndMap(schema, """true""")

    assertThat(mapped).isNotEmpty
    val error = mapped.values.first()
    assertThat(error.message).isNotBlank
  }

  fun `test anyOf no-match produces an error`() {
    val schema = """{"anyOf": [{"type": "string"}, {"type": "integer", "minimum": 10}]}"""
    val mapped = validateAndMap(schema, """false""")

    assertThat(mapped).isNotEmpty
    val error = mapped.values.first()
    assertThat(error.message).isNotBlank
  }

  fun `test oneOf multi-match produces to-more-than-one error`() {
    // Schema: value may match branch 1 (minimum: 1) or branch 2 (maximum: 100) — 50 satisfies both
    val schema = """{"oneOf": [{"type": "integer", "minimum": 1}, {"type": "integer", "maximum": 100}]}"""
    val mapped = validateAndMap(schema, """50""")

    assertThat(mapped).isNotEmpty
    val error = mapped.values.first()
    assertThat(error.fixableIssueKind).isEqualTo(FixableIssueKind.None)
    assertThat(error.message).isNotBlank
  }

  fun `test allOf required groups are merged into a single error`() {
    // Two allOf branches each declare a single required property. networknt emits two
    // separate required errors with different evaluationPaths — we must surface both.
    val schema = """{"allOf": [{"required": ["a"]}, {"required": ["b"]}]}"""
    val mapped = validateAndMap(schema, """{}""")

    assertThat(mapped).hasSize(1)
    val error = mapped.values.first()
    assertThat(error.fixableIssueKind).isEqualTo(FixableIssueKind.MissingProperty)
    val issueData = error.issueData as JsonValidationError.MissingMultiplePropsIssueData
    val missingNames = issueData.myMissingPropertyIssues.map { it.propertyName }.toSet()
    assertThat(missingNames).isEqualTo(setOf("a", "b"))
  }

  fun `test oneOf does not mix anyOf branch types`() {
    // Sibling compositions at the same instance node. Both fail for a boolean.
    // The oneOf-derived error must only reference string/number, NOT array/object from anyOf.
    val schema = """{"allOf": [{"oneOf": [{"type": "string"}, {"type": "number"}]}, {"anyOf": [{"type": "array"}, {"type": "object"}]}]}"""
    val mapped = validateAndMap(schema, """true""")

    assertThat(mapped).isNotEmpty
    val typeErrors = mapped.values.filter { it.issueData is JsonValidationError.TypeMismatchIssueData }
    val oneOfError = typeErrors.firstOrNull { error ->
      val data = error.issueData as JsonValidationError.TypeMismatchIssueData
      data.expectedTypes.any { it.description == "string" }
    }
    assertThat(oneOfError).isNotNull
    val data = oneOfError!!.issueData as JsonValidationError.TypeMismatchIssueData
    val expected = data.expectedTypes.map { it.description }.toSet()
    assertThat(expected).isEqualTo(setOf("string", "number"))
    assertThat(expected).doesNotContain("array", "object")
  }

  fun `test extractCompositionRoot returns null when no oneOf or anyOf segment present`() {
    assertThat(extractCompositionRoot("/properties/foo/type")).isNull()
    assertThat(extractCompositionRoot("")).isNull()
    assertThat(extractCompositionRoot(null)).isNull()
  }

  fun `test extractCompositionRoot returns path up to oneOf or anyOf segment`() {
    assertThat(extractCompositionRoot("/properties/script/oneOf/0/type"))
      .isEqualTo("/properties/script/oneOf")
    assertThat(extractCompositionRoot("/properties/script/oneOf/1/items/anyOf/0/type"))
      .isEqualTo("/properties/script/oneOf/1/items/anyOf")
    assertThat(extractCompositionRoot("/anyOf/2/properties/foo/type"))
      .isEqualTo("/anyOf")
  }

  fun `test extractBranchIndex returns integer segment after composition root`() {
    assertThat(extractBranchIndex("/properties/script/oneOf/0/type", "/properties/script/oneOf"))
      .isEqualTo(0)
    assertThat(extractBranchIndex("/properties/script/oneOf/1/items/anyOf/0/type", "/properties/script/oneOf"))
      .isEqualTo(1)
    assertThat(extractBranchIndex("/properties/script/oneOf/items", "/properties/script/oneOf"))
      .isNull()
    assertThat(extractBranchIndex(null, "/properties/script/oneOf")).isNull()
  }

  fun `test oneOf branch type error suppressed when other branch matches value type`() {
    // Value at /script is an array [{}].
    // Branch 0 wants string → emits type error at /script (expected string, actual array).
    // Branch 1 wants array → type check passes, fails deeper on items → emits error at /script/0.
    // Expected: branch 0 type error is suppressed; only the deeper error surfaces.
    @Language("JSON")
    val schema = """
      {
        "properties": {
          "script": {
            "oneOf": [
              {"type": "string", "minLength": 1},
              {"type": "array", "items": {"anyOf": [{"type": "string"}, {"type": "array", "items": {"type": "string"}}]}, "minItems": 1}
            ]
          }
        }
      }
    """.trimIndent()
    @Language("JSON")
    val instance = """{"script": [{}]}"""

    val mapped = validateAndMap(schema, instance)

    // No error should be anchored on the outer /script array saying "Required: string. Actual: array.".
    val outerStringTypeError = mapped.values.any { err ->
      err.message.contains("Required: string") && err.message.contains("Actual: array")
    }
    assertThat(outerStringTypeError)
      .withFailMessage("Branch-0 type error on outer oneOf should be suppressed; got messages: ${mapped.values.map { it.message }}")
      .isFalse()

    // And no redundant "Does not match any of the allowed schemas" fallback at the outer level —
    // the deeper error from branch 1 is enough for the user.
    val hasDoesNotMatchFallback = mapped.values.any { err ->
      err.message.contains("Does not match any")
    }
    assertThat(hasDoesNotMatchFallback)
      .withFailMessage("Outer oneOf 'Does not match any' fallback should be suppressed; got: ${mapped.values.map { it.message }}")
      .isFalse()
  }

  fun `test oneOf both branches type-incompatible keeps errors for Variant-3 merge`() {
    // Value is a number. Both branches want string/array. Neither branch is type-compatible.
    // Both branch errors must survive so that the existing Variant-3 merge logic (or the
    // synthesised merge from Task 3) can produce "Required one of: array, string".
    @Language("JSON")
    val schema = """
      {
        "properties": {
          "value": {
            "oneOf": [
              {"type": "string"},
              {"type": "array"}
            ]
          }
        }
      }
    """.trimIndent()
    @Language("JSON")
    val instance = """{"value": 42}"""

    val mapped = validateAndMap(schema, instance)

    assertThat(mapped).isNotEmpty
    val message = mapped.values.first().message
    assertThat(message).contains("Required one of")
    assertThat(message).contains("string")
    assertThat(message).contains("array")
  }

  fun `test oneOf branches collapsing to one type still print 'Required one of'`() {
    // Both oneOf branches expect 'object'; instance is an array. After distinct() the
    // expected-type set is a singleton {object}, but the message must STILL use the
    // composition form ("Required one of: object") per ADR-001 Variant 3 — a oneOf/anyOf
    // mismatch must never be reported as if it were a plain single-type 'type:' error.
    @Language("JSON")
    val schema = """
      {
        "properties": {
          "value": {
            "oneOf": [
              {"type": "object", "properties": {"a": {"type": "string"}}},
              {"type": "object", "properties": {"b": {"type": "integer"}}}
            ]
          }
        }
      }
    """.trimIndent()
    @Language("JSON")
    val instance = """{"value": []}"""

    val mapped = validateAndMap(schema, instance)

    assertThat(mapped).isNotEmpty
    val message = mapped.values.first().message
    assertThat(message).contains("Required one of: object")
    assertThat(message).contains("Actual: array")
  }

  fun `test grpc-style oneOf with ref and explicit object branch against array`() {
    // Reproduces the schema produced by GrpcJsonSchemaBuilder for a message-typed proto field.
    // The first branch is a ${'$'}ref to an InnerRequest definition (which has type: object);
    // the second branch is an explicit {type: object}. Both expect 'object'. Instance is array.
    // After the .distinct() collapse the expected type set is {object}, but the message must
    // still use the composition form: "Required one of: object".
    @Language("JSON")
    val schema = """
      {
        "type": "object",
        "properties": {
          "innerRequest": {
            "oneOf": [
              { "${'$'}ref": "#/definitions/Inner" },
              { "type": "object" }
            ]
          }
        },
        "definitions": {
          "Inner": { "type": "object", "additionalProperties": true }
        }
      }
    """.trimIndent()
    @Language("JSON")
    val instance = """{ "innerRequest": [] }"""

    val mapped = validateAndMap(schema, instance)

    assertThat(mapped).isNotEmpty
    val message = mapped.values.first().message
    assertThat(message)
      .withFailMessage("Expected 'Required one of: object' (composition form), got: $message")
      .contains("Required one of: object")
    assertThat(message).contains("Actual: array")
  }

  fun `test grpc-style nested map-of-map with ref leaf binds type error to deepest property value`() {
    // Reproduces GrpcRequestBodySchemaTest.test validate map field value type:
    //   map<string, MainMessage>     mapField       → additionalProperties: {$ref: MainMessage}
    //   map<string, SecondaryMessage> secondMapField → additionalProperties: {$ref: SecondaryMessage}
    //   message SecondaryMessage { string secondaryValue = 1; }
    //
    // We already know networknt itself emits a type error at instanceLocation
    //   /mapField/aaa/secondMapField/bbb/secondaryValue
    // (see NetworkntSchemaValidatorTest's grpc-style nested map-of-map repro). The question
    // here is whether NetworkntErrorMapper preserves that — i.e. PsiLocationIndex resolves
    // user-defined map keys (`aaa`, `bbb`) and the mapper produces a TypeMismatch entry.
    @Language("JSON")
    val schema = """
      {
        "type": "object",
        "properties": {
          "mapField": { "${'$'}ref": "#/definitions/mapField" }
        },
        "definitions": {
          "MainMessage": {
            "type": "object",
            "additionalProperties": true,
            "properties": {
              "secondMapField": { "${'$'}ref": "#/definitions/secondMapField" }
            }
          },
          "SecondaryMessage": {
            "type": "object",
            "additionalProperties": true,
            "properties": {
              "secondaryValue": { "type": "string" }
            }
          },
          "mapField": {
            "type": "object",
            "additionalProperties": { "${'$'}ref": "#/definitions/MainMessage" }
          },
          "secondMapField": {
            "type": "object",
            "additionalProperties": { "${'$'}ref": "#/definitions/SecondaryMessage" }
          }
        }
      }
    """.trimIndent()
    @Language("JSON")
    val instance = """
      {
        "mapField": {
          "aaa": {
            "secondMapField": {
              "bbb": {
                "secondaryValue": 123
              }
            }
          }
        }
      }
    """.trimIndent()

    val mapped = validateAndMap(schema, instance)

    val typeMismatchEntries = mapped.entries.filter {
      it.value.fixableIssueKind == JsonValidationError.FixableIssueKind.ProhibitedType
    }
    assertThat(typeMismatchEntries)
      .withFailMessage("Expected a TypeMismatch entry for the deeply-nested string violation; mapped = ${mapped.values.map { it.message }}")
      .isNotEmpty
    val message = typeMismatchEntries.first().value.message
    assertThat(message).contains("string")
    assertThat(message).contains("integer")
  }

  fun `test nested anyOf enum branches roll up into a single union`() {
    // Outer anyOf[0] is itself an anyOf of two enum leaves; outer anyOf[1] is a single enum.
    // Expected: one NonEnumValue warning at /prop with the union of all branch enums
    // (a, b, c, d, e, f) — matching the legacy engine's flattening of nested anyOf-of-enums.
    @Language("JSON")
    val schema = """
      {
        "properties": {
          "prop": {
            "anyOf": [
              {"anyOf": [{"enum": ["a", "b"]}, {"enum": ["c", "d"]}]},
              {"enum": ["e", "f"]}
            ]
          }
        }
      }
    """.trimIndent()
    @Language("JSON")
    val instance = """{"prop": "z"}"""

    val mapped = validateAndMap(schema, instance)

    assertThat(mapped).isNotEmpty
    val enumErrors = mapped.values.filter { it.fixableIssueKind == FixableIssueKind.NonEnumValue }
    assertThat(enumErrors)
      .withFailMessage("Expected at least one NonEnumValue error; got: ${mapped.values.map { it.message }}")
      .isNotEmpty

    val unionMessages = enumErrors.filter { err ->
      val m = err.message
      listOf("\"a\"", "\"b\"", "\"c\"", "\"d\"", "\"e\"", "\"f\"").all { m.contains(it) }
    }
    assertThat(unionMessages)
      .withFailMessage("Expected exactly one merged enum union across nested anyOf; got: ${mapped.values.map { it.message }}")
      .hasSize(1)
  }

  fun `test anyOf branch type errors merge into Variant-3 even without composition error`() {
    // Nested: outer oneOf(string | array), inner anyOf(string | array-of-strings) on items.
    // Value is {script: [{}]}. Inner anyOf sees object at /script/0 vs string/array → both
    // branches emit type errors. Networknt (at this nesting depth) may NOT emit a
    // keyword=anyOf sibling — we must still merge the two type errors into one Variant-3
    // message at /script/0.
    @Language("JSON")
    val schema = """
    {
      "properties": {
        "script": {
          "oneOf": [
            {"type": "string"},
            {"type": "array", "items": {"anyOf": [{"type": "string"}, {"type": "array"}]}}
          ]
        }
      }
    }
    """.trimIndent()
    @Language("JSON")
    val instance = """{"script": [{}]}"""

    val mapped = validateAndMap(schema, instance)

    // Exactly one error at the inner location, with the merged message.
    val messagesAtItem: List<String> = mapped.values.map { it.message.toString() }
    val mergedCount = messagesAtItem.count { msg ->
      msg.contains("Required one of") &&
      msg.contains("array") &&
      msg.contains("string") &&
      msg.contains("Actual: object")
    }
    assertThat(mergedCount)
      .withFailMessage("Expected exactly one merged Variant-3 warning; got messages: $messagesAtItem")
      .isEqualTo(1)

    // And no individual "Required: string. Actual: object." should remain at the same location.
    val individualCount = messagesAtItem.count { msg ->
      msg.contains("Required: string") && msg.contains("Actual: object")
    }
    assertThat(individualCount)
      .withFailMessage("Individual 'Required: string' error should have been replaced by the merge; got: $messagesAtItem")
      .isEqualTo(0)
  }

  fun `test nested oneOf prefers value-scoped branch error over sibling structural required`() {
    // Schema has a top-level oneOf with two branches:
    //   branch 0: matches when "type" matches /(good)|(ok)/
    //   branch 1: matches when "type" matches /^(fine)/ AND "extra" is present
    // Instance {"type": "doog"} fails both branches. Legacy IJ validator surfaces the
    // pattern mismatch at "doog" (value-scoped) and suppresses the "Missing required
    // property 'extra'" at the root object — because adding 'extra' alone does NOT make
    // the schema valid (doog doesn't match ^fine either), so the structural complaint
    // is misleading UX.
    @Language("JSON")
    val schema = """
      {"type":"object",
        "oneOf": [
          {
            "properties": {
              "type": {
                "type": "string",
                "oneOf": [
                  { "pattern": "(good)" },
                  { "pattern": "(ok)" }
                ]
              }
            }
          },
          {
            "properties": {
              "type": { "type": "string", "pattern": "^(fine)" },
              "extra": { "type": "string" }
            },
            "required": ["type", "extra"]
          }
        ]}
    """.trimIndent()
    @Language("JSON")
    val instance = """{"type": "doog"}"""

    val mapped = validateAndMap(schema, instance)

    // No structural "Missing required property" error anywhere — adding 'extra' would
    // not actually resolve the schema failure, so the legacy UX prefers the value-scoped
    // pattern/enum-style error from the sibling branch.
    val missingPropErrors = mapped.values.filter { it.fixableIssueKind == FixableIssueKind.MissingProperty }
    assertThat(missingPropErrors)
      .withFailMessage("Structural MissingProperty error from branch 1 should be suppressed; got: ${mapped.values.map { it.message }}")
      .isEmpty()

    // And at least one value-scoped (narrow) error should survive — typically the
    // pattern mismatch anchored on the "doog" string.
    assertThat(mapped)
      .withFailMessage("Expected at least one mapped error remaining")
      .isNotEmpty
  }

  fun `test oneOf prefers value-scoped branch error over sibling enum at composition root`() {
    // Mirrors YamlByJsonSchemaHighlightingTest#testWithWaySelection / the JSON-side counterpart.
    // Schema at prop has oneOf of:
    //   branch 0: {enum: [1..5]}  — fails at /prop with "value should be one of: 1..5"
    //   branch 1: {type: array, items: {properties: {kilo:{}}, additionalProperties:false}}
    //             — fails at /prop/0/foxtrot with "property not allowed"
    // Legacy UX surfaces the value-scoped additionalProperties error anchored deep in the
    // offending array element and suppresses the broader enum complaint at /prop — because
    // reporting "should be one of 1..5" on the whole array is misleading when the user
    // clearly meant branch 1 and just has a typo in a property name.
    @Language("JSON")
    val schema = """
      {
        "properties": {
          "prop": {
            "oneOf": [
              {"enum": [1, 2, 3, 4, 5]},
              {"type": "array", "items": {"properties": {"kilo": {}}, "additionalProperties": false}}
            ]
          }
        }
      }
    """.trimIndent()
    @Language("JSON")
    val instance = """{"prop": [{"foxtrot": 15, "kilo": 20}]}"""

    val mapped = validateAndMap(schema, instance)

    // The NonEnumValue error at /prop (the whole array) must be suppressed in favour of the
    // deeper ProhibitedProperty at /prop/0/foxtrot.
    val enumErrors = mapped.values.filter { it.fixableIssueKind == FixableIssueKind.NonEnumValue }
    assertThat(enumErrors)
      .withFailMessage("NonEnumValue error from the enum branch should be suppressed; got: ${mapped.values.map { it.message }}")
      .isEmpty()

    val prohibited = mapped.values.filter { it.fixableIssueKind == FixableIssueKind.ProhibitedProperty }
    assertThat(prohibited)
      .withFailMessage("Expected ProhibitedProperty error for 'foxtrot' to survive; got: ${mapped.values.map { it.message }}")
      .isNotEmpty
  }

  fun `test oneOf prefers value-scoped branch error over sibling additionalProperties at composition root`() {
    // Mirrors swagger 2.0 responseValue = oneOf:[response, jsonReference] where the user typo'd
    // a property value:
    //   branch 0 (response): allows {description, schema, headers, examples}; description: string
    //   branch 1 (jsonReference): {$ref: string} only, additionalProperties:false
    // Instance has {description: 123} — branch 0 surfaces a deeper type error at /resp/description,
    // branch 1 fires "additionalProperty: description is not allowed" at /resp.
    // Legacy UX surfaces ONLY the deeper type error. The composition-root additionalProperties
    // complaint is misleading because the user clearly intended branch 0 with a wrong value type.
    @Language("JSON")
    val schema = """
      {
        "properties": {
          "resp": {
            "oneOf": [
              {"type": "object", "properties": {"description": {"type": "string"}}, "required": ["description"], "additionalProperties": false},
              {"type": "object", "properties": {"${'$'}ref": {"type": "string"}}, "required": ["${'$'}ref"], "additionalProperties": false}
            ]
          }
        }
      }
    """.trimIndent()
    @Language("JSON")
    val instance = """{"resp": {"description": 123}}"""

    val mapped = validateAndMap(schema, instance)

    val prohibited = mapped.values.filter { it.fixableIssueKind == FixableIssueKind.ProhibitedProperty }
    assertThat(prohibited)
      .withFailMessage("Branch jsonReference's additionalProperty rejection of 'description' should be suppressed; got: ${mapped.values.map { it.message }}")
      .isEmpty()

    val typeErrors = mapped.values.filter { it.fixableIssueKind == FixableIssueKind.ProhibitedType }
    assertThat(typeErrors)
      .withFailMessage("Expected the deeper type error on description=123 to survive; got: ${mapped.values.map { it.message }}")
      .isNotEmpty
  }

  fun `test additionalProperties on a different property than the deeper failure is NOT suppressed`() {
    // Guard: only the rejected property that COINCIDES with a sibling-branch deeper failure should
    // be suppressed. An unrelated extra property must remain reported.
    @Language("JSON")
    val schema = """
      {
        "properties": {
          "resp": {
            "oneOf": [
              {"type": "object", "properties": {"description": {"type": "string"}}, "additionalProperties": false},
              {"type": "object", "properties": {"${'$'}ref": {"type": "string"}}, "required": ["${'$'}ref"], "additionalProperties": false}
            ]
          }
        }
      }
    """.trimIndent()
    @Language("JSON")
    val instance = """{"resp": {"description": "ok", "wat": 1}}"""

    val mapped = validateAndMap(schema, instance)

    val watErrors = mapped.values.filter {
      it.fixableIssueKind == FixableIssueKind.ProhibitedProperty && it.message.contains("wat")
    }
    assertThat(watErrors)
      .withFailMessage("Extra property 'wat' must remain reported (no sibling branch failure on it); got: ${mapped.values.map { it.message }}")
      .isNotEmpty
  }

  fun `test suppressed PSI targets drop their networknt errors`() {
    // Adapters that opt out of value-level validation (e.g. JS reference/call expressions)
    // must not contribute errors to the final map. Mark a known PSI target as suppressed and
    // verify the corresponding error is absent, while a sibling unrelated error remains.
    @Language("JSON")
    val schema = """{"type": "object", "properties": {
      "mode": {"enum": ["dev", "prod"]},
      "name": {"type": "string"}
    }}"""
    @Language("JSON")
    val instance = """{"mode": "wrong", "name": 42}"""

    val psiFile = myFixture.configureByText(JsonFileType.INSTANCE, instance) as JsonFile
    val rootElement = psiFile.topLevelValue!!
    val walker = JsonLikePsiWalker.getWalker(rootElement)!!
    val locationIndex = PsiLocationIndex.build(walker, rootElement)

    val validator = NetworkntSchemaValidator(SpecificationVersion.DRAFT_2020_12)
    val errors = validator.validate(schema, instance)

    val baselineMapper = NetworkntErrorMapper(locationIndex)
    val baseline = baselineMapper.mapErrors(errors)
    assertThat(baseline.values.map { it.fixableIssueKind })
      .contains(FixableIssueKind.NonEnumValue, FixableIssueKind.ProhibitedType)

    // Locate the PSI of "wrong" (the value of `mode`) and mark it suppressed.
    val modeValue = (rootElement as com.intellij.json.psi.JsonObject).findProperty("mode")?.value!!
    locationIndex.markSuppressedForTest(modeValue)

    val mapped = NetworkntErrorMapper(locationIndex).mapErrors(errors)
    assertThat(mapped.keys).doesNotContain(modeValue)
    // The unrelated `name: 42` type error must still come through.
    assertThat(mapped.values.map { it.fixableIssueKind }).contains(FixableIssueKind.ProhibitedType)
  }
}
