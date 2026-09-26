// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImageMatches
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import com.intellij.ui.components.ActionLink as IdeaActionLink
import com.intellij.ui.components.BrowserLink as IdeaBrowserLink

class BrowserLinkTest {

  @Test
  fun browserLinkWithUrlMatchesManualComponents() = runComposeSwingTest {
    var url by mutableStateOf("https://initial.example")

    setContent {
      BrowserLink(url = url)
    }

    onNodeOfType<IdeaBrowserLink>().apply {
      assertBrowserLinkMatches(IdeaBrowserLink(url), fetch())
    }
    url = "https://updated.example"
    awaitIdle()

    val expected = IdeaBrowserLink(url)
    onNodeOfType<IdeaBrowserLink>().apply {
      assertBrowserLinkMatches(expected, fetch())
      // The reference component is given the composed one's bounds so both rasterize at one size.
      val actual = captureToImage()
      expected.setSize(actual.width, actual.height)
      assertImageMatches(expected.captureToImage())
    }
  }

  @Test
  fun browserLinkWithTextMatchesManualComponents() = runComposeSwingTest {
    var text by mutableStateOf("Initial")
    var url by mutableStateOf("https://initial.example")

    setContent {
      BrowserLink(text = text, url = url)
    }

    onNodeOfType<IdeaBrowserLink>().apply {
      assertBrowserLinkMatches(IdeaBrowserLink(text, url), fetch())
    }
    text = "Updated"
    url = "https://updated.example"
    awaitIdle()

    val expected = IdeaBrowserLink(text, url)
    onNodeOfType<IdeaBrowserLink>().apply {
      assertBrowserLinkMatches(expected, fetch())
      val actual = captureToImage()
      expected.setSize(actual.width, actual.height)
      assertImageMatches(expected.captureToImage())
    }
  }


  private fun assertBrowserLinkMatches(expected: IdeaBrowserLink, actual: IdeaBrowserLink) {
    assertEquals(expected.url, actual.url)
    assertActionLinkMatches(expected, actual)
    assertEquals(expected.componentPopupMenu != null, actual.componentPopupMenu != null)
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