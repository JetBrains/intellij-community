// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.jewel.foundation.lazy.SelectableLazyColumn
import org.jetbrains.jewel.foundation.lazy.SelectionMode
import org.jetbrains.jewel.foundation.lazy.itemsIndexed
import org.jetbrains.jewel.foundation.lazy.rememberSelectableLazyListState
import org.jetbrains.jewel.foundation.lazy.tree.NoopListKeyActions
import org.jetbrains.jewel.foundation.modifier.onMove
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.styling.IconButtonStyle
import org.jetbrains.jewel.ui.component.styling.PopupContainerStyle
import org.jetbrains.jewel.ui.component.styling.SearchTextFieldStyle
import org.jetbrains.jewel.ui.component.styling.TextFieldColors
import org.jetbrains.jewel.ui.component.styling.TextFieldMetrics
import org.jetbrains.jewel.ui.component.styling.TextFieldStyle
import org.jetbrains.jewel.ui.theme.popupContainerStyle
import org.jetbrains.jewel.ui.theme.searchTextFieldStyle
import org.jetbrains.jewel.ui.theme.textFieldStyle
import org.jetbrains.jewel.ui.util.handleKeyEvent

/**
 * A text field specifically designed for search input with integrated history popup support.
 *
 * Provides a search input field with visual indicators (magnifying glass icon), optional search history accessible via
 * Alt+Down keyboard shortcut, and an optional clear button. The search icon changes to a history icon when history is
 * available, and supports keyboard navigation through history items.
 *
 * @param state The state holder for the search text field. Use [rememberSearchTextFieldState] to create one.
 * @param modifier Modifier to be applied to the text field.
 * @param enabled Controls whether the text field can be interacted with.
 * @param readOnly Controls whether the text can be modified.
 * @param inputTransformation Transforms text input before it appears in the field.
 * @param textStyle The typography style to be applied to the text.
 * @param keyboardOptions Options controlling keyboard input behavior.
 * @param onKeyboardAction Handler for keyboard actions.
 * @param onTextLayout Callback for text layout changes.
 * @param interactionSource Source of interactions for this text field.
 * @param style The visual styling configuration for the search text field.
 * @param textFieldStyle The base text field style to be merged with search-specific styling.
 * @param outline The outline style to be applied to the text field.
 * @param placeholder Content to display when the text field is empty. Defaults to "Search" text.
 * @param outputTransformation Transforms text output for display.
 * @param undecorated Whether to show the text field without decorations.
 * @param popupStyle The visual styling configuration for the history popup container.
 * @param history List of previous search queries to display in the history popup. When non-empty, the search icon
 *   becomes interactive and shows a history icon.
 * @param error Whether to display the field in error state.
 * @param allowClear Whether to show the clear button when text is present.
 * @param fallbackKeyEventHandler Fallback handler for unhandled key events. Useful when the event triggers in the text
 *   field and not in the child component from SearchArea or SpeedSearchArea, allowing handling of navigation keys that
 *   can be used for both the input and child component (e.g., arrow up/down for history or items).
 * @see com.intellij.ui.SearchTextField
 */
