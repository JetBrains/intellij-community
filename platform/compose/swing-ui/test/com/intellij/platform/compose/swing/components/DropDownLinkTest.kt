// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import com.intellij.ui.components.DropDownLink as IdeaDropDownLink

class DropDownLinkTest {

  private val items = listOf("Alpha", "Beta", "Gamma")

  @Test
  fun pickingAnItemReportsItAndRewritesTheLinkText() = runComposeSwingTest {
    var selected by mutableStateOf("Alpha")
    val picked = mutableListOf<String>()

    setContent {
      DropDownLink(
        items = items,
        selectedItem = selected,
        onSelectedItemChange = {
          picked += it
          selected = it
        },
      )
    }

    assertEquals("Alpha", link().text)

    link().pick("Beta")
    awaitIdle()

    assertEquals(listOf("Beta"), picked)
    assertEquals("Beta", selected)
    assertEquals("Beta", link().selectedItem)
    assertEquals("Beta", link().text)
  }

  @Test
  fun aLinkThatDoesNotUpdateItsTextKeepsTheOneItStartedWith() = runComposeSwingTest {
    var selected by mutableStateOf("Alpha")

    setContent {
      DropDownLink(
        items = items,
        selectedItem = selected,
        updateText = false,
        onSelectedItemChange = { selected = it },
      )
    }

    link().pick("Gamma")
    awaitIdle()

    assertEquals("Gamma", selected)
    assertEquals("Gamma", link().selectedItem)
    assertEquals("Alpha", link().text)
  }

  @Test
  fun aPickTheCallerDoesNotAdoptIsPutBack() = runComposeSwingTest {
    val picked = mutableListOf<String>()

    setContent {
      DropDownLink(items = items, selectedItem = "Alpha", onSelectedItemChange = { picked += it })
    }

    link().pick("Beta")
    awaitIdle()

    assertEquals(listOf("Beta"), picked)
    assertEquals("Alpha", link().selectedItem)
    assertEquals("Alpha", link().text)
  }

  /**
   * Chooses [item] as the link's own popup does: its item-chosen callback writes the choice onto the
   * link, and that write is the whole of what reaches the composition. Showing the popup itself needs a
   * running IDE application, so the write is made directly.
   */
  private fun IdeaDropDownLink<String>.pick(item: String) {
    selectedItem = item
  }

  private fun ComposeSwingTest.link(): IdeaDropDownLink<String> {
    @Suppress("UNCHECKED_CAST")
    return onNodeOfType<IdeaDropDownLink<*>>().fetch() as IdeaDropDownLink<String>
  }
}
