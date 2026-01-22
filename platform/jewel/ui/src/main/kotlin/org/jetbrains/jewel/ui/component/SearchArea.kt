// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.styling.SearchTextFieldStyle
import org.jetbrains.jewel.ui.theme.searchTextFieldStyle

/**
 * A container with an integrated search input area above its content.
 *
 * Displays a [SearchTextField] at the top with animated visibility when search text is present, separated from content
 * by a horizontal divider. The search input automatically shows when text is entered and can be dismissed with Escape.
 *
 * @param state The state holder for the search area. Use [rememberSearchAreaState] to create one.
 * @param modifier Modifier to be applied to the outer container.
 * @param searchModifier Modifier to be applied to the search text field.
 * @param style The visual styling configuration for the search text field.
 * @param textStyle The typography style to be applied to the search input text.
 * @param interactionSource Source of interactions for the search field.
 * @param history List of previous search queries to display in the search field's history popup.
 * @param error Whether to display the search field in error state.
 * @param fallbackKeyEventHandler Fallback handler for unhandled key events from the text field.
 * @param content The content to display below the search area.
 */
@Composable
@ExperimentalJewelApi
@ApiStatus.Experimental
public fun SearchArea(
    state: SearchAreaState,
    modifier: Modifier = Modifier,
    searchModifier: Modifier = Modifier,
    style: SearchTextFieldStyle = JewelTheme.searchTextFieldStyle,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    history: List<CharSequence> = emptyList(),
    error: Boolean = false,
    fallbackKeyEventHandler: (KeyEvent) -> Boolean = { false },
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier) {
        AnimatedVisibility(state.isVisible) {
            Column {
                SearchTextField(
                    state = state.searchState,
                    style = style,
                    textStyle = textStyle,
                    history = history,
                    error = error,
                    interactionSource = interactionSource,
                    fallbackKeyEventHandler = fallbackKeyEventHandler,
                    modifier = searchModifier.fillMaxWidth(),
                )

                Divider(orientation = Orientation.Horizontal, modifier = Modifier.fillMaxWidth())
            }
        }

        content()
    }
}

/**
 * State holder for [SearchArea], managing search visibility and text input.
 *
 * Wraps [SearchTextFieldState] with automatic visibility management. The search area shows when text is present and can
 * be dismissed with Escape.
 *
 * Use [rememberSearchAreaState] to create an instance.
 */
public class SearchAreaState internal constructor(internal val searchState: SearchTextFieldState) {
    /** The underlying [TextFieldState] for direct access. */
    public val textFieldState: TextFieldState
        get() = searchState.textFieldState

    /**
     * The current search text.
     *
     * Delegates to [SearchTextFieldState].
     */
    public val searchText: String
        get() = searchState.searchText

    /**
     * Whether the search area is currently visible.
     *
     * Derived from whether search text is non-empty.
     */
    public val isVisible: Boolean by derivedStateOf { searchState.textFieldState.text.isNotEmpty() }

    /**
     * Processes keyboard events for the search area.
     *
     * Handles Escape to dismiss when visible, delegating other events to [SearchTextFieldState].
     *
     * @param event The keyboard event to process.
     * @return `true` if the event was handled, `false` otherwise.
     */
    public fun processKeyEvent(event: KeyEvent): Boolean =
        when {
            searchState.processKeyEvent(event) -> true
            isVisible && event.isDismissKeyEvent() -> {
                clearAndHide()
                true
            }

            else -> false
        }

    /**
     * Clears the search text and hides the search area.
     *
     * This method empties the text field, which causes [isVisible] to become `false` and the search area to be hidden.
     */
    public fun clearAndHide() {
        searchState.textFieldState.setTextAndPlaceCursorAtEnd("")
    }
}

/**
 * Creates and remembers a [SearchAreaState] with the provided [SearchTextFieldState].
 *
 * @param state The [SearchTextFieldState] to use. Defaults to a new remembered state.
 * @return A remembered [SearchAreaState] instance.
 */
@Composable
public fun rememberSearchAreaState(state: SearchTextFieldState = rememberSearchTextFieldState()): SearchAreaState =
    remember(state) { SearchAreaState(state) }

private fun KeyEvent.isDismissKeyEvent(): Boolean = type == KeyEventType.KeyDown && key == Key.Escape
