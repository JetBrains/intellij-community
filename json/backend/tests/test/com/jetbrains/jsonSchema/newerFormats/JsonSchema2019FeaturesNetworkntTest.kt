// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.newerFormats

import com.intellij.openapi.util.registry.Registry

/**
 * Overrides for [JsonSchema2019FeaturesTest] where networknt produces different but correct behavior.
 *
 * networknt is the primary validation engine (registry key `json.schema.use.networknt.validation` defaults to `true`).
 * This subclass documents the differences and provides spec-compliant alternatives.
 *
 * See ADR-013 in `community/json/networknt.wrapper/docs/DECISIONS.md` for the subclass strategy rationale.
 */
internal class JsonSchema2019FeaturesNetworkntTest : JsonSchema2019FeaturesTest() {

  override fun setUp() {
    super.setUp()
    Registry.get("json.schema.use.networknt.validation").setValue(true, testRootDisposable)
  }

  // `test property dependencies` is NOT overridden — NetworkntErrorMapper now groups dependentRequired
  // errors by (instanceLocation, triggerProperty) to match the expected IJ behaviour (ADR Category 2).

  /**
   * Override: `$recursiveAnchor` must be a **boolean** per JSON Schema 2019-09 spec.
   * The base class test uses `"$recursiveAnchor": "branch"` (string) — a non-standard IJ extension.
   * networknt correctly rejects this with `SchemaException: must be a Boolean literal but is STRING`.
   *
   * This override uses the spec-compliant `$recursiveAnchor: true` with `$recursiveRef: "#"`.
   *
   * Spec references:
   * - 2019-09: $recursiveAnchor is boolean — https://www.learnjsonschema.com/2019-09/core/recursiveanchor/
   * - 2020-12: $dynamicAnchor (string) replaced $recursiveAnchor — https://json-schema.org/draft/2020-12/release-notes
   * - Design discussion: https://github.com/json-schema-org/json-schema-spec/issues/909
   */
  override fun `test remote recursive ref based object validation`() {
    val baseSchema = """
      {
        "${dollar}schema": "https://json-schema.org/draft/2019-09/schema",
        "${dollar}id": "https://example.com/schemas/base-schema",
        "${dollar}recursiveAnchor": true,

        "type": "object",
        "properties": {
          "test1": {
            "${dollar}recursiveRef": "#"
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
        "${dollar}recursiveAnchor": true,

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
