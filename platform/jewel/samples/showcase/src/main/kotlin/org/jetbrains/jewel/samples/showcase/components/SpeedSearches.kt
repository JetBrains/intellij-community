@file:Suppress("UnusedImports") // Detekt false positive on the buildTree import

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.samples.showcase.components

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import org.jetbrains.annotations.Nls
import org.jetbrains.jewel.foundation.lazy.rememberSelectableLazyListState
import org.jetbrains.jewel.foundation.lazy.tree.buildTree
import org.jetbrains.jewel.foundation.lazy.tree.rememberTreeState
import org.jetbrains.jewel.foundation.search.filter as filterWithMatcher
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.util.JewelLogger
import org.jetbrains.jewel.ui.component.FilterableLazyColumn
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.InlineWarningBanner
import org.jetbrains.jewel.ui.component.SearchArea
import org.jetbrains.jewel.ui.component.SimpleListItem
import org.jetbrains.jewel.ui.component.SpeedSearchArea
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticalScrollbar
import org.jetbrains.jewel.ui.component.rememberSpeedSearchState
import org.jetbrains.jewel.ui.component.search.SpeedSearchableLazyColumn
import org.jetbrains.jewel.ui.component.search.SpeedSearchableTree
import org.jetbrains.jewel.ui.component.search.highlightSpeedSearchMatches
import org.jetbrains.jewel.ui.component.search.highlightTextSearch
import org.jetbrains.jewel.ui.theme.colorPalette

@Composable
@Suppress("Nls")
internal fun FilterAndSearch(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        InlineWarningBanner(
            text =
                "One of the samples is using the SpeedSearch feature for filtering the content. " +
                    "Despite being possible, we strongly recommend using a different component for this behavior."
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            DefaultContainer("Filter - List") { SimpleSearchAreaSample() }

            DefaultContainer("Search - List") { SpeedSearchListWithHighlighting() }

            DefaultContainer("Search - Tree") { SpeedSearchTreeExample() }

            DefaultContainer("Filter - List with Speed Search") { SpeedSearchListWithFiltering() }
        }
    }
}

@Composable
private fun RowScope.DefaultContainer(title: @Nls String, content: @Composable () -> Unit) {
    Column(
        Modifier.width(200.dp).semantics { isTraversalGroup = true },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GroupHeader(text = title, modifier = Modifier.fillMaxWidth())
        content()
    }
}

/**
 * Demonstrates speed search functionality in a tree structure.
 *
 * This example showcases how to implement speed search in a hierarchical tree component. Key concepts:
 * - **SpeedSearchArea**: Container that provides speed search functionality to its children
 * - **SpeedSearchableTree**: A tree component with built-in speed search support
 * - **Text highlighting**: Uses [highlightTextSearch] and [highlightSpeedSearchMatches] to apply visual styling to
 *   matches
 *
 * The tree displays a mock project structure (src, docs, build) and allows users to quickly navigate by typing search
 * queries. Matching nodes are highlighted in real-time.
 */
@Composable
private fun SpeedSearchTreeExample(modifier: Modifier = Modifier) {
    val treeState = rememberTreeState()
    val tree = remember {
        buildTree {
            addNode("src", "src") {
                addNode("main", "src/main") {
                    addNode("kotlin", "src/main/kotlin") {
                        addLeaf("Application.kt", "src/main/kotlin/Application.kt")
                        addLeaf("Controller.kt", "src/main/kotlin/Controller.kt")
                        addLeaf("MainViewModel.kt", "src/main/kotlin/MainViewModel.kt")
                    }
                    addNode("resources", "src/main/resources") {
                        addLeaf("application.properties", "src/main/resources/application.properties")
                        addLeaf("logback.xml", "src/main/resources/logback.xml")
                    }
                }
                addNode("test", "src/test") {
                    addNode("kotlin", "src/test/kotlin") {
                        addLeaf("ApplicationTest.kt", "src/test/kotlin/ApplicationTest.kt")
                        addLeaf("ControllerTest.kt", "src/test/kotlin/ControllerTest.kt")
                    }
                }
            }
            addNode("docs", "docs") {
                addLeaf("README.md", "docs/README.md")
                addLeaf("CONTRIBUTING.md", "docs/CONTRIBUTING.md")
                addNode("api", "docs/api") {
                    addLeaf("endpoints.md", "docs/api/endpoints.md")
                    addLeaf("authentication.md", "docs/api/authentication.md")
                }
            }
            addNode("build", "build") {
                addNode("classes", "build/classes") {
                    addLeaf("Application.class", "build/classes/Application.class")
                    addLeaf("Controller.class", "build/classes/Controller.class")
                }
                addNode("libs", "build/libs") { addLeaf("application.jar", "build/libs/application.jar") }
            }
            addLeaf(".gitignore", ".gitignore")
            addLeaf("build.gradle.kts", "build.gradle.kts")
            addLeaf("settings.gradle.kts", "settings.gradle.kts")
        }
    }

    val borderColor =
        if (JewelTheme.isDark) {
            JewelTheme.colorPalette.grayOrNull(3) ?: Color(0xFF393B40)
        } else {
            JewelTheme.colorPalette.grayOrNull(12) ?: Color(0xFFEBECF0)
        }

    SpeedSearchArea(modifier.border(1.dp, borderColor, RoundedCornerShape(2.dp))) {
        SpeedSearchableTree(
            tree = tree,
            treeState = treeState,
            modifier = Modifier.size(200.dp, 200.dp).focusable(),
            onElementClick = {},
            onElementDoubleClick = {},
            nodeText = { it.data },
        ) { element ->
            Box(Modifier.fillMaxWidth()) {
                var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                Text(
                    element.data.highlightTextSearch(),
                    Modifier.padding(2.dp).highlightSpeedSearchMatches(textLayoutResult),
                    onTextLayout = { textLayoutResult = it },
                )
            }
        }
    }
}

