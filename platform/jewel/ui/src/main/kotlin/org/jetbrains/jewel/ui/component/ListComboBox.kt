package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.takeOrElse
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.lazy.SelectableLazyColumn
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState
import org.jetbrains.jewel.foundation.lazy.SelectionMode
import org.jetbrains.jewel.foundation.lazy.itemsIndexed
import org.jetbrains.jewel.foundation.lazy.rememberSelectableLazyListState
import org.jetbrains.jewel.foundation.lazy.visibleItemsRange
import org.jetbrains.jewel.foundation.modifier.onMove
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.util.JewelLogger
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.styling.ComboBoxStyle
import org.jetbrains.jewel.ui.disabledAppearance
import org.jetbrains.jewel.ui.theme.comboBoxStyle

/**
 * A non-editable dropdown list component that follows the standard visual styling.
 *
 * Provides a selectable list of items in a dropdown format. When clicked, displays a popup with the list of items.
 * Supports keyboard navigation, item selection, and custom item rendering. The selected item is displayed in the main
 * control.
 *
 * **Guidelines:** [on IJP SDK webhelp](https://plugins.jetbrains.com/docs/intellij/drop-down.html)
 *
 * **Usage example:**
 * [`ComboBoxes.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/ComboBoxes.kt)
 *
 * **Swing equivalent:**
 * [`ComboBox`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/openapi/ui/ComboBox.java)
 *
 * @param items The list of items to display in the dropdown
 * @param selectedIndex The index of the currently selected item
 * @param onSelectedItemChange Called when an item is selected, with the new index
 * @param itemKeys Function to generate unique keys for items; defaults to using the item itself as the key
 * @param modifier Modifier to be applied to the combo box
 * @param enabled Controls whether the combo box can be interacted with
 * @param outline The outline style to be applied to the combo box
 * @param maxPopupHeight The maximum height of the popup list
 * @param interactionSource Source of interactions for this combo box
 * @param style The visual styling configuration for the combo box
 * @param onPopupVisibleChange Called when the popup visibility changes
 * @param listState The State object for the selectable lazy list in the popup
 * @param itemContent Composable content for rendering each item in the list
 * @see com.intellij.openapi.ui.ComboBox
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
@Composable
public fun <T : Any> ListComboBox(
    items: List<T>,
    selectedIndex: Int,
    onSelectedItemChange: (Int) -> Unit,
    itemKeys: (Int, T) -> Any,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outline: Outline = Outline.None,
    maxPopupHeight: Dp = Dp.Unspecified,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: ComboBoxStyle = JewelTheme.comboBoxStyle,
    onPopupVisibleChange: (visible: Boolean) -> Unit = {},
    listState: SelectableLazyListState = rememberSelectableLazyListState(),
    itemContent: @Composable (item: T, isSelected: Boolean, isActive: Boolean) -> Unit,
) {
    LaunchedEffect(Unit) { listState.selectedKeys = setOf(itemKeys(selectedIndex, items[selectedIndex])) }

    var previewSelectedIndex by remember { mutableIntStateOf(selectedIndex) }
    val scope = rememberCoroutineScope()

    fun setSelectedItem(index: Int) {
        if (index >= 0 && index <= items.lastIndex) {
            listState.selectedKeys = setOf(itemKeys(index, items[index]))
            onSelectedItemChange(index)
            scope.launch { listState.lazyListState.scrollToIndex(index) }
        } else {
            JewelLogger.getInstance("ListComboBox").trace("Ignoring item index $index as it's invalid")
        }
    }

    fun resetPreviewSelectedIndex() {
        previewSelectedIndex = -1
    }

    val contentPadding = style.metrics.popupContentPadding
    val popupMaxHeight = maxPopupHeight.takeOrElse { style.metrics.maxPopupHeight }

    val popupManager = remember {
        PopupManager(
            onPopupVisibleChange = { visible ->
                resetPreviewSelectedIndex()
                onPopupVisibleChange(visible)
            },
            name = "ListComboBoxPopup",
        )
    }

    ComboBox(
        modifier =
            modifier.onPreviewKeyEvent {
                if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                if (it.key == Key.Enter || it.key == Key.NumPadEnter) {
                    if (popupManager.isPopupVisible.value && previewSelectedIndex >= 0) {
                        setSelectedItem(previewSelectedIndex)
                        resetPreviewSelectedIndex()
                    }
                    popupManager.setPopupVisible(false)
                    true
                } else {
                    false
                }
            },
        enabled = enabled,
        maxPopupHeight = popupMaxHeight,
        onArrowDownPress = {
            var currentSelectedIndex = listState.selectedItemIndex(items, itemKeys)

            // When there is a preview-selected item, pressing down will actually change the
            // selected value to the one underneath it (unless it's the last one)
            if (previewSelectedIndex >= 0 && previewSelectedIndex < items.lastIndex) {
                currentSelectedIndex = previewSelectedIndex
                resetPreviewSelectedIndex()
            }

            setSelectedItem((currentSelectedIndex + 1).coerceAtMost(items.lastIndex))
        },
        onArrowUpPress = {
            var currentSelectedIndex = listState.selectedItemIndex(items, itemKeys)

            // When there is a preview-selected item, pressing up will actually change the
            // selected value to the one above it (unless it's the first one)
            if (previewSelectedIndex > 0) {
                currentSelectedIndex = previewSelectedIndex
                resetPreviewSelectedIndex()
            }

            setSelectedItem((currentSelectedIndex - 1).coerceAtLeast(0))
        },
        style = style,
        interactionSource = interactionSource,
        outline = outline,
        popupManager = popupManager,
        labelContent = {
            // We draw label items as not selected and not active
            itemContent(items[selectedIndex], false, false)
        },
    ) {
        PopupContent(
            items = items,
            previewSelectedItemIndex = previewSelectedIndex,
            listState = listState,
            popupMaxHeight = popupMaxHeight,
            contentPadding = contentPadding,
            onPreviewSelectedItemChange = {
                if (it >= 0 && previewSelectedIndex != it) {
                    previewSelectedIndex = it
                }
            },
            onSelectedItemChange = { index: Int -> setSelectedItem(index) },
            itemKeys = itemKeys,
            itemContent = itemContent,
        )
    }
}

/**
 * A non-editable dropdown list component that follows the standard visual styling.
 *
 * Provides a selectable list of items in a dropdown format. When clicked, displays a popup with the list of items.
 * Supports keyboard navigation, item selection, and custom item rendering. The selected item is displayed in the main
 * control.
 *
 * **Guidelines:** [on IJP SDK webhelp](https://plugins.jetbrains.com/docs/intellij/drop-down.html)
 *
 * **Usage example:**
 * [`ComboBoxes.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/ComboBoxes.kt)
 *
 * **Swing equivalent:**
 * [`ComboBox`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/openapi/ui/ComboBox.java)
 *
 * @param items The list of items to display in the dropdown
 * @param selectedIndex The index of the currently selected item
 * @param onSelectedItemChange Called when an item is selected, with the new index
 * @param modifier Modifier to be applied to the combo box
 * @param enabled Controls whether the combo box can be interacted with
 * @param outline The outline style to be applied to the combo box
 * @param maxPopupHeight The maximum height of the popup list
 * @param interactionSource Source of interactions for this combo box
 * @param style The visual styling configuration for the combo box
 * @param textStyle The typography style to be applied to the items
 * @param onPopupVisibleChange Called when the popup visibility changes
 * @param itemKeys Function to generate unique keys for items; defaults to using the item itself as the key
 * @param listState The State object for the selectable lazy list in the popup
 * @see com.intellij.openapi.ui.ComboBox
 */
