// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij

import com.intellij.platform.icons.impl.patchers.SvgPatchOperation
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins how a patch operation decides whether its condition holds.
 *
 * Every frontend resolves operations through this one method, so its answer is what makes the same icon render the same
 * way everywhere. The rule is narrow on purpose: case folding is correct for a color literal and wrong for anything
 * that carries an identifier.
 */
class SvgPatchOperationMatchesTest {
  @Test
  fun `hex colors match regardless of case`() {
    assertTrue(condition("fill", "#6c707e").matches("#6C707E"))
    assertTrue(condition("stroke", "#6C707E").matches("#6c707e"))
  }

  @Test
  fun `color keywords match regardless of case`() {
    assertTrue(condition("fill", "white").matches("WHITE"))
    assertTrue(condition("fill", "none").matches("None"))
  }

  @Test
  fun `hyphenated paint keywords match regardless of case`() {
    assertTrue(condition("fill", "context-fill").matches("CONTEXT-FILL"))
    assertTrue(condition("stroke", "context-stroke").matches("Context-Stroke"))
  }

  @Test
  fun `every color-valued attribute folds case`() {
    // `color` feeds `currentColor`, and the gradient and filter attributes are colors just as much as fill is.
    for (attribute in listOf("color", "solid-color", "stop-color", "flood-color", "lighting-color")) {
      assertTrue(condition(attribute, "#aabbcc").matches("#AABBCC")) { "$attribute did not fold case" }
    }
  }

  @Test
  fun `hex shorthands match the form they stand for`() {
    // Every digit stands for a doubled pair, so #fff names the same color as #ffffff.
    assertTrue(condition("fill", "#ffffff").matches("#fff"))
    assertTrue(condition("fill", "#fff").matches("#FFFFFF"))
    assertTrue(condition("fill", "#66cc77").matches("#6c7"))
  }

  @Test
  fun `an alpha the literal carries is not part of which color it names`() {
    // A substitution replaces the opacity of what it matched along with the color, so the authored alpha does not
    // decide whether the color is a palette color.
    assertTrue(condition("fill", "#6c707e").matches("#6c707eff"))
    assertTrue(condition("fill", "#6c707e").matches("#6C707E80"))
    assertTrue(condition("fill", "#ffffff").matches("#fff8"))
  }

  @Test
  fun `a hex literal of no valid length names no color`() {
    // There is no five- or seven-digit hex color; such a value is not a color and must not be expanded into one.
    assertFalse(condition("fill", "#6c707e").matches("#6c707"))
    assertFalse(condition("fill", "#ffffff").matches("#fffff"))
    assertFalse(condition("fill", "#ffffff").matches("#ff"))
  }

  @Test
  fun `different colors do not match`() {
    assertFalse(condition("fill", "#6c707e").matches("#818594"))
    assertFalse(condition("fill", "white").matches("black"))
    assertFalse(condition("fill", "#ffffff").matches("#fffffe"))
  }

  @Test
  fun `paint references keep their case`() {
    // The fragment in url(#id) names an element, and element ids are case-sensitive: folding case here would
    // repaint whichever gradient happened to differ only in capitalisation.
    assertFalse(condition("fill", "url(#gradient)").matches("url(#Gradient)"))
    assertTrue(condition("fill", "url(#gradient)").matches("url(#gradient)"))
  }

  @Test
  fun `non-color attributes keep their case`() {
    assertFalse(condition("id", "gradient").matches("Gradient"))
    assertTrue(condition("id", "gradient").matches("gradient"))
  }

  @Test
  fun `an absent attribute matches nothing`() {
    assertFalse(condition("fill", "#6c707e").matches(null))
  }

  private fun condition(attribute: String, expected: String): SvgPatchOperation =
    SvgPatchOperation(
      attributeName = attribute,
      value = "#ff0000",
      conditional = true,
      negatedCondition = false,
      expectedValue = expected,
      operation = SvgPatchOperation.Operation.Replace,
    )
}
