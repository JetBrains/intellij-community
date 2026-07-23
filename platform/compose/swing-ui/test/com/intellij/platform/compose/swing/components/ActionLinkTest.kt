// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.intellij.ui.components.ActionLink as IdeaActionLink
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImageMatches
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ActionLinkTest {

  @Test
  fun actionLinkMatchesManualComponents() = runComposeSwingTest {
    var text by mutableStateOf("Initial")
    var onClick by mutableStateOf<() -> Unit>({})
    var clickResult = ""

    setContent {
      ActionLink(text = text, onClick = onClick)
    }

    onNodeWithText("Initial").apply {
      assertActionLinkMatches(IdeaActionLink(text), fetch<IdeaActionLink>())
    }
    onClick = { clickResult = "Initial callback" }
    awaitIdle()
    onNodeWithText("Initial").performClick()
    assertEquals("Initial callback", clickResult)

    text = "Updated"
    onClick = { clickResult = "Updated callback" }
    awaitIdle()

    val expected = IdeaActionLink(text)
    onNodeWithText("Updated").apply {
      assertActionLinkMatches(expected, fetch<IdeaActionLink>())
      performClick()
      assertEquals("Updated callback", clickResult)
      // The reference component is given the composed one's bounds so both rasterize at one size.
      val actual = captureToImage()
      expected.setSize(actual.width, actual.height)
      assertImageMatches(expected.captureToImage())
    }
  }

  private fun assertActionLinkMatches(expected: IdeaActionLink, actual: IdeaActionLink) {
    assertEquals(expected.text, actual.text)
    assertEquals(expected.autoHideOnDisable, actual.autoHideOnDisable)
    assertEquals(expected.visited, actual.visited)
    assertEquals(expected.icon, actual.icon)
    assertEquals(expected.iconTextGap, actual.iconTextGap)
    assertEquals(expected.horizontalTextPosition, actual.horizontalTextPosition)
    assertEquals(expected.toolTipText, actual.toolTipText)
  }
}