@Composable
public fun ListComboBox(
    items: List<String>,
    selectedIndex: Int,
    onSelectedItemChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outline: Outline = Outline.None,
    maxPopupHeight: Dp = Dp.Unspecified,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: ComboBoxStyle = JewelTheme.comboBoxStyle,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    onPopupVisibleChange: (visible: Boolean) -> Unit = {},
    itemKeys: (Int, String) -> Any = { _, item -> item },
    listState: SelectableLazyListState = rememberSelectableLazyListState(),
) {
    var labelText by remember { mutableStateOf(items[selectedIndex]) }
    var previewSelectedIndex by remember { mutableIntStateOf(-1) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        // Select the first item in the list when creating
        listState.selectedKeys = setOf(itemKeys(selectedIndex, items[selectedIndex]))
    }

    fun setSelectedItem(index: Int) {
        if (index >= 0 && index <= items.lastIndex) {
            listState.selectedKeys = setOf(itemKeys(index, items[index]))
            labelText = items[index]
            onSelectedItemChange(index)
            scope.launch { listState.lazyListState.scrollToIndex(index) }
        } else {
            JewelLogger.getInstance("ListComboBox").trace("Ignoring item index $index as it's invalid")
        }
    }

    val contentPadding = style.metrics.popupContentPadding
    val popupMaxHeight = maxPopupHeight.takeOrElse { style.metrics.maxPopupHeight }

    val popupManager = remember {
        PopupManager(
            onPopupVisibleChange = { visible ->
                previewSelectedIndex = -1
                onPopupVisibleChange(visible)
            },
            name = "ListComboBoxPopup",
        )
    }

    ComboBox(
        modifier =
            modifier.onPreviewKeyEvent {
                if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                if (it.key == Key.Enter || it.key == Key.NumPadEnter) {
                    if (popupManager.isPopupVisible.value && previewSelectedIndex >= 0) {
                        setSelectedItem(previewSelectedIndex)
                        previewSelectedIndex = -1
                        popupManager.setPopupVisible(false)
                    }
                    true
                } else {
                    false
                }
            },
        enabled = enabled,
        labelText = labelText,
        maxPopupHeight = popupMaxHeight,
        onArrowDownPress = {
            var currentSelectedIndex = listState.selectedItemIndex(items, itemKeys)

            // When there is a preview-selected item, pressing down will actually change the
            // selected value to the one underneath it (unless it's the last one)
            if (previewSelectedIndex >= 0 && previewSelectedIndex < items.lastIndex) {
                currentSelectedIndex = previewSelectedIndex
                previewSelectedIndex = -1
            }

            setSelectedItem((currentSelectedIndex + 1).coerceAtMost(items.lastIndex))
        },
        onArrowUpPress = {
            var currentSelectedIndex = listState.selectedItemIndex(items, itemKeys)

            // When there is a preview-selected item, pressing up will actually change the
            // selected value to the one above it (unless it's the first one)
            if (previewSelectedIndex > 0) {
                currentSelectedIndex = previewSelectedIndex
                previewSelectedIndex = -1
            }

            setSelectedItem((currentSelectedIndex - 1).coerceAtLeast(0))
        },
        style = style,
        textStyle = textStyle,
        interactionSource = interactionSource,
        outline = outline,
        popupManager = popupManager,
    ) {
        PopupContent(
            items = items,
            previewSelectedItemIndex = previewSelectedIndex,
            listState = listState,
            popupMaxHeight = popupMaxHeight,
            contentPadding = contentPadding,
            onPreviewSelectedItemChange = {
                if (it >= 0 && previewSelectedIndex != it) {
                    previewSelectedIndex = it
                }
            },
            onSelectedItemChange = ::setSelectedItem,
            itemKeys = itemKeys,
            itemContent = { item, isSelected, isActive ->
                SimpleListItem(
                    modifier = Modifier.thenIf(!enabled) { disabledAppearance() },
                    text = item,
                    selected = isSelected,
                    active = isActive,
                    iconContentDescription = item,
                )
            },
        )
    }
}

