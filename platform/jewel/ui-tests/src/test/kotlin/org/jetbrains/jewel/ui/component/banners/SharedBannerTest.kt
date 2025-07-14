package org.jetbrains.jewel.ui.component.banners

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToLog
import org.jetbrains.jewel.ui.component.banner.BannerIconActionScope
import org.jetbrains.jewel.ui.component.banner.BannerLinkActionScope
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

abstract class SharedBannerTest {
    @Test
    fun `when there is only one link actions, render it normally`() {
        runBannerTest(linkActions = { action("Action") {} }) { onNodeWithText("Action").assertIsDisplayed() }
    }

    @Test
    fun `when there are two link actions, render it normally`() {
        runBannerTest(
            linkActions = {
                action("Action 1") {}
                action("Action 2") {}
            }
        ) {
            onNodeWithText("Action 1").assertIsDisplayed()
            onNodeWithText("Action 2").assertIsDisplayed()
        }
    }

    @Test
    fun `when there are three link actions, render it normally`() {
        runBannerTest(
            linkActions = {
                action("Action 1") {}
                action("Action 2") {}
                action("Action 3") {}
            }
        ) {
            onNodeWithText("Action 1").assertIsDisplayed()
            onNodeWithText("Action 2").assertIsDisplayed()
            onNodeWithText("Action 3").assertIsDisplayed()
        }
    }

    @Test
    fun `when there are four link actions, render two of them and add the show more button`() {
        runBannerTest(
            linkActions = {
                action("Action 1") {}
                action("Action 2") {}
                action("Action 3") {}
                action("Action 4") {}
            }
        ) {
            onNodeWithText("Action 1").assertIsDisplayed()
            onNodeWithText("Action 2").assertIsDisplayed()
            onNodeWithText("More").assertIsDisplayed()

            onNodeWithText("Action 3").assertIsNotDisplayed()
            onNodeWithText("Action 4").assertIsNotDisplayed()
        }
    }

    @Test
    fun `all visible actions must be clickable`() {
        val clickedItems = MutableList(4) { false }
        runBannerTest(
            linkActions = {
                for (i in clickedItems.indices) {
                    action("Action ${i + 1}") { clickedItems[i] = true }
                }
            }
        ) {
            for (i in clickedItems.indices) {
                onNodeWithText("Action ${i + 1}").performClick()
            }

            // Visible items are clicked
            assertTrue("Action 1 was not clicked", clickedItems[0])
            assertTrue("Action 2 was not clicked", clickedItems[1])

            // Hidden items can't be accessed, so they are not clicked
            assertFalse("Action 3 was not clicked", clickedItems[2])
            assertFalse("Action 4 was not clicked", clickedItems[3])
        }
    }

    @Test
    fun `hidden items must only perform click after opening the dropdown`() {
        val clickedItems = MutableList(4) { false }
        runBannerTest(
            linkActions = {
                for (i in clickedItems.indices) {
                    action("Action ${i + 1}") { clickedItems[i] = true }
                }
            }
        ) {
            onNodeWithText("Action 1").performClick()
            onNodeWithText("Action 2").performClick()

            onNodeWithText("More").performClick()
            onNode(hasText("Action 3").and(hasAnyAncestor(isPopup()))).performClick()

            onNodeWithText("More").performClick()
            onNode(hasText("Action 4").and(hasAnyAncestor(isPopup()))).performClick()

            // All items got clicked
            assertTrue("Action 1 was not clicked", clickedItems[0])
            assertTrue("Action 2 was not clicked", clickedItems[1])
            assertTrue("Action 3 was not clicked", clickedItems[2])
            assertTrue("Action 4 was not clicked", clickedItems[3])
        }
    }

    @Test
    fun `after clicking an option hides the popup`() {
        val clickedItems = MutableList(4) { false }
        runBannerTest(
            linkActions = {
                for (i in clickedItems.indices) {
                    action("Action ${i + 1}") { clickedItems[i] = true }
                }
            }
        ) {
            // Clicking "More" show the popup
            onNodeWithText("More").performClick()
            onNode(hasText("Action 3").and(hasAnyAncestor(isPopup()))).assertIsDisplayed()
            onNode(hasText("Action 4").and(hasAnyAncestor(isPopup()))).assertIsDisplayed()

            // Clicking in any action hides the popup
            onNode(hasText("Action 3").and(hasAnyAncestor(isPopup()))).performClick()
            onNode(hasText("Action 3").and(hasAnyAncestor(isPopup()))).assertDoesNotExist()
            onNode(hasText("Action 4").and(hasAnyAncestor(isPopup()))).assertDoesNotExist()
        }
    }

    @Test
    fun `icon buttons must be visible`() {
        runBannerTest(
            iconActions = {
                iconAction(AllIconsKeys.General.Gear, "Settings") {}
                iconAction(AllIconsKeys.Actions.Close, "Close") {}
            }
        ) {
            onNodeWithContentDescription("Settings").assertIsDisplayed()
            onNodeWithContentDescription("Close").assertIsDisplayed()
        }
    }

    @Test
    fun `icon buttons must be clickable`() {
        var settingsClicked = false
        var closeClicked = false

        runBannerTest(
            iconActions = {
                iconAction(AllIconsKeys.General.Gear, "Settings") { settingsClicked = true }
                iconAction(AllIconsKeys.Actions.Close, "Close") { closeClicked = true }
            }
        ) {
            onNodeWithContentDescription("Settings").performClick()
            onNodeWithContentDescription("Close").performClick()

            // Both buttons are clicked
            assertTrue("Settings button was not clicked", settingsClicked)
            assertTrue("Close button was not clicked", closeClicked)
        }
    }

    @Test
    fun `with very long action items hide them in the more dropdown menu`() {
        runBannerTest(
            linkActions = {
                action(
                    "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Proin at interdum " +
                        "augue. Pellentesque aliquam semper finibus. Nullam ultricies tempus felis, id " +
                        "molestie turpis interdum at. Donec non elit tristique, molestie lectus at, tincidunt " +
                        "enim. Maecenas dignissim faucibus justo, sed finibus est scelerisque sit amet. Quisque " +
                        "velit ligula, lacinia eget mi."
                ) {}
            }
        ) {
            onRoot().printToLog("foo")
            onNodeWithText("Lorem ipsum dolor sit amet,", substring = true).assertIsNotDisplayed()
            onNodeWithText("More").assertIsDisplayed()
        }
    }

    abstract fun runBannerTest(
        text: String = "Lipsum.",
        linkActions: (BannerLinkActionScope.() -> Unit)? = null,
        iconActions: (BannerIconActionScope.() -> Unit)? = null,
        block: ComposeContentTestRule.() -> Unit,
    )
}
