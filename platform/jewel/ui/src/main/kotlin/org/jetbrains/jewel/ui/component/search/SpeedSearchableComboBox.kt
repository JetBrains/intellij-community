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
import org.jetbrains.jewel.ui.component.calculatePopupHeight
import org.jetbrains.jewel.ui.component.styling.ComboBoxStyle
import org.jetbrains.jewel.ui.component.takeIfInBoundsOrZero
import org.jetbrains.jewel.ui.disabledAppearance
import org.jetbrains.jewel.ui.theme.comboBoxStyle
import org.jetbrains.jewel.ui.theme.popupContainerStyle

/**
 * A combo box with integrated speed search functionality for string items.
 *
 * This composable provides a dropdown selection component with built-in speed search capabilities, allowing users to
 * quickly filter and select items by typing. Speed search is activated automatically when the user starts typing while
 * the popup is visible.
 *
 * This variant takes into account the amount of items you wish to display in the ComboBox list to calculate the popup
 * height, and is the preferred way to use `ListComboBox`. If this the height of this variant is not ideal for your use
 * case, then use variant that you can also set the `maxPopupHeight`.
 *
 * **Usage example:**
 * [`ComboBoxes.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/ComboBoxes.kt)
 *
 * **Swing equivalent:**
 * [`ComboboxSpeedSearch`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-impl/src/com/intellij/ui/ComboboxSpeedSearch.java)
 *
 * @param items The list of string items to display in the combo box.
 * @param selectedIndex The index of the currently selected item.
 * @param onSelectedItemChange Callback invoked when the selected item changes, providing the new index.
 * @param modifier The modifier to apply to the combo box.
 * @param popupModifier The modifier to apply to the popup container.
 * @param itemKeys Function to generate unique keys for each item. Defaults to using the item itself as the key.
 * @param enabled Whether the combo box is enabled for user interaction.
 * @param outline The outline style to apply to the combo box.
 * @param maxPopupWidth The maximum width for the popup. If unspecified, the popup width matches the combo box width.
 * @param style The visual style to apply to the combo box.
 * @param textStyle The text style to apply to the combo box label.
 * @param onPopupVisibleChange Callback invoked when the popup visibility changes.
 * @param listState The state object for the selectable lazy list within the popup.
 * @param dispatcher The coroutine dispatcher for speed search operations.
 */
