// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.intellij.platform.compose.swing.components.style.ActionLinkStyleScope
import com.intellij.platform.compose.swing.components.style.actionLinkIcon
import com.intellij.platform.compose.swing.components.style.autoHideOnDisable
import com.intellij.platform.compose.swing.components.style.contextHelpIcon
import com.intellij.platform.compose.swing.components.style.dropDownLinkIcon
import com.intellij.platform.compose.swing.components.style.externalLinkIcon
import com.intellij.platform.compose.swing.components.style.linkIcon
import com.intellij.platform.compose.swing.components.style.visited
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImageMatches
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.junit.jupiter.api.Test
import javax.swing.ImageIcon
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.intellij.ui.components.ActionLink as IdeaActionLink

class ActionLinkStyleTest {

  @Test
  fun actionLinkStylePropertiesMatchManualComponents() = runComposeSwingTest {
    var visited by mutableStateOf(false)
    var autoHideOnDisable by mutableStateOf(true)
    var styled by mutableStateOf(true)

    setContent {
      val style = if (styled) {
        SwingStyle<ActionLinkStyleScope> {
          visited(visited)
          autoHideOnDisable(autoHideOnDisable)
        }
      }
      else {
        SwingStyle
      }
      ActionLink(text = "Action link", onClick = {}, style = style)
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

    styled = false
    awaitIdle()

    onNodeWithText("Action link").apply {
      assertActionLinkMatches(IdeaActionLink("Action link"), fetch<IdeaActionLink>())
    }
  }

  @Test
  fun actionLinkIconSettersMatchManualComponents() = runComposeSwingTest {
    val customIcon = ImageIcon()
    val cases = listOf(
      IconCase({ linkIcon() }) { setLinkIcon() },
      IconCase({ contextHelpIcon() }) { setContextHelpIcon() },
      IconCase({ externalLinkIcon() }) { setExternalLinkIcon() },
      IconCase({ dropDownLinkIcon() }) { setDropDownLinkIcon() },
      IconCase({ actionLinkIcon(customIcon) }) { setIcon(customIcon, false) },
      IconCase({ actionLinkIcon(customIcon, atRight = true) }) { setIcon(customIcon, true) },
    )
    var style by mutableStateOf<SwingStyle<ActionLinkStyleScope>>(SwingStyle)

    setContent {
      ActionLink(text = "Action link", onClick = {}, style = style)
    }

    cases.forEach { case ->
      style = case.style
      awaitIdle()

      val expected = IdeaActionLink("Action link").apply(case.configure)
      onNodeWithText("Action link").apply {
        assertActionLinkMatches(expected, fetch<IdeaActionLink>())
        val actual = captureToImage()
        expected.setSize(actual.width, actual.height)
        assertImageMatches(expected.captureToImage())
      }
    }

    style = SwingStyle
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
    val style: SwingStyle<ActionLinkStyleScope>,
    val configure: IdeaActionLink.() -> Unit,
  )
}