// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.networknt.wrapper

import com.networknt.schema.SpecificationVersion
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Test to verify draft-06 exclusiveMinimum/exclusiveMaximum behavior.
 *
 * This test investigates why networknt doesn't produce errors for draft-06 style
 * exclusiveMinimum/exclusiveMaximum (standalone number values).
 */
class Draft06ExclusiveTest {

  @Test
  fun `draft-04 exclusiveMinimum with boolean - should error`() {
    // Draft-04 format: exclusiveMinimum is a boolean modifier
    val schema = """
      {
        "properties": {
          "prop": {
            "minimum": 3,
            "exclusiveMinimum": true
          }
        }
      }
    """.trimIndent()

    val validator = NetworkntSchemaValidator(SpecificationVersion.DRAFT_4)

    // Value 2 should error (< 3)
    val errors1 = validator.validate(schema, """{"prop": 2}""")
    assertTrue(errors1.isNotEmpty(), "Draft-04: value 2 should error (below exclusive minimum 3)")

    // Value 3 should error (= 3, exclusive)
    val errors2 = validator.validate(schema, """{"prop": 3}""")
    assertTrue(errors2.isNotEmpty(), "Draft-04: value 3 should error (equal to exclusive minimum 3)")

    // Value 4 should pass
    val errors3 = validator.validate(schema, """{"prop": 4}""")
    assertTrue(errors3.isEmpty(), "Draft-04: value 4 should pass")
  }

  @Test
  fun `draft-06 exclusiveMinimum with number - should error`() {
    // Draft-06 format: exclusiveMinimum is a standalone number
    val schema = """
      {
        "properties": {
          "prop": {
            "exclusiveMinimum": 3
          }
        }
      }
    """.trimIndent()

    val validator = NetworkntSchemaValidator(SpecificationVersion.DRAFT_6)

    // Value 2 should error (< 3)
    val errors1 = validator.validate(schema, """{"prop": 2}""")
    assertTrue(errors1.isNotEmpty(), "Draft-06: value 2 should error (below exclusive minimum 3)")

    // Value 3 should error (= 3, exclusive)
    val errors2 = validator.validate(schema, """{"prop": 3}""")
    assertTrue(errors2.isNotEmpty(), "Draft-06: value 3 should error (equal to exclusive minimum 3)")

    // Value 4 should pass
    val errors3 = validator.validate(schema, """{"prop": 4}""")
    assertTrue(errors3.isEmpty(), "Draft-06: value 4 should pass")
  }

  @Test
  fun `draft-06 exclusiveMaximum with number - should error`() {
    // Draft-06 format: exclusiveMaximum is a standalone number
    val schema = """
      {
        "properties": {
          "prop": {
            "exclusiveMaximum": 3
          }
        }
      }
    """.trimIndent()

    val validator = NetworkntSchemaValidator(SpecificationVersion.DRAFT_6)

    // Value 2 should pass
    val errors1 = validator.validate(schema, """{"prop": 2}""")
    assertTrue(errors1.isEmpty(), "Draft-06: value 2 should pass")

    // Value 3 should error (= 3, exclusive)
    val errors2 = validator.validate(schema, """{"prop": 3}""")
    assertTrue(errors2.isNotEmpty(), "Draft-06: value 3 should error (equal to exclusive maximum 3)")

    // Value 4 should error (> 3)
    val errors3 = validator.validate(schema, """{"prop": 4}""")
    assertTrue(errors3.isNotEmpty(), "Draft-06: value 4 should error (above exclusive maximum 3)")
  }

  @Test
  fun `draft-07 exclusiveMinimum with number - should error`() {
    // Draft-07 uses same format as draft-06 (number, not boolean)
    val schema = """
      {
        "properties": {
          "prop": {
            "exclusiveMinimum": 3
          }
        }
      }
    """.trimIndent()

    val validator = NetworkntSchemaValidator(SpecificationVersion.DRAFT_7)

    // Value 2 should error (< 3)
    val errors1 = validator.validate(schema, """{"prop": 2}""")
    assertTrue(errors1.isNotEmpty(), "Draft-07: value 2 should error (below exclusive minimum 3)")

    // Value 3 should error (= 3, exclusive)
    val errors2 = validator.validate(schema, """{"prop": 3}""")
    assertTrue(errors2.isNotEmpty(), "Draft-07: value 3 should error (equal to exclusive minimum 3)")

    // Value 4 should pass
    val errors3 = validator.validate(schema, """{"prop": 4}""")
    assertTrue(errors3.isEmpty(), "Draft-07: value 4 should pass")
  }

  @Test
  fun `schema without $schema declaration defaults to what version`() {
    val schema = """
      {
        "properties": {
          "prop": {
            "exclusiveMinimum": 3
          }
        }
      }
    """.trimIndent()

    // Try with different versions to see which one produces errors
    for (version in listOf(
      SpecificationVersion.DRAFT_4,
      SpecificationVersion.DRAFT_6,
      SpecificationVersion.DRAFT_7,
      SpecificationVersion.DRAFT_2019_09,
      SpecificationVersion.DRAFT_2020_12
    )) {
      val validator = NetworkntSchemaValidator(version)
      val errors = validator.validate(schema, """{"prop": 2}""")
      if (version != SpecificationVersion.DRAFT_4) {
        assertTrue(errors.isNotEmpty(), "$version should produce errors for exclusiveMinimum as number")
      }
    }
  }
}
