// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jetbrains.jewel.foundation.lazy.tree.buildTree
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.SpeedSearchArea
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.assertCursorAtPosition
import org.jetbrains.jewel.ui.component.interactions.performKeyPress
import org.junit.Rule

@Suppress("ImplicitUnitReturnType")
@OptIn(ExperimentalCoroutinesApi::class)
class SpeedSearchableTreeTest {
    @get:Rule val rule = createComposeRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    private val ComposeContentTestRule.onLazyTree
        get() = onNodeWithTag("LazyTree")

    private val ComposeContentTestRule.onSpeedSearchAreaInput
        get() = onNodeWithTag("SpeedSearchArea.Input")

    private fun ComposeContentTestRule.onLazyTreeNode(text: String) =
        onNode(hasAnyAncestor(hasTestTag("LazyTree")) and hasText(text))

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `should show on type text`() = runComposeTest {
        onLazyTree.performKeyPress("Root 42", rule = this)
        onSpeedSearchAreaInput.assertExists().assertIsDisplayed()
    }

    @Test
    fun `should hide on esc press`() = runComposeTest {
        onLazyTree.performKeyPress("Root 42", rule = this)
        onSpeedSearchAreaInput.assertExists().assertIsDisplayed()

        onLazyTree.performKeyPress(Key.Escape, rule = this)
        onSpeedSearchAreaInput.assertDoesNotExist()
    }

    @Test
    fun `on option navigation, move cursor`() = runComposeTest {
        onLazyTree.performKeyPress("Root 42", rule = this)
        onSpeedSearchAreaInput.assertExists().assertIsDisplayed()

        // Move to start
        onSpeedSearchAreaInput.performKeyPress(Key.DirectionLeft, alt = true, rule = this)
        onSpeedSearchAreaInput.assertCursorAtPosition(0)

        // Move to end
        onSpeedSearchAreaInput.performKeyPress(Key.DirectionRight, alt = true, rule = this)
        onSpeedSearchAreaInput.assertCursorAtPosition(7)
    }

