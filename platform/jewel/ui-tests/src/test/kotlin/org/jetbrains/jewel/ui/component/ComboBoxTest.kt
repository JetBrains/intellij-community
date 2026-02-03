// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.size
import kotlin.test.assertTrue
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.junit.Rule
import org.junit.Test

class ComboBoxTest {
    @get:Rule val composeRule = createComposeRule()

    private val popupMenu: SemanticsNodeInteraction
        get() = composeRule.onNodeWithTag("Jewel.ComboBox.Popup")

    private val comboBox: SemanticsNodeInteraction
        get() = composeRule.onNodeWithTag("ComboBox")

    @Test
    fun `popup width can be bigger than combo box width when nothing is set`() {
        composeRule.setContent {
            IntUiTheme {
                ComboBox(
                    labelText = "Just a label",
                    modifier = Modifier.width(200.dp).testTag("ComboBox"),
                    maxPopupWidth = Dp.Unspecified,
                    popupContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            popupItems.forEach { Text(it, modifier = Modifier.padding(horizontal = 16.dp)) }
                        }
                    },
                )
            }
        }

        comboBox.assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        popupMenu.assertIsDisplayed()
        composeRule.waitForIdle()

        // The popup should have at least the same width as the combobox (200dp)
        popupMenu.assertWidthIsAtLeast(200.dp)
    }

    @Test
    fun `popup width cannot be smaller than the combo box width when setting maxPopupWidth to a smaller value`() {
        composeRule.setContent {
            IntUiTheme {
                ComboBox(
                    labelText = "Just a label",
                    modifier = Modifier.width(200.dp).testTag("ComboBox"),
                    maxPopupWidth = 100.dp, // Will be coerced to be at least 200dp as the combo box width
                    popupContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            popupItems.forEach { Text(it, modifier = Modifier.padding(horizontal = 16.dp)) }
                        }
                    },
                )
            }
        }

        comboBox.assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        popupMenu.assertIsDisplayed()
        composeRule.waitForIdle()

        // The popup should have at least the same width as the combobox (200dp)
        popupMenu.assertWidthIsEqualTo(200.dp)
    }

    @Test
    fun `popup width cannot be smaller than the combo box width when setting width in the popupModifier`() {
        composeRule.setContent {
            IntUiTheme {
                ComboBox(
                    labelText = "Just a label",
                    modifier = Modifier.width(200.dp).testTag("ComboBox"),
                    maxPopupWidth = Dp.Unspecified,
                    popupModifier =
                        Modifier.width(100.dp), // Will be coerced to be at least 200dp as the combo box width
                    popupContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            popupItems.forEach { Text(it, modifier = Modifier.padding(horizontal = 16.dp)) }
                        }
                    },
                )
            }
        }

        comboBox.assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        popupMenu.assertIsDisplayed()
        composeRule.waitForIdle()

        // The popup should have at least the same width as the combobox (200dp)
        popupMenu.assertWidthIsEqualTo(200.dp)
    }

    @Test
    fun `popup max height from parameters should be respected`() {
        composeRule.setContent {
            IntUiTheme {
                ComboBox(
                    labelText = "Just a label",
                    modifier = Modifier.width(200.dp).testTag("ComboBox"),
                    maxPopupWidth = Dp.Unspecified,
                    maxPopupHeight = 100.dp,
                    popupContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            popupItems.forEach { Text(it, modifier = Modifier.padding(horizontal = 16.dp)) }
                        }
                    },
                )
            }
        }

        comboBox.assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        popupMenu.assertIsDisplayed()
        composeRule.waitForIdle()

        popupMenu.assertHeightIsEqualTo(100.dp)
    }

    @Test
    fun `popup resize based on the label content`() {
        val labelText = mutableStateOf("My Text")
        composeRule.setContent {
            IntUiTheme {
                ComboBox(
                    labelText = labelText.value,
                    modifier = Modifier.testTag("ComboBox"),
                    popupContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            popupItems.forEach { Text(it, modifier = Modifier.padding(horizontal = 16.dp)) }
                        }
                    },
                )
            }
        }

        val initialSize = composeRule.onNodeWithTag("ComboBox").getBoundsInRoot().size

        labelText.value = "This is another text to perform resize"
        composeRule.waitForIdle()

        val secondSize = composeRule.onNodeWithTag("ComboBox").getBoundsInRoot().size

        assertTrue(initialSize.width < secondSize.width, "The width of the combobox should have been resized")

        labelText.value = "small"
        composeRule.waitForIdle()

        val thirdSize = composeRule.onNodeWithTag("ComboBox").getBoundsInRoot().size

        assertTrue(secondSize.width > thirdSize.width, "The width of the combobox should have been resized")
    }

    @Test
    fun `popup should not change width if defined via modifier`() {
        val labelText = mutableStateOf("My Text")
        composeRule.setContent {
            IntUiTheme {
                ComboBox(
                    labelText = labelText.value,
                    modifier = Modifier.testTag("ComboBox").width(300.dp),
                    popupContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            popupItems.forEach { Text(it, modifier = Modifier.padding(horizontal = 16.dp)) }
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithTag("ComboBox").assertWidthIsEqualTo(300.dp)

        labelText.value = "This is another text to perform resize"
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ComboBox").assertWidthIsEqualTo(300.dp)

        labelText.value = "small"
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ComboBox").assertWidthIsEqualTo(300.dp)
    }

    @Test
    fun `popup should respect min and max width if defined`() {
        val labelText = mutableStateOf("My Text")
        composeRule.setContent {
            IntUiTheme {
                ComboBox(
                    labelText = labelText.value,
                    modifier = Modifier.testTag("ComboBox").widthIn(100.dp, 250.dp),
                    popupContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            popupItems.forEach { Text(it, modifier = Modifier.padding(horizontal = 16.dp)) }
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithTag("ComboBox").assertWidthIsEqualTo(100.dp)

        labelText.value = "This is another text to perform resize"
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ComboBox").assertWidthIsEqualTo(250.dp)

        labelText.value = "small"
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ComboBox").assertWidthIsEqualTo(100.dp)
    }

    private val popupItems =
        listOf(
            "Item 1",
            "Item 2",
            "Item 3",
            "Book",
            "Laughter",
            "Whisper",
            "Ocean",
            "Serendipity lorem ipsum dolor sit amet consectetur",
            "Umbrella",
            "Joy",
        )
}
