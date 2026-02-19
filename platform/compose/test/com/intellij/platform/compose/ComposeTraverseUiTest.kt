// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.TextFieldValue
import com.intellij.ide.ui.search.SearchableOptionEntry
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.NonNls
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.TriStateCheckboxRow
import org.junit.Test


class ComposeTraverseUiTest {

  private val traverseUiHelper = ComposeTraverseUiHelper()

  init {
    traverseUiHelper.setThemeProvider { content ->
      IntUiTheme { content() }
    }
  }

  @Test
  fun testBasicTextFields() {
    val textFields = listOf("Alpha", "Beta", "Gamma", "Delta")
    val content: @Composable () -> Unit = {
      BasicText(textFields[0])
      BasicTextField(value = TextFieldValue(textFields[1]), onValueChange = {})
      BasicText(textFields[2])
      BasicTextField(value = TextFieldValue(textFields[3]), onValueChange = {})
    }

    doTest(textFields, content)
  }

  @OptIn(ExperimentalJewelApi::class)
  @Test
  fun testJewelTextFields() {
    val textFields = listOf("My text field 1", "My text field 2", "My text field 3", "My text field 4")
    val content: @Composable () -> Unit = {
      Text(textFields[0])
      TextArea(value = TextFieldValue(textFields[1]), onValueChange = {})
      TextField(value = TextFieldValue(textFields[2]), onValueChange = {})
      Text(textFields[3])
    }
    doTest(textFields, content)
  }

  @Test
  fun testNestedStructure() {
    val textFields = listOf("My text field", "Some header text", "Buttons header", "Another text", "Some name")
    val content: @Composable () -> Unit = {
      Column {
        Row {
          GroupHeader(textFields[1])
          Text(textFields[0])
        }

        Row {
          Column {
            GroupHeader(textFields[2])
            DefaultButton(onClick = {}) { Text(textFields[3]) }
            DefaultButton(onClick = {}) { Text(textFields[4]) }

          }
        }
        Column {
          // empty text
          Text("")
        }
      }
    }
    doTest(textFields, content)
  }

  @OptIn(ExperimentalJewelApi::class)
  @Test
  fun testJewelCheckBoxes() {
    val textFields = listOf(
      "Group Title",
      "Enable feature X",
      "Tri-state option",
    )
    val content: @Composable () -> Unit = {
      GroupHeader(text = textFields[0])
      CheckboxRow(
        checked = false,
        onCheckedChange = {},
        text = textFields[1]
      )
      TriStateCheckboxRow(
        state = ToggleableState(true),
        onClick = {},
        text = textFields[2]
      )
    }

    doTest(textFields, content)
  }


  @Test
  fun testEmptyComponent() {
    val textFields = emptyList<String>()
    val content: @Composable () -> Unit = {
      Column {
        Row {
          Row {
          }
        }
      }
    }

    doTest(textFields, content)
  }

  @Test
  fun testJewelRadioRows() {
    val textFields = listOf(
      "Choose engine",
      "K2",
      "K1",
    )
    val content: @Composable () -> Unit = {
      GroupHeader(textFields[0])
      Column {
        RadioButtonRow(selected = true, onClick = {}, text = textFields[1])
        RadioButtonRow(selected = false, onClick = {}, text = textFields[2])
      }
    }
    doTest(textFields, content)
  }

  @Test
  fun testJewelComponents() {
    val textFields = listOf(
      "Checkbox turns on/off something",
      "Another checkbox",
    )
    val content: @Composable () -> Unit = {
      var checked by remember { mutableStateOf(ToggleableState.Off) }
      TriStateCheckboxRow(
        textFields[0],
        checked,
        onClick = {
          checked =
            when (checked) {
              ToggleableState.On -> ToggleableState.Off
              else -> ToggleableState.On
            }
        },
      )

      var checked2 by remember { mutableStateOf(ToggleableState.Off) }
      TriStateCheckboxRow(
        textFields[1],
        checked2,
        onClick = {
          checked2 =
            when (checked2) {
              ToggleableState.On -> ToggleableState.Off
              else -> ToggleableState.On
            }
        },
      )
    }

    doTest(textFields, content)
  }

  private fun doTest(expectedTextFields: List<String>, content: @Composable () -> Unit) {
    val configurable = object : ComposeSearchableConfigurable() {
      @Composable
      override fun ComposeContent() {
        content()
      }

      override fun getId(): @NonNls String {
        return "test.compose.configurable"
      }

      override fun getDisplayName(): @NlsContexts.ConfigurableName String? {
        return "Test compose configurable"
      }

      override fun isModified(): Boolean = false
      override fun apply() {}
    }

    try {
      val hits = collectHits(configurable)
      assertHits(hits, expectedTextFields)
    }
    finally {
      configurable.disposeUIResources()
    }
  }

  private fun collectHits(configurable: ComposeSearchableConfigurable): Set<String> {
    val options = linkedSetOf<SearchableOptionEntry>()
    traverseUiHelper.afterConfigurable(configurable, options)
    return options.map { it.hit }.toSet()
  }

  private fun assertHits(actual: Set<String>, expected: List<String>) {
    val expectedSet = expected.toSet()
    if (actual != expectedSet) {
      throw AssertionError("Unexpected hits. Expected=${expectedSet.sorted()} Actual=${actual.sorted()}")
    }
  }

}