    @Test
    fun `on type, select first occurrence`() = runComposeTest {
        onLazyTree.performKeyPress("Root 42", rule = this)
        onLazyTreeNode("Root 42").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun `on type continue typing, continue selecting first occurrence`() = runComposeTest {
        onLazyTree.performKeyPress("Root 2", rule = this)
        onLazyTreeNode("Root 2").assertIsDisplayed().assertIsSelected()

        onLazyTree.performKeyPress("4", rule = this)
        onLazyTreeNode("Root 24").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun `select first visible match if after the current item`() = runComposeTest {
        onLazyTree.performKeyPress("Root 40", rule = this)
        onLazyTreeNode("Root 40").assertIsDisplayed().assertIsSelected()

        // Return scroll a bit and ensure 39 is visible
        onLazyTree.performScrollToIndex(35)
        onLazyTreeNode("Root 39").assertIsDisplayed()

        // Delete the number and replace with "9"
        onSpeedSearchAreaInput.performKeyPress(Key.Backspace, rule = this)
        onSpeedSearchAreaInput.performKeyPress(Key.Backspace, rule = this)
        onSpeedSearchAreaInput.performKeyPress("9", rule = this)

        // 39 is the first visible item containing 9, so it will be selected
        onLazyTreeNode("Root 39").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun `select closest match if after the current item`() =
        runComposeTest(1000, 10, 10) {
            onLazyTree.performKeyPress("Root 245", rule = this)
            onLazyTreeNode("Root 245").assertIsDisplayed().assertIsSelected()

            // Delete the number
            onSpeedSearchAreaInput.performKeyPress(Key.Backspace, rule = this)
            onSpeedSearchAreaInput.performKeyPress(Key.Backspace, rule = this)
            onSpeedSearchAreaInput.performKeyPress(Key.Backspace, rule = this)

            // Add `99` and jumps to next reference matching "99"
            onSpeedSearchAreaInput.performKeyPress("99", rule = this)
            onLazyTreeNode("Root 299").assertIsDisplayed().assertIsSelected()
        }

    @Test
    fun `on arrow up or down, navigate to the next and previous occurrences`() = runComposeTest {
        onLazyTree.performKeyPress("Root 9", rule = this)
        onLazyTreeNode("Root 9").assertIsDisplayed().assertIsSelected()

        onLazyTree.performKeyPress(Key.DirectionDown, rule = this)
        onLazyTreeNode("Root 19").assertIsDisplayed().assertIsSelected()

        onLazyTree.performKeyPress(Key.DirectionDown, rule = this)
        onLazyTreeNode("Root 29").assertIsDisplayed().assertIsSelected()

        onLazyTree.performKeyPress(Key.DirectionUp, rule = this)
        onLazyTreeNode("Root 19").assertIsDisplayed().assertIsSelected()

        onLazyTree.performKeyPress(Key.DirectionUp, rule = this)
        onLazyTreeNode("Root 9").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun `deleting last char should keep current state`() = runComposeTest {
        onLazyTree.performKeyPress("Root 42", rule = this)
        onSpeedSearchAreaInput.assertExists().assertIsDisplayed()
        onLazyTreeNode("Root 42").assertIsDisplayed().assertIsSelected()

        // Remove "2" from "Root 42" to make "Root 4", but keep 42 selected as it matches the search query
        onSpeedSearchAreaInput.performKeyPress(Key.Backspace, rule = this)
        onLazyTreeNode("Root 42").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun `should handle partial text matching`() = runComposeTest {
        onLazyTree.performKeyPress("ot 1", rule = this)
        onLazyTreeNode("Root 1").assertIsDisplayed().assertIsSelected()

        // Should match "Root 10", "Root 11", etc.
        onLazyTree.performKeyPress(Key.DirectionDown, rule = this)
        onLazyTreeNode("Root 10").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun `should handle no matches gracefully`() = runComposeTest {
        // Select item 5
        onLazyTreeNode("Root 5").performClick().assertIsSelected()

        // Types something that does not match any item
        onLazyTree.performKeyPress("Nope", rule = this)
        onSpeedSearchAreaInput.assertExists().assertIsDisplayed()

        // Selection should stay in the current item
        onLazyTreeNode("Root 5").assertIsSelected()
    }

    @Test
    fun `should keep text when navigating through matches`() = runComposeTest {
        onLazyTree.performKeyPress("Root 2", rule = this)
        onLazyTreeNode("Root 2").assertIsDisplayed().assertIsSelected()

        // Navigate and check search is still active
        onLazyTree.performKeyPress(Key.DirectionDown, rule = this)
        onLazyTreeNode("Root 12").assertIsDisplayed().assertIsSelected()
        onSpeedSearchAreaInput.assertExists().assertIsDisplayed()

        onLazyTree.performKeyPress(Key.DirectionDown, rule = this)
        onLazyTreeNode("Root 20").assertIsDisplayed().assertIsSelected()
        onSpeedSearchAreaInput.assertExists().assertIsDisplayed()

        // Add more text to existing search
        onLazyTree.performKeyPress("0", rule = this)
        onLazyTreeNode("Root 20").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun `should hide speed search and expand when clicking arrow right`() = runComposeTest {
        onLazyTree.performKeyPress("Root 42", rule = this)
        onLazyTreeNode("Root 42").assertIsDisplayed().assertIsSelected()

        onSpeedSearchAreaInput.assertExists().assertIsDisplayed()
        onLazyTree.performKeyPress(Key.DirectionRight, rule = this)
        onSpeedSearchAreaInput.assertDoesNotExist()

        onLazyTreeNode("Node 42.1").assertExists()
    }

    @Test
    fun `after expanding items, speed search should work`() = runComposeTest {
        onLazyTree.performKeyPress("Root 42", rule = this)
        onLazyTreeNode("Root 42").assertIsDisplayed().performKeyPress(Key.Escape).performMouseInput { doubleClick() }
        waitForIdle()

        onLazyTree.performKeyPress("Node 42.77", rule = this)
        onLazyTreeNode("Node 42.77").assertIsDisplayed().performKeyPress(Key.Escape).performMouseInput { doubleClick() }
        waitForIdle()

        onLazyTreeNode("Subleaf 42.77.1").assertExists() // Ensure expanded all the way down
        waitForIdle()

        onLazyTree.performScrollToIndex(0) // Return to top

        onLazyTree.performKeyPress("42.77.94", rule = this)
        onLazyTreeNode("Subleaf 42.77.94").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun `on lose focus, hide input`() = runComposeTest {
        onLazyTree.performKeyPress("Root 42", rule = this)
        onSpeedSearchAreaInput.assertExists().assertIsDisplayed()

        onNodeWithTag("Button").performClick()
        onSpeedSearchAreaInput.assertDoesNotExist()
    }

    @Test
    fun `on lose focus with dismissOnLoseFocus false, keep input visible`() =
        runComposeTest(dismissOnLoseFocus = false) {
            onLazyTree.performKeyPress("Root 42", rule = this)
            onSpeedSearchAreaInput.assertExists().assertIsDisplayed()

            onNodeWithTag("Button").performClick()
            onSpeedSearchAreaInput.assertExists().assertIsDisplayed()
        }

    private fun runComposeTest(
        level1: Int = 100,
        level2: Int = 100,
        level3: Int = 100,
        dismissOnLoseFocus: Boolean = true,
        block: ComposeContentTestRule.() -> Unit,
    ) {
        val tree = buildTree {
            repeat(level1) { root ->
                addNode("Root ${root + 1}") {
                    repeat(level2) { node ->
                        addNode("Node ${root + 1}.${node + 1}") {
                            repeat(level3) { addLeaf("Subleaf ${root + 1}.${node + 1}.${it + 1}") }
                        }
                    }
                    repeat(level2) { addLeaf("Leaf ${root + 1}.${it + 1}") }
                }
            }
        }
        rule.setContent {
            val focusRequester = remember { FocusRequester() }

            IntUiTheme {
                Column {
                    SpeedSearchArea(
                        modifier = Modifier.testTag("SpeedSearchArea"),
                        dismissOnLoseFocus = dismissOnLoseFocus,
                    ) {
                        SpeedSearchableTree(
                            tree = tree,
                            modifier = Modifier.size(200.dp).testTag("LazyTree").focusRequester(focusRequester),
                            nodeText = { it.data },
                            dispatcher = testDispatcher,
                        ) {
                            Box(Modifier.fillMaxWidth()) {
                                var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                                Text(
                                    it.data.highlightTextSearch(),
                                    Modifier.padding(2.dp).highlightSpeedSearchMatches(textLayoutResult),
                                    onTextLayout = { textLayoutResult = it },
                                )
                            }
                        }
                    }

                    DefaultButton(onClick = {}, modifier = Modifier.testTag("Button")) { Text("Press me") }
                }
            }

            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        }

        rule.block()
    }
}