/**
 * An editable dropdown list component that follows the standard visual styling.
 *
 * Provides a text field with a dropdown list of suggestions. Users can either select from the list or type their own
 * value. Supports keyboard navigation, item selection, and custom item rendering. The selected or entered text is
 * displayed in the editable text field.
 *
 * **Guidelines:** [on IJP SDK webhelp](https://plugins.jetbrains.com/docs/intellij/drop-down.html)
 *
 * **Usage example:**
 * [`ComboBoxes.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/ComboBoxes.kt)
 *
 * **Swing equivalent:**
 * [`ComboBox`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/openapi/ui/ComboBox.java)
 * with [setEditable(true)](https://docs.oracle.com/javase/8/docs/api/javax/swing/JComboBox.html#setEditable-boolean-)
 *
 * @param items The list of items to display in the dropdown
 * @param selectedIndex The index of the currently selected item
 * @param onSelectedItemChange Called when the selected item changes, with the new index and item
 * @param modifier Modifier to be applied to the combo box
 * @param enabled Controls whether the combo box can be interacted with
 * @param outline The outline style to be applied to the combo box
 * @param maxPopupHeight The maximum height of the popup list
 * @param interactionSource Source of interactions for this combo box
 * @param style The visual styling configuration for the combo box
 * @param textStyle The typography style to be applied to the items
 * @param onPopupVisibleChange Called when the popup visibility changes
 * @param itemKeys Function to generate unique keys for items; defaults to using the item itself as the key
 * @param listState The State object for the selectable lazy list in the popup
 * @see com.intellij.openapi.ui.ComboBox
 */
