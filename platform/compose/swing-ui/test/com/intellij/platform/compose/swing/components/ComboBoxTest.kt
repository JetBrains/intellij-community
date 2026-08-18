// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Test
import java.awt.Component
import javax.swing.DefaultComboBoxModel
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import com.intellij.openapi.ui.ComboBox as IdeaComboBox

class ComboBoxTest {

  @Test
  fun comboBoxRoundTripsTheUsersPickThroughTheCallback() = runComposeSwingTest {
    var selected by mutableStateOf<String?>("Gradle")

    setContent {
      ComboBox(
        items = listOf("Gradle", "Maven", "Bazel"),
        selectedItem = selected,
        onSelectedItemChange = { selected = it },
      )
    }

    val comboBox = onNodeOfType<IdeaComboBox<String>>().fetch()
    assertEquals("Gradle", comboBox.selectedItem)

    // What the user picking an item in the popup leaves behind.
    comboBox.selectedItem = "Bazel"
    awaitIdle()

    assertEquals("Bazel", selected)
    assertEquals("Bazel", comboBox.selectedItem)
  }

  @Test
  fun comboBoxUndoesAPickTheCallerDoesNotAdopt() = runComposeSwingTest {
    val picks = mutableListOf<String?>()

    setContent {
      ComboBox(
        items = listOf("Gradle", "Maven"),
        selectedItem = "Gradle",
        onSelectedItemChange = { picks += it },
      )
    }

    val comboBox = onNodeOfType<IdeaComboBox<String>>().fetch()
    comboBox.selectedItem = "Maven"
    awaitIdle()

    assertEquals(listOf<String?>("Maven"), picks)
    assertEquals("Gradle", comboBox.selectedItem)
  }

  @Test
  fun comboBoxFollowsChangesToTheItems() = runComposeSwingTest {
    var items by mutableStateOf(listOf("Gradle", "Maven"))
    var selected by mutableStateOf<String?>("Maven")

    setContent {
      ComboBox(items = items, selectedItem = selected, onSelectedItemChange = { selected = it })
    }

    assertEquals(listOf("Gradle", "Maven"), onNodeOfType<IdeaComboBox<String>>().fetch().offeredItems())

    items = listOf("Gradle", "Maven", "Bazel")
    awaitIdle()

    val comboBox = onNodeOfType<IdeaComboBox<String>>().fetch()
    assertEquals(listOf("Gradle", "Maven", "Bazel"), comboBox.offeredItems())
    assertEquals("Maven", comboBox.selectedItem)
    assertEquals("Maven", selected)
  }

  @Test
  fun comboBoxDropsAndReportsASelectionTheItemsNoLongerContain() = runComposeSwingTest {
    var items by mutableStateOf(listOf("Gradle", "Maven"))
    var selected by mutableStateOf<String?>("Maven")

    setContent {
      ComboBox(items = items, selectedItem = selected, onSelectedItemChange = { selected = it })
    }

    items = listOf("Gradle", "Bazel")
    awaitIdle()

    assertNull(selected)
    assertNull(onNodeOfType<IdeaComboBox<String>>().fetch().selectedItem)
  }

  @Test
  fun comboBoxShowsAndReportsTheCallerOwnedModel() = runComposeSwingTest {
    val model = DefaultComboBoxModel(arrayOf("Gradle", "Maven"))
    var picked: String? = null

    setContent {
      ComboBox(model = model, onSelectedItemChange = { picked = it })
    }

    val comboBox = onNodeOfType<IdeaComboBox<String>>().fetch()
    assertSame(model, comboBox.model)
    assertEquals("Gradle", comboBox.selectedItem)

    comboBox.selectedItem = "Maven"
    awaitIdle()

    assertEquals("Maven", picked)
    assertEquals("Maven", model.selectedItem)
  }

  @Test
  fun comboBoxRendersItemsThroughTheSuppliedRenderer() = runComposeSwingTest {
    val cell = JLabel("cell")
    var renderer by mutableStateOf<ListCellRenderer<in String>?>(null)

    setContent {
      ComboBox(
        items = listOf("Gradle"),
        selectedItem = "Gradle",
        onSelectedItemChange = {},
        renderer = renderer,
      )
    }

    val comboBox = onNodeOfType<IdeaComboBox<String>>().fetch()
    assertNotSame(cell, comboBox.renderRow("Gradle"))

    renderer = ListCellRenderer<String> { _, _, _, _, _ -> cell }
    awaitIdle()
    assertSame(cell, comboBox.renderRow("Gradle"))

    renderer = null
    awaitIdle()
    assertNotSame(cell, comboBox.renderRow("Gradle"))
  }

  /** The items the combo box offers, in the order its popup lists them. */
  private fun IdeaComboBox<String>.offeredItems(): List<String> = (0 until itemCount).map { getItemAt(it) }

  /**
   * The component the combo box's renderer stamps for [item], as its popup asks for a row.
   *
   * The renderer is reached through a raw element type because it comes back projected as
   * `ListCellRenderer<in String>`, whose stamping method cannot be handed a `String`.
   */
  @Suppress("UNCHECKED_CAST")
  private fun IdeaComboBox<String>.renderRow(item: String): Component =
    (renderer as ListCellRenderer<Any?>).getListCellRendererComponent(JList(), item, 0, false, false)
}
