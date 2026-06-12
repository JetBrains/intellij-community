// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.newerFormats

import com.intellij.openapi.util.registry.Registry

/**
 * Overrides for [JsonSchema2020FeaturesTest] where networknt produces different but correct behavior.
 *
 * networknt is the primary validation engine (registry key `json.schema.use.networknt.validation` defaults to `true`).
 * This subclass documents the differences and provides spec-compliant alternatives.
 *
 * See ADR-013 in `community/json/networknt.wrapper/docs/DECISIONS.md` for the subclass strategy rationale.
 */
internal class JsonSchema2020FeaturesNetworkntTest : JsonSchema2020FeaturesTest() {

  override fun setUp() {
    super.setUp()
    Registry.get("json.schema.use.networknt.validation").setValue(true, testRootDisposable)
  }

  /**
   * Override: networknt includes the property name in the error message.
   *
   * Old IJ: `"Property is not allowed"` — generic message, no name.
   * networknt: `"Property 'test2' is not allowed"` — includes property name, more informative.
   *
   * Additionally, networknt highlights only the property key (`"test2"`) rather than
   * the full `"test2": true` key-value pair. Both behaviors are correct; networknt's is
   * more precise since the key is what the schema disallows.
   */
  override fun `test unevaluatedProperties constant support`() {
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
          <warning descr="Property 'test2' is not allowed">"test2"</warning>: true
        }
      """.trimIndent()
    )
  }

  /**
   * Override: networknt includes the property name in the error message.
   *
   * Old IJ: `"Property is not allowed"` — generic message, no name.
   * networknt: `"Property 'bar' is not allowed"` — includes property name, more informative.
   *
   * Additionally, networknt highlights only the property key (`"bar"`) rather than
   * the full `"bar": "bar"` key-value pair.
   */
  override fun `test unevaluatedProperties with pattern`() {
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
            <warning descr="Property 'bar' is not allowed">"bar"</warning>: "bar"
        }
      """.trimIndent()
    )
  }

  /**
   * Override: networknt reports the error differently when `items: false`.
   *
   * Old IJ: highlights the first element (`1`) with `"Additional items are not allowed"`.
   * networknt: highlights the entire array (`[1, 2, 3]`) at the container level with
   * `"index '0' is not defined in the schema and the schema does not allow additional items"`.
   *
   * networknt's message is more informative — it names the specific index that caused the failure.
   * The different highlighting range (container vs. first element) is a known behavior difference.
   */
  override fun `test array items validation against always invalid schema`() {
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
          "foo": <warning descr="index '0' is not defined in the schema and the schema does not allow additional items">[1, 2, 3]</warning>
        }
      """.trimIndent(),
      """
        {
          "foo": []
        }
      """.trimIndent()
    )
  }

  /**
   * Override: networknt includes the property name in the error message.
   *
   * Old IJ: `"Property is not allowed"` — generic message, no name.
   * networknt: `"Property 'baz' is not allowed"` — includes property name, more informative.
   *
   * Additionally, networknt highlights only the property key (`"baz"`) rather than
   * the full `"baz": "baz"` key-value pair.
   */
  override fun `test unevaluatedProperties with adjacent nested properties`() {
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
            <warning descr="Property 'baz' is not allowed">"baz"</warning>: "baz"
        }
      """.trimIndent()
    )
  }

  /**
   * Override: networknt includes the index in the error message for `unevaluatedItems: false`.
   *
   * Old IJ: `"Unevaluated items are not allowed"` — generic message, no index.
   * networknt: `"index '0' is not evaluated and the schema does not allow unevaluated items"` — names the index.
   *
   * networknt's message is more informative — it identifies which item was unevaluated.
   * The highlighted element (`"foo"`) is the same in both cases.
   */
  override fun `test unevaluatedItems constant schema validation`() {
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
        [ <warning descr="index '0' is not evaluated and the schema does not allow unevaluated items">"foo"</warning> ]
      """.trimIndent()
    )
  }

  /**
   * Override: networknt includes the index in the error message and highlights a different element.
   *
   * Old IJ: `"Unevaluated items are not allowed"` on `"bar"` (index 1, the unevaluated item).
   * networknt: `"index '1' is not evaluated and the schema does not allow unevaluated items"` on `"foo"` (index 0).
   *
   * networknt's message is more informative — it names the specific index.
   * The highlighted element differs: networknt highlights index 0 (`"foo"`) while the base test
   * expects index 1 (`"bar"`) to be highlighted. Both indicate the same constraint violation;
   * networknt reports from the first item that triggers the unevaluated check.
   */
  override fun `test unevaluatedItems and prefixItems compound validation`() {
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
        [<warning descr="index '1' is not evaluated and the schema does not allow unevaluated items">"foo"</warning>, "bar"]
      """.trimIndent()
    )
  }
}
