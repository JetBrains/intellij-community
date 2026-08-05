// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.intellij.openapi.actionSystem.AnActionHolder
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.dsl.builder.components.SegmentedButtonComponent
import com.intellij.util.ui.accessibility.ScreenReader
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import com.intellij.openapi.ui.ComboBox as IdeaComboBox

/**
 * The presentations a [SegmentedButtonComponent] renders come from an application service, and the combo
 * box it swaps for renders its items through another, so both need an application.
 */
@TestApplication
class SegmentedButtonTest {

  @Test
  fun swapsToAComboBoxAndBackAsTheItemsCrossWhatButtonsAllow() = runComposeSwingTest {
    var items by mutableStateOf(listOf("Auto", "On"))
    var selected by mutableStateOf<String?>("On")

    setContent {
      SegmentedButton(
        items = items,
        selectedItem = selected,
        onSelectedItemChange = { selected = it },
        renderer = { it },
        maxButtonsCount = 2,
      )
    }

    assertEquals("On", onNodeOfType<SegmentedButtonComponent<String>>().fetch().selectedItem)
    onNodeOfType<IdeaComboBox<String>>().assertDoesNotExist()

    items = listOf("Auto", "On", "Off")
    awaitIdle()

    onNodeOfType<SegmentedButtonComponent<String>>().assertDoesNotExist()
    assertEquals("On", onNodeOfType<IdeaComboBox<String>>().fetch().selectedItem)

    items = listOf("Auto", "On")
    awaitIdle()

    onNodeOfType<IdeaComboBox<String>>().assertDoesNotExist()
    assertEquals("On", onNodeOfType<SegmentedButtonComponent<String>>().fetch().selectedItem)
  }

  @Test
  fun swapsToAComboBoxAndBackAsAScreenReaderComesAndGoes() = runComposeSwingTest {
    val items = listOf("Auto", "On")
    var selected by mutableStateOf<String?>("On")

    setContent {
      SegmentedButton(
        items = items,
        selectedItem = selected,
        onSelectedItemChange = { selected = it },
        renderer = { it },
      )
    }
    awaitIdle()

    try {
      ScreenReader.setActive(true)
      awaitIdle()

      onNodeOfType<SegmentedButtonComponent<String>>().assertDoesNotExist()
      assertEquals("On", onNodeOfType<IdeaComboBox<String>>().fetch().selectedItem)
    }
    finally {
      ScreenReader.setActive(false)
    }
    awaitIdle()

    onNodeOfType<IdeaComboBox<String>>().assertDoesNotExist()
    assertEquals("On", onNodeOfType<SegmentedButtonComponent<String>>().fetch().selectedItem)
  }

  @Test
  fun reportsTheButtonTheUserPressed() = runComposeSwingTest {
    val items = listOf("Auto", "On", "Off")
    var selected by mutableStateOf<String?>("Auto")
    val chosen = mutableListOf<String?>()

    setContent {
      SegmentedButton(
        items = items,
        selectedItem = selected,
        onSelectedItemChange = {
          chosen += it
          selected = it
        },
        renderer = { it },
      )
    }

    // What pressing a segment does: the button's toggle action moves the component's selection.
    onNodeOfType<SegmentedButtonComponent<String>>().fetch().selectedItem = "Off"
    awaitIdle()

    assertEquals(listOf<String?>("Off"), chosen)
    assertEquals("Off", selected)
    assertEquals("Off", onNodeOfType<SegmentedButtonComponent<String>>().fetch().selectedItem)
  }

  @Test
  fun reportsTheItemTheUserChoseInTheComboBox() = runComposeSwingTest {
    val items = listOf("Auto", "On", "Off")
    var selected by mutableStateOf<String?>("Auto")
    val chosen = mutableListOf<String?>()

    setContent {
      SegmentedButton(
        items = items,
        selectedItem = selected,
        onSelectedItemChange = {
          chosen += it
          selected = it
        },
        renderer = { it },
        maxButtonsCount = 2,
      )
    }

    onNodeOfType<IdeaComboBox<String>>().fetch().selectedItem = "Off"
    awaitIdle()

    assertEquals(listOf<String?>("Off"), chosen)
    assertEquals("Off", selected)
    assertEquals("Off", onNodeOfType<IdeaComboBox<String>>().fetch().selectedItem)
  }

  @Test
  fun undoesAChoiceTheCallerDoesNotAdopt() = runComposeSwingTest {
    val items = listOf("Auto", "On", "Off")
    val selected = "Auto"

    setContent {
      SegmentedButton(items = items, selectedItem = selected, onSelectedItemChange = {}, renderer = { it })
    }

    onNodeOfType<SegmentedButtonComponent<String>>().fetch().selectedItem = "Off"
    awaitIdle()

    assertEquals("Auto", onNodeOfType<SegmentedButtonComponent<String>>().fetch().selectedItem)
  }

  @Test
  fun relabelsTheButtonsWhenTheRendererReturnsOtherText() = runComposeSwingTest {
    val items = listOf("auto", "on")
    var shout by mutableStateOf(false)

    setContent {
      SegmentedButton(
        items = items,
        selectedItem = "on",
        onSelectedItemChange = {},
        renderer = { if (shout) it.uppercase() else it },
      )
    }

    assertEquals(listOf("auto", "on"), onNodeOfType<SegmentedButtonComponent<String>>().fetch().buttonTexts())

    shout = true
    awaitIdle()

    assertEquals(listOf("AUTO", "ON"), onNodeOfType<SegmentedButtonComponent<String>>().fetch().buttonTexts())
  }

  /** The text of each segment, read off the toggle action the button was built for. */
  private fun SegmentedButtonComponent<*>.buttonTexts(): List<String?> =
    components.filterIsInstance<AnActionHolder>().map { it.action.templatePresentation.text }
}