@Composable
public fun SearchTextField(
    state: SearchTextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    inputTransformation: InputTransformation? = null,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    onTextLayout: (Density.(getResult: () -> TextLayoutResult?) -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: SearchTextFieldStyle = JewelTheme.searchTextFieldStyle,
    textFieldStyle: TextFieldStyle = JewelTheme.textFieldStyle,
    outline: Outline = Outline.None,
    placeholder: @Composable (() -> Unit)? = { Text("Search") },
    outputTransformation: OutputTransformation? = null,
    undecorated: Boolean = true,
    popupStyle: PopupContainerStyle = JewelTheme.popupContainerStyle,
    history: List<CharSequence> = emptyList(),
    error: Boolean = false,
    allowClear: Boolean = true,
    fallbackKeyEventHandler: (KeyEvent) -> Boolean = { false },
) {
    val density = LocalDensity.current
    state.updateHistoryEntries(history)

    val finalTextFieldStyle = remember(style, error) { textFieldStyle.withSearchInputStyle(style, error) }
    var fieldSize by remember { mutableStateOf(finalTextFieldStyle.metrics.minSize) }

    val currentFallbackKeyEventHandler by rememberUpdatedState(fallbackKeyEventHandler)

    TextField(
        state = state.textFieldState,
        enabled = enabled,
        readOnly = readOnly,
        inputTransformation = inputTransformation,
        textStyle = textStyle,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        onTextLayout = onTextLayout,
        style = finalTextFieldStyle,
        outline = outline,
        outputTransformation = outputTransformation,
        undecorated = undecorated,
        placeholder = placeholder,
        leadingIcon = { SearchIcon(state, style, textFieldStyle.iconButtonStyle, fieldSize, history, popupStyle) },
        trailingIcon = { ClearIconButton(state, style, textFieldStyle.iconButtonStyle, allowClear) },
        interactionSource = interactionSource,
        modifier =
            Modifier.onGloballyPositioned { fieldSize = with(density) { it.size.toSize().toDpSize() } }
                .onPreviewKeyEvent { state.processKeyEventInternal(it) || currentFallbackKeyEventHandler(it) }
                .then(modifier)
                .testTag("Jewel.Search.Input"),
    )
}

/**
 * State holder for [SearchTextField], managing text input and history popup visibility.
 *
 * Use [rememberSearchTextFieldState] to create an instance of this class.
 *
 * @property textFieldState The underlying [TextFieldState] that manages the text input.
 */
public class SearchTextFieldState internal constructor(public val textFieldState: TextFieldState) {
    /**
     * The current search text.
     *
     * Derived from [textFieldState].
     */
    public val searchText: String by derivedStateOf { textFieldState.text.toString() }

    internal var searchHistory by mutableStateOf(emptyList<CharSequence>())
    internal var searchHistoryHoveredIndex: Int by mutableIntStateOf(-1)
    internal val isSearchHistoryVisible: Boolean by derivedStateOf { searchHistoryHoveredIndex >= 0 }

    /**
     * Controls the visibility of the search history popup.
     *
     * When shown, the first history item is automatically selected.
     *
     * @param show Whether to show the history popup.
     */
    public fun setShowSearchHistory(show: Boolean) {
        searchHistoryHoveredIndex = if (show) 0 else -1
    }

    /**
     * Processes keyboard events for history popup navigation and text field interactions.
     *
     * Handles arrow keys, Enter, and Escape for history popup navigation, delegating other events to the underlying
     * text field state.
     *
     * @param event The keyboard event to process.
     * @return `true` if the event was handled, `false` otherwise.
     */
    public fun processKeyEvent(event: KeyEvent): Boolean =
        processKeyEventInternal(
            event = event,
            handleInTextFieldState = { textFieldState.handleKeyEvent(event = event) },
        )

    internal fun updateHistoryEntries(value: List<CharSequence>) {
        Snapshot.withMutableSnapshot {
            searchHistory = value

            if (searchHistoryHoveredIndex > value.lastIndex) {
                searchHistoryHoveredIndex = value.lastIndex
            }
        }
    }

    internal fun processKeyEventInternal(event: KeyEvent, handleInTextFieldState: () -> Boolean = { false }): Boolean =
        when {
            event.type != KeyEventType.KeyDown -> handleInTextFieldState()

            !isSearchHistoryVisible && event.isShowHistoryEvent() -> {
                setShowSearchHistory(true)
                true
            }

            event.key == Key.DirectionUp -> {
                if (isSearchHistoryVisible && searchHistoryHoveredIndex > 0) {
                    searchHistoryHoveredIndex--
                    true
                } else {
                    false
                }
            }

            event.key == Key.DirectionDown -> {
                if (isSearchHistoryVisible && searchHistoryHoveredIndex < searchHistory.lastIndex) {
                    searchHistoryHoveredIndex++
                    true
                } else {
                    false
                }
            }

            isSearchHistoryVisible && event.key == Key.Escape -> {
                setShowSearchHistory(false)
                true
            }

            isSearchHistoryVisible && event.key == Key.Enter -> {
                val selectedItem = searchHistory.getOrNull(searchHistoryHoveredIndex)
                if (selectedItem != null) {
                    setShowSearchHistory(false)
                    textFieldState.setTextAndPlaceCursorAtEnd(selectedItem.toString())
                    true
                } else {
                    false
                }
            }

            else -> handleInTextFieldState()
        }
}

/**
 * Creates and remembers a [SearchTextFieldState] with the provided [TextFieldState].
 *
 * Use this overload to share the [TextFieldState] with other components or for greater control over state
 * initialization.
 *
 * @param textFieldState The [TextFieldState] to use. Defaults to a new empty state.
 * @return A remembered [SearchTextFieldState] instance.
 */
@Composable
public fun rememberSearchTextFieldState(
    textFieldState: TextFieldState = rememberTextFieldState()
): SearchTextFieldState = remember { SearchTextFieldState(textFieldState) }

/**
 * Creates and remembers a [SearchTextFieldState] with initial text and selection.
 *
 * Use this overload for simple cases where you need to initialize the search field with text.
 *
 * @param initialText The initial text to display. Defaults to empty.
 * @param initialSelection The initial text selection range. Defaults to cursor at end.
 * @return A remembered [SearchTextFieldState] instance.
 */
@Composable
public fun rememberSearchTextFieldState(
    initialText: String = "",
    initialSelection: TextRange = TextRange(initialText.length),
): SearchTextFieldState = remember { SearchTextFieldState(TextFieldState(initialText, initialSelection)) }

@Composable
private fun SearchIcon(
    state: SearchTextFieldState,
    style: SearchTextFieldStyle,
    iconButtonStyle: IconButtonStyle,
    fieldSize: DpSize,
    history: List<CharSequence>,
    popupStyle: PopupContainerStyle,
) {
    AnimatedContent(
        history.isNotEmpty(),
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        modifier = Modifier.padding(style.metrics.contentPadding),
    ) { hasHistory ->
        if (hasHistory) {
            IconButton(
                onClick = { state.setShowSearchHistory(true) },
                style = iconButtonStyle,
                modifier = Modifier.testTag("Jewel.Search.SearchHistory").padding(end = style.metrics.spaceBetweenIcons),
            ) {
                Icon(key = style.icons.searchHistoryIcon, contentDescription = "Search History")
            }
        } else {
            Icon(
                key = style.icons.searchIcon,
                contentDescription = null,
                modifier = Modifier.padding(end = style.metrics.spaceBetweenIcons),
            )
        }
    }

    if (state.isSearchHistoryVisible) {
        PopupContent(state = state, items = history, fieldSize = fieldSize, style = style, popupStyle = popupStyle)
    }
}

@Composable
private fun ClearIconButton(
    state: SearchTextFieldState,
    style: SearchTextFieldStyle,
    iconButtonStyle: IconButtonStyle,
    allowClear: Boolean,
) {
    AnimatedVisibility(allowClear && state.textFieldState.text.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
        IconButton(
            onClick = { state.textFieldState.setTextAndPlaceCursorAtEnd("") },
            style = iconButtonStyle,
            modifier = Modifier.testTag("Jewel.Search.Clear").padding(start = style.metrics.spaceBetweenIcons),
        ) {
            Icon(key = style.icons.clearIcon, contentDescription = "Clear")
        }
    }
}

private fun TextFieldStyle.withSearchInputStyle(
    searchInputStyle: SearchTextFieldStyle,
    error: Boolean,
): TextFieldStyle =
    TextFieldStyle(
        colors =
            colors.withSearchInputStyle(
                if (error) searchInputStyle.colors.error else searchInputStyle.colors.foreground
            ),
        metrics = metrics.withSearchInputStyle(searchInputStyle.metrics.contentPadding),
        iconButtonStyle = iconButtonStyle,
    )

private fun TextFieldColors.withSearchInputStyle(foreground: Color): TextFieldColors =
    if (foreground.isUnspecified || (content == foreground && caret == foreground)) {
        this
    } else {
        TextFieldColors(
            background = background,
            backgroundDisabled = backgroundDisabled,
            backgroundFocused = backgroundFocused,
            backgroundPressed = backgroundPressed,
            backgroundHovered = backgroundHovered,
            content = foreground.takeOrElse { content },
            contentDisabled = foreground.takeOrElse { contentDisabled },
            contentFocused = foreground.takeOrElse { contentFocused },
            contentPressed = foreground.takeOrElse { contentPressed },
            contentHovered = foreground.takeOrElse { contentHovered },
            border = border,
            borderDisabled = borderDisabled,
            borderFocused = borderFocused,
            borderPressed = borderPressed,
            borderHovered = borderHovered,
            caret = foreground.takeOrElse { caret },
            caretDisabled = foreground.takeOrElse { caretDisabled },
            caretFocused = foreground.takeOrElse { caretFocused },
            caretPressed = foreground.takeOrElse { caretPressed },
            caretHovered = foreground.takeOrElse { caretHovered },
            placeholder = placeholder,
        )
    }

private fun TextFieldMetrics.withSearchInputStyle(contentPadding: PaddingValues): TextFieldMetrics =
    if (this.contentPadding == contentPadding) {
        this
    } else {
        TextFieldMetrics(
            borderWidth = borderWidth,
            contentPadding = contentPadding,
            cornerSize = cornerSize,
            minSize = minSize,
        )
    }

private fun KeyEvent.isShowHistoryEvent() = type == KeyEventType.KeyDown && key == Key.DirectionDown && isAltPressed

@Composable
private fun PopupContent(
    state: SearchTextFieldState,
    items: List<CharSequence>,
    fieldSize: DpSize,
    style: SearchTextFieldStyle,
    popupStyle: PopupContainerStyle,
) {
    val listState = rememberSelectableLazyListState(0)

    val contentPadding = style.metrics.popupContentPadding

    PopupContainer(
        style = popupStyle,
        onDismissRequest = { state.setShowSearchHistory(false) },
        modifier = Modifier.testTag("Jewel.SearchInput.HistoryPopup"),
        horizontalAlignment = Alignment.Start,
        popupProperties = PopupProperties(focusable = false),
    ) {
        VerticallyScrollableContainer(scrollState = listState.lazyListState as ScrollableState) {
            SelectableLazyColumn(
                modifier =
                    Modifier.heightIn(
                            min =
                                fieldSize.height +
                                    contentPadding.calculateTopPadding() +
                                    contentPadding.calculateBottomPadding()
                        )
                        .widthIn(max = fieldSize.width)
                        .fillMaxWidth()
                        .testTag("Jewel.SearchInput.HistoryPopup.List"),
                contentPadding = contentPadding,
                selectionMode = SelectionMode.Single,
                state = listState,
                onSelectedIndexesChange = { selectedItemsIndexes ->
                    val selectedIndex = selectedItemsIndexes.firstOrNull()
                    if (selectedIndex != null) {
                        state.textFieldState.setTextAndPlaceCursorAtEnd(items[selectedIndex].toString())
                        state.setShowSearchHistory(false)
                    }
                },
                // Disable inner list keyboard navigation while popup is visible; navigation is handled at ComboBox
                // level
                keyActions = NoopListKeyActions,
            ) {
                itemsIndexed(
                    items = items,
                    key = { _, item -> item },
                    itemContent = { index, item ->
                        Box(
                            modifier =
                                Modifier.thenIf(!listState.isScrollInProgress) {
                                    onMove {
                                        if (state.searchHistoryHoveredIndex != index) {
                                            state.searchHistoryHoveredIndex = index
                                        }
                                    }
                                }
                        ) {
                            // Items can be "actually" selected, or "preview" selected (e.g.,
                            // hovered), but if we have a "preview" selection, we hide the "actual" selection
                            val isItemSelected = listState.selectedKeys.contains(item)
                            val showAsSelected =
                                (isItemSelected && state.searchHistoryHoveredIndex < 0) ||
                                    state.searchHistoryHoveredIndex == index

                            SimpleListItem(item.toString(), selected = showAsSelected)
                        }
                    },
                )
            }
        }
    }
}
