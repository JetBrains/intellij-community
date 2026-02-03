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
            onTextLayout = {
                @Suppress("AssignedValueIsNeverRead")
                textLayoutResult = it
            },
        )
    }
}

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
