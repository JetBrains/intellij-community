// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij

import com.intellij.platform.icons.impl.patchers.writeSvgAttribute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Pins how a patch writes a color, which every frontend does through this one function so that the same operation
 * produces the same document everywhere.
 *
 * A color attribute carries no alpha channel — alpha lives on the paired opacity attribute — so writing a color has to
 * land across both, and the opacity the document was authored with described the color that was just replaced.
 */
class SvgAttributeWriterTest {
  @Test
  fun `a translucent color is split across the color attribute and its opacity`() {
    // #ff000080 is not a value `fill` can hold: the attribute has no alpha channel.
    val result = write("fill", "#ff000080")

    assertEquals("#ff0000", result["fill"])
    assertEquals(0x80 / 255f, result["fill-opacity"]?.toFloat(), "the alpha belongs on fill-opacity")
  }

  @Test
  fun `an opaque color clears the opacity the document was authored with`() {
    val result = write("fill", "#ff0000", mapOf("fill-opacity" to "0.7"))

    assertEquals("#ff0000", result["fill"])
    assertNull(result["fill-opacity"], "0.7 described the color that was just replaced, not the new one")
  }

  @Test
  fun `every color attribute with a paired opacity carries its own`() {
    for ((attribute, opacity) in PAIRS) {
      val result = write(attribute, "#ff000080", mapOf(opacity to "0.7"))
      assertEquals("#ff0000", result[attribute])
      assertEquals(0x80 / 255f, result[opacity]?.toFloat()) { "$attribute did not write $opacity" }
    }
  }

  @Test
  fun `a value that is not a color leaves the opacity alone`() {
    val result = write("fill", "transparent", mapOf("fill-opacity" to "0.7"))

    assertEquals("transparent", result["fill"])
    assertEquals("0.7", result["fill-opacity"], "only a color write says anything about opacity")
  }

  @Test
  fun `an attribute that holds no color is written verbatim`() {
    assertEquals("#ff000080", write("stroke-width", "#ff000080")["stroke-width"])
  }

  private fun write(
    attributeName: String,
    value: String,
    attributes: Map<String, String> = emptyMap(),
  ): Map<String, String> {
    val result = attributes.toMutableMap()
    writeSvgAttribute(attributeName, value, { name, written -> result[name] = written }, { result.remove(it) })
    return result
  }

  private companion object {
    private val PAIRS =
      listOf(
        "fill" to "fill-opacity",
        "stroke" to "stroke-opacity",
        "stop-color" to "stop-opacity",
        "flood-color" to "flood-opacity",
      )
  }
}