/**
 * Demonstrates speed search with text highlighting in a list (non-filtering approach).
 *
 * This example shows the simplest speed search implementation where all items remain visible and matching text is
 * highlighted. This is useful when you want users to see the full list context while searching.
 *
 * Key concepts:
 * - **Non-filtering behavior**: All list items remain visible regardless of search query
 * - **Automatic state management**: SpeedSearchArea automatically manages search state
 * - **Text highlighting**: Uses [highlightTextSearch] and [highlightSpeedSearchMatches] to apply visual styling to
 *   matches
 * - **Selection tracking**: Integrates with [rememberSelectableLazyListState] for item selection
 *
 * Use this approach when:
 * - Users need to see the full list context
 * - The list is small enough to scan visually
 * - You want to show "no matches" by lack of highlighting rather than empty list
 */
@Composable
private fun SpeedSearchListWithHighlighting(modifier: Modifier = Modifier) {
    val state = rememberSelectableLazyListState()

    SpeedSearchArea(modifier) {
        SpeedSearchableLazyColumn(modifier = Modifier.focusable(), state = state) {
            items(TEST_LIST, textContent = { item -> item }, key = { item -> item }) { item ->
                LaunchedEffect(isSelected) {
                    if (isSelected) {
                        JewelLogger.getInstance("SpeedSearches").info("Item $item got selected")
                    }
                }

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

        VerticalScrollbar(state.lazyListState, modifier = Modifier.align(Alignment.CenterEnd))
    }
}

/**
 * Demonstrates speed search with list filtering (items are hidden when they don't match).
 *
 * This example shows a more advanced speed search implementation where the list is dynamically filtered based on the
 * search query. Non-matching items are removed from view, providing a cleaner, more focused search experience.
 *
 * Key concepts:
 * - **Filtering behavior**: Uses [filter] extension to hide non-matching items
 * - **Explicit state management**: Uses [rememberSpeedSearchState] to manually control search state
 * - **Derived state**: [derivedStateOf] efficiently recomputes filtered list only when search changes
 * - **Matcher pattern**: SpeedSearchState provides a [currentMatcher] that implements the matching logic
 * - **Combined highlighting**: Filtered items are still highlighted to show which parts matched
 *
 * Use this approach when:
 * - Working with large lists where showing all items is impractical
 * - You want a focused view showing only relevant results
 * - The search is the primary interaction method (like command palettes or quick pickers)
 * - You need to provide feedback when no items match (empty list state)
 */
@Composable
private fun SpeedSearchListWithFiltering(modifier: Modifier = Modifier) {
    val state = rememberSelectableLazyListState()
    val speedSearchState = rememberSpeedSearchState()

    val listItems by remember {
        derivedStateOf {
            if (speedSearchState.searchText.isEmpty()) {
                TEST_LIST
            } else {
                TEST_LIST.filterWithMatcher(speedSearchState.currentMatcher)
            }
        }
    }

    SpeedSearchArea(speedSearchState, modifier) {
        SpeedSearchableLazyColumn(modifier = Modifier.focusable(), state = state) {
            items(listItems, textContent = { item -> item }, key = { item -> item }) { item ->
                LaunchedEffect(isSelected) {
                    if (isSelected) {
                        JewelLogger.getInstance("SpeedSearches").info("Item $item got selected")
                    }
                }

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

        VerticalScrollbar(state.lazyListState, modifier = Modifier.align(Alignment.CenterEnd))
    }
}

/**
 * Demonstrates a standard search area with simple text-based filtering.
 *
 * This example shows a basic search implementation using [SimpleSearchAreaSample] (which uses[SearchArea], not
 * [SpeedSearchArea]) with straightforward string-based filtering. Unlike speed search, this provides a more traditional
 * search experience without advanced matching algorithms or text highlighting.
 */
@Composable
private fun SimpleSearchAreaSample(modifier: Modifier = Modifier) {
    FilterableLazyColumn(modifier, onSelectedIndexesChange = { println("Selected index changed to $it") }) {
        items(TEST_LIST, textContent = { it }, key = { it }) { item ->
            LaunchedEffect(isSelected) {
                if (isSelected) {
                    JewelLogger.getInstance("SpeedSearches").info("Item $item got selected")
                }
            }

            SimpleListItem(
                text = item,
                selected = isSelected,
                active = isActive,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}

private val TEST_LIST =
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
