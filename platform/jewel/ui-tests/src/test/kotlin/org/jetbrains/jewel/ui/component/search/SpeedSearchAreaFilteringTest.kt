// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.onFirstVisible
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
import org.jetbrains.jewel.foundation.lazy.rememberSelectableLazyListState
import org.jetbrains.jewel.foundation.search.filter
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.SimpleListItem
import org.jetbrains.jewel.ui.component.SpeedSearchArea
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.interactions.performKeyPress
import org.jetbrains.jewel.ui.component.rememberSpeedSearchState
import org.junit.Rule

/**
 * Tests for [SpeedSearchArea] focusing on the filtering pattern where non-matching items are hidden from view.
 *
 * This test suite validates the behavior when using [filter] extension with [SpeedSearchState.currentMatcher] to
 * dynamically filter list items based on the search query, as demonstrated in the showcase example
 * [SpeedSearchListWithFiltering].
 *
 * Key differences from highlighting tests:
 * - **Filtering**: Items that don't match are removed from the DOM (not rendered)
 * - **Highlighting**: All items remain visible, matches are visually highlighted
 *
 * Testing approach:
 * - Uses explicit [rememberSpeedSearchState] for state management
 * - Uses [derivedStateOf] with [filter] to create filtered lists
 * - Validates items are actually removed from the component tree (not just hidden)
 * - Tests the complete user workflow: type → filter → navigate → clear
 */
@Suppress("ImplicitUnitReturnType")
@OptIn(ExperimentalCoroutinesApi::class)
class SpeedSearchAreaFilteringTest {
    @get:Rule val rule = createComposeRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    private val ComposeContentTestRule.onLazyColumn
        get() = onNodeWithTag("LazyColumn")

    private val ComposeContentTestRule.onSpeedSearchAreaInput
        get() = onNodeWithTag("SpeedSearchArea.Input")

    private fun ComposeContentTestRule.onLazyColumnItem(text: String) =
        onNode(hasAnyAncestor(hasTestTag("LazyColumn")) and hasText(text))

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `on type, filter list to show only matching items`() = runFilteringComposeTest {
        // Initially, all items should be visible
        onLazyColumnItem("Spring Boot").assertExists()
        onLazyColumnItem("React").assertExists()
        onLazyColumnItem("Django").assertExists()

        // Type "Spring" - only items containing "Spring" should be visible
        onLazyColumn.performKeyPress("Spring", rule = this)

        // These should be visible (match "Spring")
        onLazyColumnItem("Spring Boot").assertIsDisplayed()
        onLazyColumnItem("Spring Framework").assertIsDisplayed()
        onLazyColumnItem("Spring Data").assertIsDisplayed()
        onLazyColumnItem("Spring Security").assertIsDisplayed()

        // These should not exist in the tree at all (filtered out)
        onLazyColumnItem("React").assertDoesNotExist()
        onLazyColumnItem("Angular").assertDoesNotExist()
        onLazyColumnItem("Django").assertDoesNotExist()
    }

    @Test
    fun `on type more characters, further narrow filtered results`() = runFilteringComposeTest {
        // Type "S" - many items match
        onLazyColumn.performKeyPress("S", rule = this)
        onLazyColumnItem("Spring Boot").assertExists()
        onLazyColumnItem("Spring Framework").assertExists()
        onLazyColumnItem("Next.js").assertExists()

        // Type "pr" - only "Spring" items should remain
        onLazyColumn.performKeyPress("pr", rule = this)
        onLazyColumnItem("Spring Boot").assertIsDisplayed()
        onLazyColumnItem("Spring Framework").assertIsDisplayed()
        onLazyColumnItem("Next.js").assertDoesNotExist()

        // Type "ing B" - only "Spring Boot" should match
        onLazyColumn.performKeyPress("ing B", rule = this)
        onLazyColumnItem("Spring Boot").assertIsDisplayed()
        onLazyColumnItem("Spring Framework").assertDoesNotExist()
        onLazyColumnItem("Spring Data").assertDoesNotExist()
    }

    @Test
    fun `on backspace, expand filtered list`() = runFilteringComposeTest {
        // Type "Spring Boot" - only one item matches
        onLazyColumn.performKeyPress("Spring Boot", rule = this)
        onLazyColumnItem("Spring Boot").assertIsDisplayed()
        onLazyColumnItem("Spring Framework").assertDoesNotExist()

        // Delete " Boot" by pressing backspace 5 times
        repeat(5) { onSpeedSearchAreaInput.performKeyPress(Key.Backspace, rule = this) }

        // Now "Spring" matches multiple items
        onLazyColumnItem("Spring Boot").assertIsDisplayed()
        onLazyColumnItem("Spring Framework").assertIsDisplayed()
        onLazyColumnItem("Spring Data").assertIsDisplayed()
        onLazyColumnItem("Spring Security").assertIsDisplayed()
    }

