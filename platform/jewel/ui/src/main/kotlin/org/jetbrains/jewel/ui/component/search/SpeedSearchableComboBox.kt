// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListKey
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState
import org.jetbrains.jewel.foundation.lazy.SelectionMode
import org.jetbrains.jewel.foundation.lazy.rememberSelectableLazyListState
import org.jetbrains.jewel.foundation.lazy.tree.DefaultSelectableLazyColumnKeyActions
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.AnchorVerticalMenuPositionProvider
import org.jetbrains.jewel.ui.component.ComboBoxLabelText
import org.jetbrains.jewel.ui.component.ListComboBoxImpl
import org.jetbrains.jewel.ui.component.ProvideSearchMatchState
import org.jetbrains.jewel.ui.component.SimpleListItem
import org.jetbrains.jewel.ui.component.SpeedSearchScope
import org.jetbrains.jewel.ui.component.SpeedSearchState
import org.jetbrains.jewel.ui.component.styling.ComboBoxStyle
import org.jetbrains.jewel.ui.component.takeIfInBoundsOrZero
import org.jetbrains.jewel.ui.disabledAppearance
import org.jetbrains.jewel.ui.theme.comboBoxStyle
import org.jetbrains.jewel.ui.theme.popupContainerStyle

/**
 * A convenience overload of [SpeedSearchableComboBox] for plain [String] items.
 *
 * Renders a combo box whose popup list supports speed search: as the user types, items are matched against the query,
 * the list scrolls to the best match, and matched characters are highlighted. It must be used within a
 * [SpeedSearchScope] (typically provided by [org.jetbrains.jewel.ui.component.SpeedSearchArea]).
 *
 * Item rendering uses [SimpleListItem] with automatic search-match highlighting. For custom item rendering or
 * non-string item types, use the generic [SpeedSearchableComboBox] overload instead.
 *
 * @param items The list of strings to display in the combo box popup.
 * @param selectedIndex The index of the currently selected item.
 * @param onSelectedItemChange Called with the new index when the user selects a different item.
 * @param modifier The [Modifier] to apply to the combo box.
 * @param popupModifier The [Modifier] to apply to the popup container.
 * @param itemKeys A function that returns a stable, unique key for each item. Defaults to the item value itself.
 * @param enabled Whether the combo box is interactive.
 * @param outline The [Outline] to draw around the combo box.
 * @param maxPopupHeight The maximum height of the popup. Defaults to [Dp.Unspecified] (unconstrained).
 * @param maxPopupWidth The maximum width of the popup. Defaults to [Dp.Unspecified] (unconstrained).
 * @param style The visual style configuration for the combo box.
 * @param textStyle The text style used for both the label and popup items.
 * @param onPopupVisibleChange Called when the popup is shown or hidden.
 * @param listState The state object controlling selection and scroll position in the popup list.
 * @param dispatcher The coroutine dispatcher used for background search operations. Defaults to [Dispatchers.Default].
 */
@Composable
@ExperimentalJewelApi
@ApiStatus.Experimental
public fun SpeedSearchScope.SpeedSearchableComboBox(
    @Nls items: List<String>,
    selectedIndex: Int,
    onSelectedItemChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    popupModifier: Modifier = Modifier,
    itemKeys: (Int, String) -> Any = { _, item -> item },
    enabled: Boolean = true,
    outline: Outline = Outline.None,
    maxPopupHeight: Dp = Dp.Unspecified,
    maxPopupWidth: Dp = Dp.Unspecified,
    style: ComboBoxStyle = JewelTheme.comboBoxStyle,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    onPopupVisibleChange: (visible: Boolean) -> Unit = {},
    listState: SelectableLazyListState =
        rememberSelectableLazyListState(selectedIndex.takeIfInBoundsOrZero(items.indices)),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    SpeedSearchableComboBoxImpl(
        items = items,
        selectedIndex = selectedIndex,
        onSelectedItemChange = onSelectedItemChange,
        itemKeys = itemKeys,
        itemText = { _, item -> item },
        modifier = modifier,
        popupModifier = popupModifier,
        enabled = enabled,
        outline = outline,
        maxPopupHeight = maxPopupHeight,
        maxPopupWidth = maxPopupWidth,
        style = style,
        onPopupVisibleChange = onPopupVisibleChange,
        listState = listState,
        dispatcher = dispatcher,
        labelContent = { item -> ComboBoxLabelText(item.orEmpty(), textStyle, style, enabled) },
    ) { item, isSelected, isActive ->
        var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

        SimpleListItem(
            modifier = Modifier.thenIf(!enabled) { disabledAppearance() },
            textModifier = Modifier.highlightSpeedSearchMatches(textLayoutResult),
            text = item.highlightTextSearch(),
            selected = isSelected,
            active = isActive,
            iconContentDescription = item,
            onTextLayout = { textLayoutResult = it },
        )
    }
}