@Composable
public fun EditableListComboBox(
    items: List<String>,
    selectedIndex: Int,
    onSelectedItemChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outline: Outline = Outline.None,
    maxPopupHeight: Dp = Dp.Unspecified,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: ComboBoxStyle = JewelTheme.comboBoxStyle,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    onPopupVisibleChange: (visible: Boolean) -> Unit = {},
    itemKeys: (Int, String) -> Any = { _, item -> item },
    listState: SelectableLazyListState = rememberSelectableLazyListState(),
) {
    val textFieldState = rememberTextFieldState(items[selectedIndex])
    var previewSelectedIndex by remember { mutableIntStateOf(-1) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        // Select the first item in the list when creating
        listState.selectedKeys = setOf(itemKeys(selectedIndex, items[selectedIndex]))
    }

    fun setSelectedItem(index: Int) {
        if (index >= 0 && index <= items.lastIndex) {
            // Note: it's important to do the edit _before_ updating the list state,
            // since updating the list state will cause another, asynchronous and
            // potentially nested call to edit, which is not supported.
            // This is because setting the selected keys on the SLC will eventually
            // cause a call to this very function via SLC's onSelectedIndexesChange.
            textFieldState.edit { replace(0, length, items[index]) }

            if (listState.selectedKeys.size != 1 || itemKeys(index, items[index]) !in listState.selectedKeys) {
                // This guard condition should also help avoid issues caused by side effects
                // of setting new selected keys, as per the comment above.
                listState.selectedKeys = setOf(itemKeys(index, items[index]))
            }
            onSelectedItemChange(index)
            scope.launch { listState.lazyListState.scrollToIndex(index) }
        } else {
            listState.selectedKeys = emptySet()
        }
    }

    val contentPadding = style.metrics.popupContentPadding
    val popupMaxHeight = maxPopupHeight.takeOrElse { style.metrics.maxPopupHeight }

    EditableComboBox(
        textFieldState = textFieldState,
        modifier = modifier,
        enabled = enabled,
        outline = outline,
        interactionSource = interactionSource,
        style = style,
        textStyle = textStyle,
        onArrowDownPress = {
            var currentSelectedIndex = listState.selectedItemIndex(items, itemKeys)

            // When there is a preview-selected item, pressing down will actually change the
            // selected value to the one underneath it (unless it's the last one)
            if (previewSelectedIndex >= 0 && previewSelectedIndex < items.lastIndex) {
                currentSelectedIndex = previewSelectedIndex
                previewSelectedIndex = -1
            }

            setSelectedItem((currentSelectedIndex + 1).coerceAtMost(items.lastIndex))
        },
        onArrowUpPress = {
            var currentSelectedIndex = listState.selectedItemIndex(items, itemKeys)

            // When there is a preview-selected item, pressing up will actually change the
            // selected value to the one above it (unless it's the first one)
            if (previewSelectedIndex > 0) {
                currentSelectedIndex = previewSelectedIndex
                previewSelectedIndex = -1
            }

            setSelectedItem((currentSelectedIndex - 1).coerceAtLeast(0))
        },
        onEnterPress = {
            val indexOfSelected = items.indexOf(textFieldState.text)
            if (indexOfSelected != -1) {
                setSelectedItem(indexOfSelected)
            }
        },
        popupManager =
            remember {
                PopupManager(
                    onPopupVisibleChange = {
                        previewSelectedIndex = -1
                        onPopupVisibleChange(it)
                    },
                    name = "EditableListComboBoxPopup",
                )
            },
        popupContent = {
            PopupContent(
                items = items,
                previewSelectedItemIndex = previewSelectedIndex,
                listState = listState,
                popupMaxHeight = popupMaxHeight,
                contentPadding = contentPadding,
                onPreviewSelectedItemChange = {
                    if (it >= 0 && previewSelectedIndex != it) {
                        previewSelectedIndex = it
                    }
                },
                onSelectedItemChange = ::setSelectedItem,
                itemKeys = itemKeys,
                itemContent = { item, isSelected, isActive ->
                    SimpleListItem(
                        text = item,
                        isSelected = isSelected,
                        isActive = isActive,
                        iconContentDescription = item,
                    )
                },
            )
        },
    )
}

