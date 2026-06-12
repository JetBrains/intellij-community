// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.networknt.wrapper

import com.networknt.schema.InputFormat
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class NetworkntSchemaValidatorTest {
  private val schema = """
    {
      "type": "object",
      "properties": {
        "name": { "type": "string" },
        "age": { "type": "integer" }
      },
      "required": ["name"]
    }
  """.trimIndent()

  @Test
  fun `valid instance produces no errors`() {
    val validator = NetworkntSchemaValidator(SpecificationVersion.DRAFT_2020_12)
    val instance = """{ "name": "Alice", "age": 30 }"""

    val errors = validator.validate(schema, instance)
    assertTrue(errors.isEmpty(), "Valid instance should produce no errors")
  }

  @Test
  fun `invalid instance produces errors`() {
    val validator = NetworkntSchemaValidator(SpecificationVersion.DRAFT_2020_12)
    val instance = """{ "age": "not a number" }"""

    val errors = validator.validate(schema, instance)
    assertFalse(errors.isEmpty(), "Invalid instance should produce errors")
    assertTrue(errors.any { it.keyword == "required" }, "Should have 'required' error for missing 'name'")
    assertTrue(errors.any { it.keyword == "type" }, "Should have 'type' error for 'age' being a string")
  }

  @Test
  fun `Error getSchemaPropertyNames returns property names from schema`() {
    val validator = NetworkntSchemaValidator(SpecificationVersion.DRAFT_2020_12)
    val instance = """{ "unknownField": "value" }"""

    val errors = validator.validate(schema, instance)
    val requiredError = errors.find { it.keyword == "required" }
    assertNotNull(requiredError, "Should have required error")

    val propertyNames = requiredError!!.getSchemaPropertyNames()
    assertTrue(propertyNames.contains("name"), "Property names should include 'name'; got: $propertyNames")
    assertTrue(propertyNames.contains("age"), "Property names should include 'age'; got: $propertyNames")
    assertEquals(2, propertyNames.size, "Should have exactly 2 property names; got: $propertyNames")
  }

  @Test
  fun `Error getExpectedTypes returns single type`() {
    val schemaWithType = """
      {
        "type": "object",
        "properties": {
          "value": { "type": "string" }
        }
      }
    """.trimIndent()

    val validator = NetworkntSchemaValidator(SpecificationVersion.DRAFT_2020_12)
    val instance = """{ "value": 123 }"""

    val errors = validator.validate(schemaWithType, instance)
    val typeError = errors.find { it.keyword == "type" }
    assertNotNull(typeError, "Should have type error")

    val expectedTypes = typeError!!.getExpectedTypes()
    assertEquals(listOf("string"), expectedTypes, "Expected types should be ['string']")
  }

  @Test
  fun `Error getExpectedTypes returns union types`() {
    val schemaWithUnionType = """
      {
        "type": "object",
        "properties": {
          "value": { "type": ["string", "number"] }
        }
      }
    """.trimIndent()

    val validator = NetworkntSchemaValidator(SpecificationVersion.DRAFT_2020_12)
    val instance = """{ "value": true }"""

    val errors = validator.validate(schemaWithUnionType, instance)
    val typeError = errors.find { it.keyword == "type" }
    assertNotNull(typeError, "Should have type error")

    val expectedTypes = typeError!!.getExpectedTypes()
    assertTrue(expectedTypes.contains("string"), "Expected types should include 'string'; got: $expectedTypes")
    assertTrue(expectedTypes.contains("number"), "Expected types should include 'number'; got: $expectedTypes")
    assertEquals(2, expectedTypes.size, "Should have exactly 2 expected types; got: $expectedTypes")
  }

  @Test
  fun `Error getPropertySchema returns schema for specific property`() {
    val validator = NetworkntSchemaValidator(SpecificationVersion.DRAFT_2020_12)
    val instance = """{ "age": "not a number" }"""

    val errors = validator.validate(schema, instance)
    // Use the required error — its parentSchemaNode is the root object schema
    // which contains the "properties" map
    val requiredError = errors.find { it.keyword == "required" }
    assertNotNull(requiredError, "Should have required error")

    val agePropertySchema = requiredError!!.getPropertySchema("age")
    assertNotNull(agePropertySchema, "Should have schema for 'age' property")
    assertEquals("integer", agePropertySchema!!.get("type").textValue(), "Age property schema should have type 'integer'")

    val namePropertySchema = requiredError.getPropertySchema("name")
    assertNotNull(namePropertySchema, "Should have schema for 'name' property")
    assertEquals("string", namePropertySchema!!.get("type").textValue(), "Name property schema should have type 'string'")
  }

  @Test
  fun `unversioned schema URI falls back to configured draft version`() {
    // Schema with $schema: "http://json-schema.org/schema#" — the unversioned meta-schema URI.
    // networknt can't resolve this URI (no classpath resource, no network in tests).
    // FallbackDialectRegistry should fall back to the configured DRAFT_4 and validate correctly.
    val schemaWithUnversionedUri = """
      {
        "${'$'}schema": "http://json-schema.org/schema#",
        "type": "object",
        "properties": {
          "name": { "type": "string" }
        },
        "required": ["name"]
      }
    """.trimIndent()

    val version = SpecificationVersion.DRAFT_4
    val registry = SchemaRegistry.builder()
      .defaultDialectId(version.dialectId)
      .dialectRegistry(FallbackDialectRegistry(version))
      .build()

    // Should NOT throw — FallbackDialectRegistry handles the unresolvable $schema URI
    val parsedSchema = registry.getSchema(schemaWithUnversionedUri)
    val errors = parsedSchema.validate("""{ "age": 1 }""", InputFormat.JSON)

    assertTrue(errors.any { it.keyword == "required" }, "Should detect missing required 'name' field")
  }

  @Test
  fun `known draft URI is resolved without fallback`() {
    // Schema with a known $schema URI — should resolve directly, no fallback needed
    val schemaWithKnownDraft = """
      {
        "${'$'}schema": "http://json-schema.org/draft-04/schema#",
        "type": "object",
        "properties": {
          "count": { "type": "integer" }
        }
      }
    """.trimIndent()

    val version = SpecificationVersion.DRAFT_4
    val registry = SchemaRegistry.builder()
      .defaultDialectId(version.dialectId)
      .dialectRegistry(FallbackDialectRegistry(version))
      .build()

    val parsedSchema = registry.getSchema(schemaWithKnownDraft)
    val errors = parsedSchema.validate("""{ "count": "not-a-number" }""", InputFormat.JSON)

    assertTrue(errors.any { it.keyword == "type" }, "Should detect type mismatch for 'count'")
  }

  @Test
  fun `OpenAPI 3_0 schema validates invalid instance`() {
    // Reproduce the issue: OpenAPI schema loaded from spec.openapis.org has
    // $schema: "http://json-schema.org/draft-04/schema#" (standard Draft 4).
    // Validation should work but user reports zero errors in IDE.
    val openapiSchema = java.net.URI("https://spec.openapis.org/oas/3.0/schema/2021-09-28")
      .toURL().readText()

    val version = SpecificationVersion.DRAFT_4
    val registry = SchemaRegistry.builder()
      .defaultDialectId(version.dialectId)
      .dialectRegistry(FallbackDialectRegistry(version))
      .build()

    val parsedSchema = registry.getSchema(openapiSchema)

    // Minimal invalid OpenAPI — missing required 'info' and 'paths', bad version
    val invalidInstance = """
      {
        "openapi": "4.0.0",
        "endpoints": ["/pets"]
      }
    """.trimIndent()

    val errors = parsedSchema.validate(invalidInstance, InputFormat.JSON)

    assertTrue(errors.isNotEmpty(), "Should detect errors in invalid OpenAPI instance")
    assertTrue(errors.any { it.keyword == "required" }, "Should detect missing required fields (info, paths)")
  }

  @Test
  fun `if-then with required produces pattern error in matching branch`() {
    // Repro for YamlJsonSchema2020FeaturesTest.test if-then-else validation with inlined branch schemas:
    // schema has allOf of three if/then where the third branch's if-required matches the instance,
    // and its then.properties.postal_code.pattern should fail for "20500".
    val schemaJson = """
      {
        "type": "object",
        "properties": {
          "street_address": { "type": "string" },
          "country": { "enum": ["United States of America", "Canada", "Netherlands"] }
        },
        "allOf": [
          { "if": { "required": ["firstBranch"] },
            "then": { "properties": { "postal_code": { "pattern": "[0-9]{5}(-[0-9]{4})?" } } } },
          { "if": { "required": ["secondBranch"] },
            "then": { "properties": { "postal_code": { "pattern": "[A-Z][0-9][A-Z] [0-9][A-Z][0-9]" } } } },
          { "if": { "required": ["thirdBranch"] },
            "then": { "properties": { "postal_code": { "pattern": "[0-9]{4} [A-Z]{2}" } } } }
        ]
      }
    """.trimIndent()
    val instance = """{ "thirdBranch": true, "postal_code": "20500" }"""

    // `if`/`then` were introduced in Draft 7. networknt strictly ignores unknown keywords
    // under older drafts, so Draft 4/6 produce no errors at all — that is *correct* spec
    // behaviour, and the surrounding test guards against regressing in either direction.
    val expectsPattern = mapOf(
      SpecificationVersion.DRAFT_4 to false,
      SpecificationVersion.DRAFT_6 to false,
      SpecificationVersion.DRAFT_7 to true,
      SpecificationVersion.DRAFT_2019_09 to true,
      SpecificationVersion.DRAFT_2020_12 to true,
    )
    for ((version, shouldFire) in expectsPattern) {
      val errors = NetworkntSchemaValidator(version).validate(schemaJson, instance)
      val hasPattern = errors.any { it.keyword == "pattern" }
      assertEquals(
        shouldFire, hasPattern,
        "Pattern error under $version: expected=$shouldFire, got keywords=${errors.map { it.keyword }}"
      )
    }
  }

  @Test
  fun `grpc-style oneOf with two object branches against array emits per-branch type errors`() {
    // Reproduces the schema GrpcJsonSchemaBuilder generates for a message-typed field:
    //   "innerRequest" → oneOf[{$ref to InnerRequest}, {type:object}]
    // Both branches expect 'object'. Instance is array.
    // We need networknt to emit per-branch type errors with evaluationPath
    // /properties/innerRequest/oneOf/N/type — only then does our mapper produce
    // "Required one of: object" (Variant 3). Print the keywords + evalPaths so the test
    // also serves as ground-truth documentation.
    val schemaJson = """
      {
        "${'$'}schema": "http://json-schema.org/draft-04/schema#",
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
    val instance = """{ "innerRequest": [] }"""

    val errors = NetworkntSchemaValidator(SpecificationVersion.DRAFT_4).validate(schemaJson, instance)
    val keywords = errors.map { "${it.keyword}@${it.evaluationPath}" }
    val typeErrors = errors.filter { it.keyword == "type" }
    assertTrue(
      typeErrors.isNotEmpty(),
      "Expected at least one branch 'type' error, got: $keywords"
    )
    // Pin the shape of the evaluation paths: branch 0 goes through `$ref` (because its
    // schema is a $ref), branch 1's path ends directly at `/type`. Both must be recognised
    // as top-level branch type errors so that NetworkntErrorMapper merges them into a
    // single "Required one of: object" instead of letting branch 0 leak through as
    // "Required: object" via mapTypeError. Regression guard for the grpc oneOf-of-refs case.
    assertTrue(
      keywords.any { it.endsWith("/oneOf/0/\$ref/type") },
      "Expected branch-0 type error via \$ref, got: $keywords"
    )
    assertTrue(
      keywords.any { it.endsWith("/oneOf/1/type") },
      "Expected branch-1 direct type error, got: $keywords"
    )
  }

  @Test
  fun `nested map-of-map via dollar-ref additionalProperties resolves and emits leaf type error`() {
    // Documents that a clean two-level `additionalProperties: {$ref: …}` chain validates
    // correctly under networknt — i.e. nested map-of-map style schemas work in principle.
    // The companion gRPC integration test
    // `GrpcRequestBodySchemaTest.test validate map field value type` is overridden to keep
    // running on the legacy IJ engine because GrpcJsonSchemaBuilder also writes non-URI `id`
    // fields ("com.jetbrains.grpc.MainMessage/mapField") that networknt rejects with
    // `InvalidSchemaException` — fixing that requires changes in the gRPC schema generator
    // (out of scope for the wrapper module). This test pins the wrapper-side baseline.
    val schemaJson = """
      {
        "${'$'}schema": "http://json-schema.org/draft-04/schema#",
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

    val errors = NetworkntSchemaValidator(SpecificationVersion.DRAFT_4).validate(schemaJson, instance)
    val typeErrors = errors.filter { it.keyword == "type" }
    assertTrue(
      typeErrors.any { it.instanceLocation?.toString()?.endsWith("/secondaryValue") == true },
      "Expected a type error at /…/secondaryValue, got: ${errors.map { "${it.keyword}@${it.evaluationPath}" }}"
    )
  }

  @Test
  fun `CancellationChecker is called during validation`() {
    // Create a custom validator with ExecutionConfig that includes cancellation checker
    val registry = SchemaRegistry.builder()
      .defaultDialectId(SpecificationVersion.DRAFT_2020_12.dialectId)
      .build()
    val testSchema = registry.getSchema(schema)

    var checkerCalled = false

    try {
      val mapper = tools.jackson.databind.ObjectMapper()
      val instance = """{ "age": "not a number" }"""
      val jsonNode = mapper.readTree(instance)

      testSchema.validate(jsonNode) { executionContext ->
        executionContext.executionConfig { configBuilder ->
          configBuilder.cancellationChecker {
            checkerCalled = true
            throw RuntimeException("Validation cancelled")
          }
        }
      }
      fail("Should have thrown exception from cancellation checker")
    } catch (e: RuntimeException) {
      assertEquals("Validation cancelled", e.message, "Should have correct cancellation message")
      assertTrue(checkerCalled, "Cancellation checker should have been called")
    }
  }
}