/**
 * Renders a combo box whose popup list supports speed search functionality.
 *
 * This composable combines a [org.jetbrains.jewel.ui.component.ListComboBoxImpl] with speed search capabilities,
 * providing automatic text matching, navigation between matches, and intelligent scrolling behavior. It must be used
 * within a [SpeedSearchScope] (typically provided by [org.jetbrains.jewel.ui.component.SpeedSearchArea]).
 *
 * **Key features:**
 * - **Automatic matching**: Items are automatically matched against the search query via [itemText]
 * - **Smart navigation**: Arrow keys navigate between matching items when speed search is active
 * - **Auto-scrolling**: Automatically scrolls to keep the best matching item visible
 * - **Highlight integration**: Use [ProvideSearchMatchState] (via [itemContent]) to highlight matched characters
 * - **Performance optimized**: Uses a background dispatcher for search operations
 *
 * Example usage:
 * ```kotlin
 * SpeedSearchArea {
 *     SpeedSearchableComboBox(
 *         items = myItems,
 *         selectedIndex = selectedIndex,
 *         onSelectedItemChange = { selectedIndex = it },
 *         itemKeys = { _, item -> item.id },
 *         itemText = { _, item -> item.name },
 *     ) { item, isSelected, isActive ->
 *         var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
 *         SimpleListItem(
 *             text = item.name.highlightTextSearch(),
 *             selected = isSelected,
 *             active = isActive,
 *             onTextLayout = { textLayoutResult = it },
 *             textModifier = Modifier.highlightSpeedSearchMatches(textLayoutResult),
 *         )
 *     }
 * }
 * ```
 *
 * @param items The list of items to display in the combo box popup.
 * @param selectedIndex The index of the currently selected item.
 * @param onSelectedItemChange Called with the new index when the user selects a different item.
 * @param itemKeys A function that returns a stable, unique key for each item given its index and value.
 * @param itemText A function that returns the searchable text for each item, or `null` if the item should not be
 *   matched.
 * @param modifier The [Modifier] to apply to the combo box.
 * @param popupModifier The [Modifier] to apply to the popup container.
 * @param enabled Whether the combo box is interactive.
 * @param outline The [Outline] to draw around the combo box.
 * @param maxPopupHeight The maximum height of the popup. Defaults to [Dp.Unspecified] (unconstrained).
 * @param maxPopupWidth The maximum width of the popup. Defaults to [Dp.Unspecified] (unconstrained).
 * @param style The visual style configuration for the combo box.
 * @param onPopupVisibleChange Called when the popup is shown or hidden.
 * @param listState The state object controlling selection and scroll position in the popup list.
 * @param dispatcher The coroutine dispatcher used for background search operations. Defaults to [Dispatchers.Default].
 * @param itemContent The composable used to render each popup item.
 * @see org.jetbrains.jewel.ui.component.SpeedSearchArea for the search container
 * @see highlightTextSearch for highlighting search matches in text
 * @see highlightSpeedSearchMatches for applying match highlight styles
 */
@Composable
@ExperimentalJewelApi
@ApiStatus.Experimental
public fun <T : Any> SpeedSearchScope.SpeedSearchableComboBox(
    items: List<T>,
    selectedIndex: Int,
    onSelectedItemChange: (Int) -> Unit,
    itemKeys: (Int, T) -> Any,
    itemText: (Int, T) -> String?,
    modifier: Modifier = Modifier,
    popupModifier: Modifier = Modifier,
    enabled: Boolean = true,
    outline: Outline = Outline.None,
    maxPopupHeight: Dp = Dp.Unspecified,
    maxPopupWidth: Dp = Dp.Unspecified,
    style: ComboBoxStyle = JewelTheme.comboBoxStyle,
    onPopupVisibleChange: (visible: Boolean) -> Unit = {},
    listState: SelectableLazyListState =
        rememberSelectableLazyListState(selectedIndex.takeIfInBoundsOrZero(items.indices)),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    itemContent: @Composable (item: T, isSelected: Boolean, isActive: Boolean) -> Unit,
) {
    SpeedSearchableComboBoxImpl(
        items = items,
        selectedIndex = selectedIndex,
        onSelectedItemChange = onSelectedItemChange,
        itemKeys = itemKeys,
        itemText = itemText,
        modifier = modifier,
        popupModifier = popupModifier,
        enabled = enabled,
        outline = outline,
        maxPopupHeight = maxPopupHeight,
        maxPopupWidth = maxPopupWidth,
        style = style,
        onPopupVisibleChange = onPopupVisibleChange,
        listState = listState,
        dispatcher = dispatcher,
        labelContent = { item ->
            if (item != null) {
                itemContent(item, false, false)
            }
        },
        itemContent = itemContent,
    )
}

