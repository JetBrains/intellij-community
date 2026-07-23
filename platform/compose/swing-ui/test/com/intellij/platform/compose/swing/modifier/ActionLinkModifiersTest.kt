// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.modifier

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.intellij.platform.compose.swing.components.ActionLink
import com.intellij.ui.components.ActionLink as IdeaActionLink
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImageMatches
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.junit.jupiter.api.Test
import javax.swing.ImageIcon
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActionLinkModifiersTest {

  @Test
  fun actionLinkCustomPropertiesMatchManualComponents() = runComposeSwingTest {
    var visited by mutableStateOf(false)
    var autoHideOnDisable by mutableStateOf(true)
    var showModifiers by mutableStateOf(true)

    setContent {
      val modifier = if (showModifiers) {
        SwingModifier.visited(visited).autoHideOnDisable(autoHideOnDisable)
      }
      else {
        SwingModifier
      }
      ActionLink(text = "Action link", onClick = {}, modifier = modifier)
    }

    onNodeWithText("Action link").apply {
      assertActionLinkMatches(IdeaActionLink("Action link"), fetch<IdeaActionLink>())
    }
    visited = true
    autoHideOnDisable = false
    awaitIdle()

    val expected = IdeaActionLink("Action link").apply {
      this.visited = true
      this.autoHideOnDisable = false
    }
    onNodeWithText("Action link").apply {
      val updatedLink = fetch<IdeaActionLink>()
      assertTrue(updatedLink.visited)
      assertFalse(updatedLink.autoHideOnDisable)
      // The reference component is given the composed one's bounds so both rasterize at one size.
      val actual = captureToImage()
      expected.setSize(actual.width, actual.height)
      assertImageMatches(expected.captureToImage())
    }

    showModifiers = false
    awaitIdle()

    onNodeWithText("Action link").apply {
      assertActionLinkMatches(IdeaActionLink("Action link"), fetch<IdeaActionLink>())
    }
  }

  @Test
  fun actionLinkIconModifiersMatchManualComponents() = runComposeSwingTest {
    val customIcon = ImageIcon()
    val cases = listOf(
      IconCase(SwingModifier.linkIcon()) { setLinkIcon() },
      IconCase(SwingModifier.contextHelpIcon()) { setContextHelpIcon() },
      IconCase(SwingModifier.externalLinkIcon()) { setExternalLinkIcon() },
      IconCase(SwingModifier.dropDownLinkIcon()) { setDropDownLinkIcon() },
      IconCase(SwingModifier.actionLinkIcon(customIcon, atRight = false)) { setIcon(customIcon, false) },
    )
    var modifier by mutableStateOf<SwingModifier>(SwingModifier)

    setContent {
      ActionLink(text = "Action link", onClick = {}, modifier = modifier)
    }

    cases.forEach { case ->
      modifier = case.modifier
      awaitIdle()

      val expected = IdeaActionLink("Action link").apply(case.configure)
      onNodeWithText("Action link").apply {
        assertActionLinkMatches(expected, fetch<IdeaActionLink>())
        val actual = captureToImage()
        expected.setSize(actual.width, actual.height)
        assertImageMatches(expected.captureToImage())
      }
    }

    modifier = SwingModifier
    awaitIdle()

    onNodeWithText("Action link").apply {
      assertActionLinkMatches(IdeaActionLink("Action link"), fetch<IdeaActionLink>())
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

  private class IconCase(
    val modifier: SwingModifier,
    val configure: IdeaActionLink.() -> Unit,
  )
}