private suspend fun LazyListState.scrollToIndex(itemIndex: Int) {
    val isFirstItemFullyVisible = firstVisibleItemScrollOffset == 0

    // If there are no visible items, just return
    val lastItemInfo = layoutInfo.visibleItemsInfo.lastOrNull() ?: return
    val isLastItemFullyVisible = layoutInfo.viewportEndOffset - lastItemInfo.offset >= lastItemInfo.size

    val lastItemInfoSize = lastItemInfo.size
    when {
        itemIndex < visibleItemsRange.first -> scrollToItem((itemIndex - 1).coerceAtLeast(0))
        itemIndex == visibleItemsRange.first && !isFirstItemFullyVisible -> scrollToItem(itemIndex)
        itemIndex == visibleItemsRange.last && !isLastItemFullyVisible -> {
            scrollToItem(itemIndex, layoutInfo.viewportEndOffset - lastItemInfoSize)
        }
        itemIndex > visibleItemsRange.last -> {
            // First scroll assuming the new item has the same height as the current last item
            scrollToItem(itemIndex, layoutInfo.viewportEndOffset - lastItemInfoSize)

            // After scrolling, check if we need to adjust due to different item sizes
            val newLastItemInfo = layoutInfo.visibleItemsInfo.lastOrNull() ?: return
            if (newLastItemInfo.size != lastItemInfoSize) {
                scrollToItem(itemIndex, layoutInfo.viewportEndOffset - newLastItemInfo.size)
            }
        }
    }
}

/** Returns the index of the selected item in the list, returning -1 if there is no selected item. */
public fun <T> SelectableLazyListState.selectedItemIndex(items: List<T>, itemKeys: (Int, T) -> Any): Int {
    if (selectedKeys.isEmpty()) return -1

    val selectedKey = selectedKeys.first()
    for (i in items.indices) {
        if (itemKeys(i, items[i]) == selectedKey) {
            return i
        }
    }
    return -1
}

@Composable
private fun <T : Any> PopupContent(
    items: List<T>,
    previewSelectedItemIndex: Int,
    listState: SelectableLazyListState,
    popupMaxHeight: Dp,
    contentPadding: PaddingValues,
    onPreviewSelectedItemChange: (Int) -> Unit,
    onSelectedItemChange: (Int) -> Unit,
    itemKeys: (Int, T) -> Any,
    itemContent: @Composable (item: T, isSelected: Boolean, isActive: Boolean) -> Unit,
) {
    VerticallyScrollableContainer(
        scrollState = listState.lazyListState,
        modifier = Modifier.heightIn(max = popupMaxHeight),
    ) {
        SelectableLazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = popupMaxHeight).padding(contentPadding),
            selectionMode = SelectionMode.Single,
            state = listState,
            onSelectedIndexesChange = { selectedItemsIndexes ->
                val selectedIndex = selectedItemsIndexes.firstOrNull()
                if (selectedIndex != null) onSelectedItemChange(selectedIndex)
            },
        ) { ->
            itemsIndexed(
                items = items,
                key = { itemIndex, item -> itemKeys(itemIndex, item) },
                itemContent = { index, item ->
                    Box(
                        modifier =
                            Modifier.thenIf(!listState.isScrollInProgress) {
                                onMove {
                                    if (previewSelectedItemIndex != index) {
                                        onPreviewSelectedItemChange(index)
                                    }
                                }
                            }
                    ) {
                        // Items can be "actually" selected, or "preview" selected (e.g.,
                        // hovered),
                        // but if we have a "preview" selection, we hide the "actual" selection
                        val key = itemKeys(index, item)
                        val isItemSelected = listState.selectedKeys.contains(key)
                        val showAsSelected =
                            (isItemSelected && previewSelectedItemIndex < 0) || previewSelectedItemIndex == index

                        // We assume items are active when visible (the popup isn't really, but should show as such)
                        itemContent(item, showAsSelected, true)
                    }
                },
            )
        }
    }
}
