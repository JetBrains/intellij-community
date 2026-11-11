// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.intellij.platform.compose.swing.components.style.ActionLinks
import com.intellij.platform.compose.swing.components.style.Comments
import com.intellij.platform.compose.swing.components.style.FormPanels
import com.intellij.platform.compose.swing.components.style.OptionButtons
import com.intellij.platform.compose.swing.components.style.SearchTextFields
import com.intellij.platform.compose.swing.components.style.SegmentedButtons
import com.intellij.platform.compose.swing.components.style.ThreeStateCheckBoxes
import com.intellij.platform.compose.swing.components.style.TitledSeparators
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.style.Buttons
import org.jetbrains.compose.swing.components.style.ComboBoxes
import org.jetbrains.compose.swing.components.style.Labels
import org.jetbrains.compose.swing.components.style.Panels
import org.jetbrains.compose.swing.components.style.Separators
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.AllComponents
import org.jetbrains.compose.swing.style.enabled
import org.jetbrains.compose.swing.style.toolTip
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Test
import javax.swing.JComponent
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Each component takes its own style, and a style an ancestor provides for its kind reaches it. The tool tip stands in
 * for any property: every one of these components is a `JComponent`.
 */
@TestApplication
class ComponentStyleTest {

  @Test
  fun eachComponentTakesItsOwnStyleAndGivesThePropertyBackWhenTheStyleGoes() = runComposeSwingTest {
    var styled by mutableStateOf(true)

    setContent {
      val tip = if (styled) "own" else null
      OneOfEach(
        actionLink = { ActionLink("Action", {}, SwingModifier.testTag(it), style = { if (tip != null) toolTip(tip) }) },
        browserLink = { BrowserLink("https://example.com", SwingModifier.testTag(it), style = { if (tip != null) toolTip(tip) }) },
        dropDownLink = { DropDownLink(listOf("x"), "x", {}, SwingModifier.testTag(it), style = { if (tip != null) toolTip(tip) }) },
        optionButton = { OptionButton("Option", {}, SwingModifier.testTag(it), style = { if (tip != null) toolTip(tip) }) },
        comboBox = { ComboBox(listOf("x"), "x", {}, SwingModifier.testTag(it), style = { if (tip != null) toolTip(tip) }) },
        threeStateCheckBox = {
          ThreeStateCheckBox("Three", ThreeStateCheckBoxState.NOT_SELECTED, {}, SwingModifier.testTag(it), style = { if (tip != null) toolTip(tip) })
        },
        comment = { Comment("Comment", SwingModifier.testTag(it), style = { if (tip != null) toolTip(tip) }) },
        titledSeparator = { TitledSeparator("Title", SwingModifier.testTag(it), style = { if (tip != null) toolTip(tip) }) },
        searchTextField = { SearchTextField("", {}, SwingModifier.testTag(it), style = { if (tip != null) toolTip(tip) }) },
        segmentedButton = {
          SegmentedButton(listOf("p", "q"), "p", {}, SwingModifier.testTag(it), style = { if (tip != null) toolTip(tip) }, renderer = { item -> item })
        },
        formPanel = { FormPanel(SwingModifier.testTag(it), style = { if (tip != null) toolTip(tip) }) { FormRow("Row:") {} } },
      )
    }

    TAGS.forEach { assertEquals("own", toolTipOf(it), it) }

    styled = false
    awaitIdle()

    TAGS.forEach { assertNull(toolTipOf(it), it) }
  }

  @Test
  fun eachComponentAppliesTheKeyOfItsKind() = runComposeSwingTest {
    setContent {
      Panel(
        style = {
          provideNestedStyle(ActionLinks) { toolTip("link") }
          provideNestedStyle(OptionButtons) { toolTip("optionButton") }
          provideNestedStyle(ComboBoxes) { toolTip("comboBox") }
          provideNestedStyle(ThreeStateCheckBoxes) { toolTip("threeStateCheckBox") }
          provideNestedStyle(Comments) { toolTip("comment") }
          provideNestedStyle(TitledSeparators) { toolTip("titledSeparator") }
          provideNestedStyle(SearchTextFields) { toolTip("searchTextField") }
          provideNestedStyle(SegmentedButtons) { toolTip("segmentedButton") }
          provideNestedStyle(FormPanels) { toolTip("formPanel") }
        },
      ) {
        OneOfEach()
      }
    }

    assertEquals("link", toolTipOf("actionLink"))
    assertEquals("link", toolTipOf("browserLink"))
    assertEquals("link", toolTipOf("dropDownLink"))
    (TAGS - listOf("actionLink", "browserLink", "dropDownLink")).forEach { assertEquals(it, toolTipOf(it)) }
  }

