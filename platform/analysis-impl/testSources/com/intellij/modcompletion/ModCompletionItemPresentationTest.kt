// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcompletion

import com.intellij.openapi.util.text.MarkupText
import com.intellij.openapi.util.text.MarkupText.Kind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.swing.ImageIcon

internal class ModCompletionItemPresentationTest {
  @Test
  fun `equal texts compare as equal`() {
    val first = presentation("foo", "bar")
    val second = presentation("foo", "bar")
    assertEquals(0, first.compareTo(second))
  }

  @Test
  fun `main text is compared first`() {
    assertLess(presentation("a", "z"), presentation("b", "a"))
  }

  @Test
  fun `detail text is compared when main text is equal`() {
    assertLess(presentation("foo", "a"), presentation("foo", "b"))
    assertLess(presentation("foo"), presentation("foo", "a"))
  }

  @Test
  fun `kind of main text is compared after its text`() {
    val normal = presentation("foo")
    val grayed = ModCompletionItemPresentation(MarkupText.plainText("foo").highlightAll(Kind.GRAYED))
    assertLess(normal, grayed)
  }

  @Test
  fun `icons are ignored`() {
    val withIcons = presentation("foo", "bar")
      .withMainIcon { ImageIcon() }
      .withDetailIcon { ImageIcon() }
    assertEquals(0, withIcons.compareTo(presentation("foo", "bar")))
  }

  @Test
  fun `sort uses main text then detail text`() {
    val items = listOf(
      presentation("b"),
      presentation("a", "y"),
      presentation("a", "x"),
      presentation("a"),
    )
    val sorted = items.sorted().map { it.mainText().toText() + "|" + it.detailText().toText() }
    assertEquals(listOf("a|", "a|x", "a|y", "b|"), sorted)
  }

  private fun presentation(mainText: String, detailText: String = ""): ModCompletionItemPresentation {
    return ModCompletionItemPresentation(MarkupText.plainText(mainText)).withDetailText(MarkupText.plainText(detailText))
  }

  private fun assertLess(smaller: ModCompletionItemPresentation, greater: ModCompletionItemPresentation) {
    assertTrue(smaller < greater, "$smaller must be less than $greater")
    assertTrue(greater > smaller, "$greater must be greater than $smaller")
  }
}