    @Test
    fun `on no matches, list should be empty`() = runFilteringComposeTest {
        // Type something that doesn't match any item
        onLazyColumn.performKeyPress("NonexistentFramework", rule = this)

        // All items should be filtered out
        onLazyColumnItem("Spring Boot").assertDoesNotExist()
        onLazyColumnItem("React").assertDoesNotExist()
        onLazyColumnItem("Django").assertDoesNotExist()

        // Count all list items in the LazyColumn - should be 0
        onAllNodes(hasAnyAncestor(hasTestTag("LazyColumn"))).assertCountEquals(0)
    }

    @Test
    fun `filtered items should select first match`() = runFilteringComposeTest {
        // Type "Vue" - should select first match
        onLazyColumn.performKeyPress("Spring", rule = this)

        // "Spring Boot" should be the first match and selected
        onLazyColumnItem("Spring Boot").assertIsDisplayed().assertIsSelected()

        // Other matches should exist but not be selected
        onLazyColumnItem("Spring Framework").assertIsDisplayed()
        onLazyColumnItem("Spring Data").assertIsDisplayed()
        onLazyColumnItem("Spring Security").assertIsDisplayed()
    }

    @Test
    fun `navigation should cycle through filtered items only`() = runFilteringComposeTest {
        // Type "Angular" - filters to Angular-related items
        onLazyColumn.performKeyPress("Angular", rule = this)

        // First match should be selected
        onLazyColumnItem("Angular").assertIsDisplayed().assertIsSelected()

        // Navigate down - should go to next filtered item
        onLazyColumn.performKeyPress(Key.DirectionDown, rule = this)
        onLazyColumnItem("AngularJS").assertIsDisplayed().assertIsSelected()

        // Navigate up - should go back to Angular
        onLazyColumn.performKeyPress(Key.DirectionUp, rule = this)
        onLazyColumnItem("Angular").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun `partial match filtering should work correctly`() = runFilteringComposeTest {
        // Type "Nest" - should match NestJS
        onLazyColumn.performKeyPress("Nest", rule = this)
        onLazyColumnItem("NestJS").assertIsDisplayed()

        // Should not match Express.js (doesn't contain "Nest")
        onLazyColumnItem("Express.js").assertDoesNotExist()
    }

    @Test
    fun `case insensitive filtering should work`() = runFilteringComposeTest {
        // Type lowercase "spring"
        onLazyColumn.performKeyPress("spring", rule = this)

        // Should match items with uppercase "Spring"
        onLazyColumnItem("Spring Boot").assertIsDisplayed()
        onLazyColumnItem("Spring Framework").assertIsDisplayed()
    }

    @Test
    fun `special characters should work in filtering`() =
        runFilteringComposeTest(listEntries = listOf("React.js", "Vue.js", "Next.js", "Nuxt.js", "Express.js")) {
            // Type ".js" - all items contain ".js"
            onLazyColumn.performKeyPress(".js", rule = this)

            onLazyColumnItem("React.js").assertIsDisplayed()
            onLazyColumnItem("Vue.js").assertIsDisplayed()
            onLazyColumnItem("Next.js").assertIsDisplayed()
            onLazyColumnItem("Nuxt.js").assertIsDisplayed()
            onLazyColumnItem("Express.js").assertIsDisplayed()
        }

    @Test
    fun `adding character after match should update filter immediately`() = runFilteringComposeTest {
        // Type "Spring"
        onLazyColumn.performKeyPress("Spring", rule = this)

        // 4 Spring items should be visible
        onLazyColumnItem("Spring Boot").assertIsDisplayed()
        onLazyColumnItem("Spring Framework").assertIsDisplayed()
        onLazyColumnItem("Spring Data").assertIsDisplayed()
        onLazyColumnItem("Spring Security").assertIsDisplayed()

        // Add " F" to narrow to "Spring F"
        onLazyColumn.performKeyPress(" F", rule = this)

        // Only "Spring Framework" should remain
        onLazyColumnItem("Spring Framework").assertIsDisplayed()
        onLazyColumnItem("Spring Boot").assertDoesNotExist()
        onLazyColumnItem("Spring Data").assertDoesNotExist()
        onLazyColumnItem("Spring Security").assertDoesNotExist()
    }

    @Test
    fun `changing search should update filtered list dynamically`() = runFilteringComposeTest {
        // Type "Kotlin"
        onLazyColumn.performKeyPress("Kotlin", rule = this)
        onLazyColumnItem("Kotlinx Serialization").assertIsDisplayed()
        onLazyColumnItem("React").assertDoesNotExist()

        // Delete "Kotlin" and type "React"
        repeat(6) { onSpeedSearchAreaInput.performKeyPress(Key.Backspace, rule = this) }
        onLazyColumn.performKeyPress("React", rule = this)

        // Now React items should be visible, Kotlin items hidden
        onLazyColumnItem("React").assertIsDisplayed()
        onLazyColumnItem("React Native").assertIsDisplayed()
        onLazyColumnItem("Kotlinx Serialization").assertDoesNotExist()
    }

    @Test
    fun `on press esc, hide the speed search and list all entries`() = runFilteringComposeTest {
        // All items should be visible when start
        onLazyColumnItem("React").assertExists()
        onLazyColumnItem("Redux").assertExists()

        // Type to filter
        onLazyColumn.performKeyPress("React", rule = this)
        onLazyColumnItem("React").assertIsDisplayed()
        onLazyColumnItem("Redux").assertDoesNotExist()

        // Click button to lose focus
        onLazyColumn.performKeyPress(Key.Escape, rule = this)
        onSpeedSearchAreaInput.assertDoesNotExist()

        // All items should be visible again
        onLazyColumnItem("React").assertExists()
        onLazyColumnItem("Redux").assertExists()
    }

    @Test
    fun `empty search should show all items`() = runFilteringComposeTest {
        // Type a space (which is valid input)
        onLazyColumn.performKeyPress(" ", rule = this)

        // All items should still be visible (blank search shows all)
        onLazyColumnItem("Spring Boot").assertExists()
        onLazyColumnItem("React").assertExists()
        onLazyColumnItem("Django").assertExists()
    }

    @Test
    fun `filtering large list should perform correctly`() =
        runFilteringComposeTest(listEntries = List(500) { "Item $it" }) {
            // Type "1" - should match all items containing "1"
            onLazyColumn.performKeyPress("1", rule = this)

            // Should match items like "Item 1", "Item 10", "Item 21", etc.
            onLazyColumnItem("Item 1").assertIsDisplayed()
            onLazyColumnItem("Item 10").assertIsDisplayed()
            onLazyColumnItem("Item 11").assertIsDisplayed()
            onLazyColumnItem("Item 21").assertIsDisplayed()

            // Should not match items without "1"
            onLazyColumnItem("Item 2").assertDoesNotExist()
            onLazyColumnItem("Item 3").assertDoesNotExist()

            // Type "23" to narrow down
            onLazyColumn.performKeyPress("23", rule = this)

            // Only items containing "123" should remain
            onLazyColumnItem("Item 123").assertIsDisplayed()
            onLazyColumnItem("Item 1").assertDoesNotExist()
            onLazyColumnItem("Item 10").assertDoesNotExist()
        }

    @Test
    fun `multiple word search should filter correctly`() = runFilteringComposeTest {
        // Type "Spring Frame" - should match "Spring Framework"
        onLazyColumn.performKeyPress("Spring Frame", rule = this)

        onLazyColumnItem("Spring Framework").assertIsDisplayed()
        onLazyColumnItem("Spring Boot").assertDoesNotExist()
        onLazyColumnItem("Spring Data").assertDoesNotExist()
    }

    @Test
    fun `on lose focus, hide input and show all items`() = runFilteringComposeTest {
        // Type to filter
        onLazyColumn.performKeyPress("Spring", rule = this)
        onLazyColumnItem("Spring Boot").assertIsDisplayed()
        onLazyColumnItem("React").assertDoesNotExist()
        onSpeedSearchAreaInput.assertExists().assertIsDisplayed()

        // Click button to lose focus
        onNodeWithTag("Button").performClick()
        onSpeedSearchAreaInput.assertDoesNotExist()

        // All items should be visible again (filter cleared)
        onLazyColumnItem("Spring Boot").assertExists()
        onLazyColumnItem("React").assertExists()
        onLazyColumnItem("Django").assertExists()
    }

    @Test
    fun `on lose focus with dismissOnLoseFocus false, keep input and filtered items visible`() =
        runFilteringComposeTest(dismissOnLoseFocus = false) {
            // Type to filter
            onLazyColumn.performKeyPress("Spring", rule = this)
            onLazyColumnItem("Spring Boot").assertIsDisplayed()
            onLazyColumnItem("React").assertDoesNotExist()
            onSpeedSearchAreaInput.assertExists().assertIsDisplayed()

            // Click button to lose focus
            onNodeWithTag("Button").performClick()

            // Input should still be visible
            onSpeedSearchAreaInput.assertExists().assertIsDisplayed()

            // Filtered items should still be visible, non-matching items should not exist
            onLazyColumnItem("Spring Boot").assertIsDisplayed()
            onLazyColumnItem("Spring Framework").assertIsDisplayed()
            onLazyColumnItem("React").assertDoesNotExist()
            onLazyColumnItem("Django").assertDoesNotExist()
        }

    @Test
    fun `navigation after filter clear and dismiss should work correctly`() = runFilteringComposeTest {
        // Filter by 'RxJS' - will match only one item
        onLazyColumn.performKeyPress("RxJS", rule = this)
        onLazyColumnItem("RxJS").assertIsDisplayed()
        onLazyColumnItem("React").assertDoesNotExist()
        onLazyColumnItem("Ruby on Rails").assertDoesNotExist()

        // Delete all filter text (4 characters)
        repeat(4) { onSpeedSearchAreaInput.performKeyPress(Key.Backspace, rule = this) }
        waitForIdle()

        // Close speed search with Escape - this should restore the full list and keep RxJS selected
        onLazyColumn.performKeyPress(Key.Escape, rule = this)
        onSpeedSearchAreaInput.assertDoesNotExist()

        // RxJS should still be selected and visible
        // My fix ensures lastActiveItemIndex is updated to RxJS's position in the full list (index 10)
        onLazyColumnItem("RxJS").assertIsDisplayed().assertIsSelected()

        // Navigate down through the list - should be able to reach all items
        // RxJS is at position 10, Compose Multiplatform at 25, Ruby on Rails at 32
        // Need to press down 22 times to go from position 10 to 32
        repeat(22) { onLazyColumn.performKeyPress(Key.DirectionDown, rule = this) }

        // Should now be at "Ruby on Rails" (position 32)
        // This verifies the fix: navigation correctly continues from RxJS's true position
        onLazyColumnItem("Ruby on Rails").assertIsDisplayed().assertIsSelected()
    }

    private fun runFilteringComposeTest(
        listEntries: List<String> = TEST_FRAMEWORKS,
        dismissOnLoseFocus: Boolean = true,
        block: ComposeContentTestRule.() -> Unit,
    ) {
        rule.setContent {
            val focusRequester = remember { FocusRequester() }
            val speedSearchState = rememberSpeedSearchState()
            val state = rememberSelectableLazyListState()

            // Filter the list based on the current matcher - same pattern as showcase
            val filteredItems by remember { derivedStateOf { listEntries.filter(speedSearchState.currentMatcher) } }

            IntUiTheme {
                Column {
                    SpeedSearchArea(
                        state = speedSearchState,
                        modifier = Modifier.testTag("SpeedSearchArea"),
                        dismissOnLoseFocus = dismissOnLoseFocus,
                    ) {
                        SpeedSearchableLazyColumn(
                            modifier =
                                Modifier.size(200.dp, 400.dp)
                                    .testTag("LazyColumn")
                                    .focusRequester(focusRequester)
                                    .onFirstVisible { focusRequester.requestFocus() },
                            state = state,
                            dispatcher = testDispatcher,
                            // Don't pass dispatcher to avoid circular dependency with filtering
                        ) {
                            items(filteredItems, textContent = { it }, key = { it }) { item ->
                                var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

                                SimpleListItem(
                                    text = item.highlightTextSearch(),
                                    selected = isSelected,
                                    active = isActive,
                                    onTextLayout = { textLayoutResult = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    textModifier = Modifier.highlightSpeedSearchMatches(textLayoutResult),
                                )
                            }
                        }
                    }

                    DefaultButton(onClick = {}, modifier = Modifier.testTag("Button")) { Text("Press me") }
                }
            }
        }

        rule.waitForIdle()
        rule.block()
    }
}

private val TEST_FRAMEWORKS =
    listOf(
        "Spring Boot",
        "Spring Framework",
        "Spring Data",
        "Spring Security",
        "React",
        "React Native",
        "Redux",
        "Next.js",
        "Angular",
        "AngularJS",
        "RxJS",
        "Vue.js",
        "Vuex",
        "Nuxt.js",
        "Django",
        "Django REST Framework",
        "Flask",
        "FastAPI",
        "Express.js",
        "NestJS",
        "Koa",
        "Ktor",
        "Exposed",
        "Kotlinx Serialization",
        "Jetpack Compose",
        "Compose Multiplatform",
        "SwiftUI",
        "UIKit",
        "Combine",
        "Flutter",
        "Dart",
        "GetX",
        "Ruby on Rails",
        "Sinatra",
        "Hanami",
        "Laravel",
        "Symfony",
        "CodeIgniter",
        "ASP.NET Core",
        "Entity Framework",
        "Blazor",
        "Quarkus",
        "Micronaut",
        "Helidon",
        "Node.js",
        "Deno",
        "Bun",
    )
