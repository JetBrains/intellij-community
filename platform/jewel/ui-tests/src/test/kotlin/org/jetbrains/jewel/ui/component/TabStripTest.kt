package org.jetbrains.jewel.ui.component

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import kotlin.math.max
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.interactions.performKeyPress
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.painter.hints.Stateful
import org.jetbrains.jewel.ui.painter.rememberResourcePainterProvider
import org.jetbrains.jewel.ui.theme.defaultTabStyle
import org.junit.Rule
import org.junit.Test

@Suppress("ImplicitUnitReturnType")
@OptIn(ExperimentalTestApi::class)
class TabStripTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `clicking on a tab selects it`() = runComposeTest {
        onNodeWithText("Test Tab 1").assertIsSelected()
        onNodeWithText("Test Tab 2").assertIsNotSelected()

        onNodeWithText("Test Tab 2").performClick()

        onNodeWithText("Test Tab 1").assertIsNotSelected()
        onNodeWithText("Test Tab 2").assertIsSelected()
    }

    @Test
    fun `on press arrow right, moves focus to next tab`() = runComposeTest {
        onNodeWithText("Test Tab 1").assertIsSelected()

        onRoot().performKeyPress(Key.DirectionRight)

        onNodeWithText("Test Tab 2").assertIsSelected()
    }

    @Test
    fun `on press arrow right, in the last tab, moves focus to the first tab`() =
        runComposeTest(initiallySelectedTabIndex = 11) {
            onNodeWithText("Test Tab 12").assertIsSelected()

            onRoot().performKeyPress(Key.DirectionRight)

            onNodeWithText("Test Tab 1").assertIsSelected()
        }

    @Test
    fun `when keeping arrow right pressed, keeps moving the selected tab`() = runComposeTest {
        onNodeWithText("Test Tab 1").assertIsSelected()

        onRoot().performKeyPress(Key.DirectionRight, duration = 3_000)

        onNodeWithText("Test Tab 5").assertIsSelected()
    }

    @Test
    fun `on press arrow left, moves focus to previous tab`() =
        runComposeTest(initiallySelectedTabIndex = 1) {
            onNodeWithText("Test Tab 2").assertIsSelected()

            onRoot().performKeyPress(Key.DirectionLeft)

            onNodeWithText("Test Tab 1").assertIsSelected()
        }

    @Test
    fun `on press arrow left, in the first tab, moves focus to the last tab`() = runComposeTest {
        onNodeWithText("Test Tab 1").assertIsSelected()

        onRoot().performKeyPress(Key.DirectionLeft)

        onNodeWithText("Test Tab 12").assertIsSelected()
    }

    @Test
    fun `on press end, moves focus to last tab`() = runComposeTest {
        onNodeWithText("Test Tab 1").assertIsSelected()

        onRoot().performKeyPress(Key.MoveEnd)

        onNodeWithText("Test Tab 12").assertIsSelected()
    }

    @Test
    fun `on press home, moves focus to first tab`() =
        runComposeTest(initiallySelectedTabIndex = 11) {
            onNodeWithText("Test Tab 12").assertIsSelected()

            onRoot().performKeyPress(Key.MoveHome)

            onNodeWithText("Test Tab 1").assertIsSelected()
        }

    @Test
    fun `on scroll to tab, set it in focus`() = runComposeTest {
        onNodeWithText("Test Tab 12").assertIsNotDisplayed()

        onRoot().performKeyPress(Key.MoveEnd)

        onNodeWithText("Test Tab 12").assertIsDisplayed()
    }

    private fun runComposeTest(
        closeable: Boolean = true,
        initiallyOpenTabs: List<Int> = (1..12).toList(),
        initiallySelectedTabIndex: Int = 0,
        block: ComposeContentTestRule.() -> Unit,
    ) {
        rule.setContent {
            val focusRequester = remember { FocusRequester() }
            var selectedTabIndex by remember { mutableIntStateOf(initiallySelectedTabIndex) }
            val tabIds = remember { initiallyOpenTabs.toMutableStateList() }

            val tabs =
                remember(tabIds, selectedTabIndex) {
                    tabIds.mapIndexed { index, id ->
                        TabData.Default(
                            selected = index == selectedTabIndex,
                            content = { tabState ->
                                val iconProvider = rememberResourcePainterProvider(AllIconsKeys.Actions.Find)
                                val icon by iconProvider.getPainter(Stateful(tabState))
                                SimpleTabContent(label = "Test Tab $id", state = tabState, icon = icon)
                            },
                            onClose = {
                                tabIds.removeAt(index)
                                if (selectedTabIndex >= index) {
                                    val maxPossibleIndex = max(0, tabIds.lastIndex)
                                    selectedTabIndex = (selectedTabIndex - 1).coerceIn(0..maxPossibleIndex)
                                }
                            },
                            onClick = { selectedTabIndex = index },
                            closable = closeable,
                        )
                    }
                }

            IntUiTheme {
                TabStrip(
                    tabs = tabs,
                    style = JewelTheme.defaultTabStyle,
                    modifier = Modifier.focusRequester(focusRequester),
                )
            }

            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        }

        rule.block()
    }
}