  @Test
  fun componentsThatLookLikeALibraryKindApplyItsKeyBeforeTheirOwn() = runComposeSwingTest {
    setContent {
      Panel(
        style = {
          provideNestedStyle(Buttons) { toolTip("button") }
          provideNestedStyle(Separators) { toolTip("separator") }
          provideNestedStyle(Panels) { toolTip("panel") }
          provideNestedStyle(Labels) { toolTip("label") }
          // The own key wins over the library key, wherever the two are provided.
          provideNestedStyle(OptionButtons) { toolTip("optionButton") }
        },
      ) {
        OneOfEach()
      }
    }

    assertEquals("optionButton", toolTipOf("optionButton"))
    assertEquals("separator", toolTipOf("titledSeparator"))
    assertEquals("panel", toolTipOf("formPanel"))
    assertEquals("label", onNodeWithText("Row:").fetch<JComponent>().toolTipText, "the label of a form row is a label")
    // A link is not a push button, so a style for buttons leaves it alone.
    assertNull(toolTipOf("actionLink"))
    assertNull(toolTipOf("browserLink"))
    assertNull(toolTipOf("dropDownLink"))
  }

  @Test
  fun aFormPanelStylesTheComponentsOfItsRows() = runComposeSwingTest {
    var enabled by mutableStateOf(false)

    setContent {
      FormPanel(style = { provideNestedStyle(AllComponents) { enabled(enabled) } }) {
        FormRow("Name:") { ActionLink("Action", {}, SwingModifier.testTag("link")) }
      }
    }

    assertFalse(onNodeWithTag("link").fetch<JComponent>().isEnabled)

    enabled = true
    awaitIdle()

    assertTrue(onNodeWithTag("link").fetch<JComponent>().isEnabled)
  }

  private fun ComposeSwingTest.toolTipOf(tag: String): String? = onNodeWithTag(tag).fetch<JComponent>().toolTipText

  private companion object {
    val TAGS = listOf(
      "actionLink", "browserLink", "dropDownLink", "optionButton", "comboBox", "threeStateCheckBox", "comment",
      "titledSeparator", "searchTextField", "segmentedButton", "formPanel",
    )
  }
}

/** One of each component, each tagged with its name in [ComponentStyleTest]. */
@Composable
private fun OneOfEach(
  actionLink: @Composable (String) -> Unit = { ActionLink("Action", {}, SwingModifier.testTag(it)) },
  browserLink: @Composable (String) -> Unit = { BrowserLink("https://example.com", SwingModifier.testTag(it)) },
  dropDownLink: @Composable (String) -> Unit = { DropDownLink(listOf("x"), "x", {}, SwingModifier.testTag(it)) },
  optionButton: @Composable (String) -> Unit = { OptionButton("Option", {}, SwingModifier.testTag(it)) },
  comboBox: @Composable (String) -> Unit = { ComboBox(listOf("x"), "x", {}, SwingModifier.testTag(it)) },
  threeStateCheckBox: @Composable (String) -> Unit = {
    ThreeStateCheckBox("Three", ThreeStateCheckBoxState.NOT_SELECTED, {}, SwingModifier.testTag(it))
  },
  comment: @Composable (String) -> Unit = { Comment("Comment", SwingModifier.testTag(it)) },
  titledSeparator: @Composable (String) -> Unit = { TitledSeparator("Title", SwingModifier.testTag(it)) },
  searchTextField: @Composable (String) -> Unit = { SearchTextField("", {}, SwingModifier.testTag(it)) },
  segmentedButton: @Composable (String) -> Unit = { SegmentedButton(listOf("p", "q"), "p", {}, SwingModifier.testTag(it), renderer = { item -> item }) },
  formPanel: @Composable (String) -> Unit = { FormPanel(SwingModifier.testTag(it)) { FormRow("Row:") {} } },
) {
  actionLink("actionLink")
  browserLink("browserLink")
  dropDownLink("dropDownLink")
  optionButton("optionButton")
  comboBox("comboBox")
  threeStateCheckBox("threeStateCheckBox")
  comment("comment")
  titledSeparator("titledSeparator")
  searchTextField("searchTextField")
  segmentedButton("segmentedButton")
  formPanel("formPanel")
}
