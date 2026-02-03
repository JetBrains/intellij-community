// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.util.JewelLogger
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.theme.outlinedSplitButtonStyle
import org.junit.Rule
import org.junit.Test

class SplitButtonTest {
    @get:Rule val composeRule = createComposeRule()

    private val popupMenu: SemanticsNodeInteraction
        get() = composeRule.onNodeWithTag("Jewel.SplitButton.Popup")

    private val splitButton: SemanticsNodeInteraction
        get() = composeRule.onNodeWithTag("SplitButton")

    private val splitButtonSecondaryAction: SemanticsNodeInteraction
        get() = composeRule.onNodeWithTag("Jewel.SplitButton.SecondaryAction")

    @Test
    fun `popup width can be bigger than button width when nothing is set`() {
        composeRule.setContent {
            IntUiTheme {
                DefaultSplitButton(
                    onClick = {},
                    content = { Text("Split button") },
                    modifier = Modifier.width(200.dp).testTag("SplitButton"),
                    menuContent = {
                        items(
                            items = popupItems,
                            isSelected = { false },
                            onItemClick = { JewelLogger.getInstance("Jewel").warn("Item clicked: $it") },
                            content = { Text(it) },
                        )
                    },
                )
            }
        }

        splitButton.assertIsDisplayed()
        splitButtonSecondaryAction.performClick()
        composeRule.waitForIdle()

        popupMenu.assertIsDisplayed()
        composeRule.waitForIdle()

        // The popup should have at least the same width as the combobox (200dp)
        popupMenu.assertWidthIsAtLeast(200.dp)
    }

    @Test
    fun `popup width cannot be smaller than the button width when setting maxPopupWidth to a smaller value`() {
        composeRule.setContent {
            IntUiTheme {
                DefaultSplitButton(
                    onClick = {},
                    content = { Text("Split button") },
                    modifier = Modifier.width(200.dp).testTag("SplitButton"),
                    menuContent = {
                        items(
                            items = popupItems,
                            isSelected = { false },
                            onItemClick = { JewelLogger.getInstance("Jewel").warn("Item clicked: $it") },
                            content = { Text(it) },
                        )
                    },
                    maxPopupWidth = 100.dp, // Will be coerced to be at least 200dp as the button width
                )
            }
        }

        splitButton.assertIsDisplayed()
        splitButtonSecondaryAction.performClick()
        composeRule.waitForIdle()

        popupMenu.assertIsDisplayed()
        composeRule.waitForIdle()

        // The popup should have at least the same width as the combobox (200dp)
        popupMenu.assertWidthIsEqualTo(200.dp)
    }

    @Test
    fun `popup width cannot be smaller than the button width when setting width in the popupModifier`() {
        composeRule.setContent {
            IntUiTheme {
                DefaultSplitButton(
                    onClick = {},
                    content = { Text("Split button") },
                    modifier = Modifier.width(200.dp).testTag("SplitButton"),
                    popupModifier = Modifier.width(100.dp), // Will be coerced to be at least 200dp as the button width
                    menuContent = {
                        items(
                            items = popupItems,
                            isSelected = { false },
                            onItemClick = { JewelLogger.getInstance("Jewel").warn("Item clicked: $it") },
                            content = { Text(it) },
                        )
                    },
                )
            }
        }

        splitButton.assertIsDisplayed()
        splitButtonSecondaryAction.performClick()
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
                DefaultSplitButton(
                    onClick = {},
                    content = { Text("Split button") },
                    modifier = Modifier.width(200.dp).testTag("SplitButton"),
                    menuContent = {
                        items(
                            items = popupItems,
                            isSelected = { false },
                            onItemClick = { JewelLogger.getInstance("Jewel").warn("Item clicked: $it") },
                            content = { Text(it) },
                        )
                    },
                    maxPopupHeight = 100.dp,
                )
            }
        }

        splitButton.assertIsDisplayed()
        splitButtonSecondaryAction.performClick()
        composeRule.waitForIdle()

        popupMenu.assertIsDisplayed()
        composeRule.waitForIdle()

        popupMenu.assertHeightIsEqualTo(100.dp)
    }

    @Test
    fun `chevron does not get squashed when width-constrained (JEWEL-1061)`() {
        var expectedWidthDp: Dp = Dp.Unspecified
        composeRule.setContent {
            IntUiTheme {
                expectedWidthDp = JewelTheme.outlinedSplitButtonStyle.button.metrics.minSize.height

                OutlinedSplitButton(
                    onClick = {},
                    secondaryOnClick = {},
                    content = { Text("Long text that gets ellipsized", maxLines = 1) },
                    menuContent = {},
                    modifier = Modifier.width(80.dp),
                )
            }
        }

        // The chevron should be as wide as the min height of the button
        composeRule.onNodeWithTag("Jewel.SplitButton.SecondaryAction").assertWidthIsEqualTo(expectedWidthDp)
    }

    @Test
    fun `chevron and divider scale beyond the minimum button height if the button is taller (JEWEL-1061)`() {
        composeRule.setContent {
            IntUiTheme {
                OutlinedSplitButton(
                    onClick = {},
                    secondaryOnClick = {},
                    content = { Text("My text", maxLines = 1) },
                    menuContent = {},
                    modifier = Modifier.height(80.dp),
                )
            }
        }

        // The chevron should be as wide as the min height of the button
        composeRule.onNodeWithTag("Jewel.SplitButton.SecondaryAction").assertHeightIsEqualTo(80.dp)
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