@Composable
@ExperimentalJewelApi
@ApiStatus.Experimental
public fun SpeedSearchScope.SpeedSearchableComboBox(
    items: List<@Nls String>,
    selectedIndex: Int,
    onSelectedItemChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    popupModifier: Modifier = Modifier,
    itemKeys: (Int, String) -> Any = { _, item -> item },
    enabled: Boolean = true,
    outline: Outline = Outline.None,
    maxPopupWidth: Dp = Dp.Unspecified,
    style: ComboBoxStyle = JewelTheme.comboBoxStyle,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    onPopupVisibleChange: (visible: Boolean) -> Unit = {},
    listState: SelectableLazyListState =
        rememberSelectableLazyListState(selectedIndex.takeIfInBoundsOrZero(items.indices)),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    val maxPopupHeight =
        calculatePopupHeight(
            itemCount = items.size,
            maxPopupRowCount = style.metrics.maxPopupRowCount,
            popupContentPadding = style.metrics.popupContentPadding,
        )

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
 * A combo box with integrated speed search functionality for string items.
 *
 * This composable provides a dropdown selection component with built-in speed search capabilities, allowing users to
 * quickly filter and select items by typing. Speed search is activated automatically when the user starts typing while
 * the popup is visible.
 *
 * **Usage example:**
 * [`ComboBoxes.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/ComboBoxes.kt)
 *
 * **Swing equivalent:**
 * [`ComboboxSpeedSearch`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-impl/src/com/intellij/ui/ComboboxSpeedSearch.java)
 *
 * @param items The list of string items to display in the combo box.
 * @param selectedIndex The currently selected item index. Must be within the valid range of item indices.
 * @param onSelectedItemChange Callback invoked when the selected item changes, providing the new index.
 * @param modifier The modifier to apply to the combo box.
 * @param popupModifier The modifier to apply to the popup container.
 * @param itemKeys Function to generate unique keys for each item. Defaults to using the item itself as the key.
 * @param enabled Whether the combo box is enabled for user interaction.
 * @param outline The outline style to apply to the combo box.
 * @param maxPopupHeight The maximum height for the popup. If it's unspecified, it will allow the content to grow as
 *     * needed
 *
 * @param maxPopupWidth The maximum width for the popup. If unspecified, the popup width is determined by content.
 * @param style The visual style to apply to the combo box.
 * @param textStyle The text style to apply to the combo box label.
 * @param onPopupVisibleChange Callback invoked when the popup visibility changes.
 * @param listState The state object for the selectable lazy list within the popup.
 * @param dispatcher The coroutine dispatcher for speed search operations.
 */
@Composable
@ExperimentalJewelApi
@ApiStatus.Experimental
public fun SpeedSearchScope.SpeedSearchableComboBox(
    items: List<@Nls String>,
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
 * A combo box with integrated speed search functionality for generic items.
 *
 * This composable provides a dropdown selection component with built-in speed search capabilities, allowing users to
 * quickly filter and select items by typing. The generic type parameter allows for custom item types with full control
 * over rendering. Speed search is activated automatically when the user starts typing while the popup is visible.
 *
 * **Usage example:**
 * [`ComboBoxes.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/ComboBoxes.kt)
 *
 * **Swing equivalent:**
 * [`ComboboxSpeedSearch`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-impl/src/com/intellij/ui/ComboboxSpeedSearch.java)
 *
 * @param T The type of items in the combo box. Must be a non-nullable type.
 * @param items The list of items to display in the combo box.
 * @param selectedIndex The index of the currently selected item.
 * @param onSelectedItemChange Callback invoked when the selected item changes, providing the new index.
 * @param itemKeys Function to generate unique keys for each item based on index and item value.
 * @param itemText Function to extract searchable text from each item for speed search functionality.
 * @param modifier The modifier to apply to the combo box.
 * @param popupModifier The modifier to apply to the popup container.
 * @param enabled Whether the combo box is enabled for user interaction.
 * @param outline The outline style to apply to the combo box.
 * @param maxPopupWidth The maximum width for the popup. If unspecified, the popup width matches the combo box width.
 * @param style The visual style to apply to the combo box.
 * @param onPopupVisibleChange Callback invoked when the popup visibility changes.
 * @param listState The state object for the selectable lazy list within the popup.
 * @param dispatcher The coroutine dispatcher for speed search operations.
 * @param itemContent Composable lambda to render each item in the list, with parameters for the item, selection state,
 *   and active state.
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
    maxPopupWidth: Dp = Dp.Unspecified,
    style: ComboBoxStyle = JewelTheme.comboBoxStyle,
    onPopupVisibleChange: (visible: Boolean) -> Unit = {},
    listState: SelectableLazyListState =
        rememberSelectableLazyListState(selectedIndex.takeIfInBoundsOrZero(items.indices)),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    itemContent: @Composable (item: T, isSelected: Boolean, isActive: Boolean) -> Unit,
) {
    val maxPopupHeight =
        calculatePopupHeight(
            itemCount = items.size,
            maxPopupRowCount = style.metrics.maxPopupRowCount,
            popupContentPadding = style.metrics.popupContentPadding,
        )

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

/**
 * A combo box with integrated speed search functionality for string items.
 *
 * This composable provides a dropdown selection component with built-in speed search capabilities, allowing users to
 * quickly filter and select items by typing. Speed search is activated automatically when the user starts typing while
 * the popup is visible.
 *
 * **Usage example:**
 * [`ComboBoxes.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/ComboBoxes.kt)
 *
 * **Swing equivalent:**
 * [`ComboboxSpeedSearch`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-impl/src/com/intellij/ui/ComboboxSpeedSearch.java)
 *
 * @param items The list of string items to display in the combo box.
 * @param selectedIndex The currently selected item index. Must be within the valid range of item indices.
 * @param onSelectedItemChange Callback invoked when the selected item changes, providing the new index.
 * @param modifier The modifier to apply to the combo box.
 * @param popupModifier The modifier to apply to the popup container.
 * @param itemKeys Function to generate unique keys for each item. Defaults to using the item itself as the key.
 * @param enabled Whether the combo box is enabled for user interaction.
 * @param outline The outline style to apply to the combo box.
 * @param maxPopupHeight The maximum height for the popup. If it's unspecified, it will allow the content to grow as
 *     * needed
 *
 * @param maxPopupWidth The maximum width for the popup. If unspecified, the popup width is determined by content.
 * @param style The visual style to apply to the combo box.
 * @param onPopupVisibleChange Callback invoked when the popup visibility changes.
 * @param listState The state object for the selectable lazy list within the popup.
 * @param dispatcher The coroutine dispatcher for speed search operations.
 * @param itemContent Composable content for rendering each item in the list.
 */
@Deprecated(
    "Use the overload without maxPopupHeight parameter. The popup height is now calculated based on maxPopupRowCount in ComboBoxMetrics.",
    ReplaceWith(
        "SpeedSearchableComboBox(items, selectedIndex, onSelectedItemChange, itemKeys, itemText, modifier, popupModifier, " +
            "enabled, outline, maxPopupWidth, style, onPopupVisibleChange, listState, dispatcher, itemContent)"
    ),
)
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