@Composable
@ExperimentalJewelApi
@ApiStatus.Experimental
private fun <T : Any> SpeedSearchScope.SpeedSearchableComboBoxImpl(
    items: List<T>,
    selectedIndex: Int,
    onSelectedItemChange: (Int) -> Unit,
    itemKeys: (Int, T) -> Any,
    itemText: (Int, T) -> String?,
    labelContent: @Composable (item: T?) -> Unit,
    modifier: Modifier = Modifier,
    popupModifier: Modifier = Modifier,
    enabled: Boolean = true,
    outline: Outline = Outline.None,
    maxPopupHeight: Dp = Dp.Unspecified,
    maxPopupWidth: Dp = Dp.Unspecified,
    style: ComboBoxStyle = JewelTheme.comboBoxStyle,
    onPopupVisibleChange: (visible: Boolean) -> Unit = {},
    listState: SelectableLazyListState =
        rememberSelectableLazyListState(selectedIndex.takeIfInBoundsOrZero(items.indices)),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    itemContent: @Composable (item: T, isSelected: Boolean, isActive: Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val popupStyle = JewelTheme.popupContainerStyle

    val currentItems by rememberUpdatedState(items)
    val currentKeys by remember { derivedStateOf { currentItems.mapIndexed(itemKeys) } }
    val currentTexts by remember { derivedStateOf { currentItems.mapIndexed(itemText) } }

    var popupVisible by remember { mutableStateOf(false) }

    val speedSearchKeyActions = remember {
        SpeedSearchableLazyColumnKeyActions(DefaultSelectableLazyColumnKeyActions, speedSearchState)
    }

    ListComboBoxImpl(
        items = items,
        selectedIndex = selectedIndex,
        onSelectedItemChange = onSelectedItemChange,
        itemKeys = itemKeys,
        modifier =
            modifier.onPreviewKeyEvent { event ->
                if (!popupVisible) return@onPreviewKeyEvent false

                if (!processKeyEvent(event) && speedSearchState.isVisibleAndNotEmpty) {
                    val actionHandled =
                        speedSearchKeyActions
                            .handleOnKeyEvent(
                                event,
                                currentKeys.map(SelectableLazyListKey::Selectable),
                                listState,
                                SelectionMode.Single,
                            )
                            .invoke(event)
                    if (actionHandled) {
                        scope.launch { listState.lastActiveItemIndex?.let { listState.scrollToItem(it) } }
                    }
                    return@onPreviewKeyEvent actionHandled
                }

                if (event.key == Key.Spacebar) {
                    // Returning true so we don't close the popup while typing
                    return@onPreviewKeyEvent true
                }

                false
            },
        popupModifier = popupModifier,
        enabled = enabled,
        outline = outline,
        maxPopupHeight = maxPopupHeight,
        maxPopupWidth = maxPopupWidth,
        interactionSource = interactionSource,
        style = style,
        onPopupVisibleChange = { visible ->
            popupVisible = visible
            onPopupVisibleChange(visible)
            if (!visible && speedSearchState.isVisible) {
                speedSearchState.hideSearch()
            }
        },
        popupPositionProvider =
            AnchorVerticalMenuPositionProvider(
                contentOffset = popupStyle.metrics.offset,
                contentMargin = popupStyle.metrics.menuMargin,
                alignment = Alignment.Start,
                density = LocalDensity.current,
                onPopupPositionDecided = {
                    speedSearchState.position =
                        when (it) {
                            Alignment.Top -> Alignment.Bottom
                            else -> Alignment.Top
                        }
                },
            ),
        listState = listState,
        labelContent = labelContent,
        itemContent = { index, item, isSelected, isActive ->
            ProvideSearchMatchState(speedSearchState, currentTexts[index], searchMatchStyle) {
                itemContent(item, isSelected, isActive)
            }
        },
    )

    SpeedSearchableLazyColumnScrollEffect(listState, speedSearchState, currentKeys, dispatcher)

    LaunchedEffect(listState, dispatcher) {
        val entriesState = MutableStateFlow(emptyList<String?>())

        val entriesFlow = snapshotFlow { currentTexts }
        async(dispatcher) { entriesFlow.collect(entriesState::emit) }

        speedSearchState.attach(entriesState, dispatcher)
    }
}

private val SpeedSearchState.isVisibleAndNotEmpty: Boolean
    get() = isVisible && searchText.isNotEmpty()
