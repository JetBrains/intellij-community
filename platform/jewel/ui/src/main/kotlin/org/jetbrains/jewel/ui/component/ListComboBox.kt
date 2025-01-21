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
import kotlinx.coroutines.launch
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
import org.jetbrains.jewel.ui.theme.comboBoxStyle

@Composable
public fun ListComboBox(
    items: List<String>,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    initialSelectedIndex: Int = 0,
    outline: Outline = Outline.None,
    maxPopupHeight: Dp = Dp.Unspecified,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: ComboBoxStyle = JewelTheme.comboBoxStyle,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    onSelectedItemChange: (Int, String) -> Unit = { _, _ -> },
    onPopupVisibleChange: (visible: Boolean) -> Unit = {},
    itemContent: @Composable (text: String, isSelected: Boolean, isActive: Boolean) -> Unit,
) {
    val listState = rememberSelectableLazyListState()
    var labelText by remember { mutableStateOf(items.firstOrNull().orEmpty()) }
    var previewSelectedIndex by remember { mutableIntStateOf(-1) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        // Select the first item in the list automatically when creating
        if (items.isNotEmpty()) {
            listState.selectedKeys = setOf(initialSelectedIndex.coerceIn(0, items.lastIndex))
        }
    }

    fun setSelectedItem(index: Int) {
        if (index >= 0 && index <= items.lastIndex) {
            listState.selectedKeys = setOf(index)
            labelText = items[index]
            onSelectedItemChange(index, items[index])
            scope.launch { listState.lazyListState.scrollToIndex(index) }
        } else {
            JewelLogger.getInstance("ListComboBox").trace("Ignoring item index $index as it's invalid")
        }
    }

    val contentPadding = JewelTheme.comboBoxStyle.metrics.popupContentPadding
    val popupMaxHeight =
        if (maxPopupHeight == Dp.Unspecified) {
            JewelTheme.comboBoxStyle.metrics.maxPopupHeight
        } else {
            maxPopupHeight
        }

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
        isEnabled = isEnabled,
        labelText = labelText,
        maxPopupHeight = popupMaxHeight,
        onArrowDownPress = {
            var currentSelectedIndex = listState.selectedItemIndex()

            // When there is a preview-selected item, pressing down will actually change the
            // selected value to the one underneath it (unless it's the last one)
            if (previewSelectedIndex >= 0 && previewSelectedIndex < items.lastIndex) {
                currentSelectedIndex = previewSelectedIndex
                previewSelectedIndex = -1
            }

            setSelectedItem((currentSelectedIndex + 1).coerceAtMost(items.lastIndex))
        },
        onArrowUpPress = {
            var currentSelectedIndex = listState.selectedItemIndex()

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
            itemContent = itemContent,
        )
    }
}

@Composable
public fun EditableListComboBox(
    items: List<String>,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    initialSelectedIndex: Int = 0,
    outline: Outline = Outline.None,
    maxPopupHeight: Dp = Dp.Unspecified,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: ComboBoxStyle = JewelTheme.comboBoxStyle,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    onSelectedItemChange: (Int, String) -> Unit = { _, _ -> },
    onPopupVisibleChange: (visible: Boolean) -> Unit = {},
    itemContent: @Composable (text: String, isSelected: Boolean, isActive: Boolean) -> Unit,
) {
    val listState = rememberSelectableLazyListState()
    val textFieldState = rememberTextFieldState(items.firstOrNull().orEmpty())
    var previewSelectedIndex by remember { mutableIntStateOf(-1) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        // Select the first item in the list automatically when creating
        if (items.isNotEmpty()) {
            listState.selectedKeys = setOf(initialSelectedIndex.coerceIn(0, items.lastIndex))
        }
    }

    fun setSelectedItem(index: Int) {
        if (index >= 0 && index <= items.lastIndex) {
            // Note: it's important to do the edit _before_ updating the list state,
            // since updating the list state will cause another, asynchronous and
            // potentially nested call to edit, which is not supported.
            // This is because setting the selected keys on the SLC will eventually
            // cause a call to this very function via SLC's onSelectedIndexesChange.
            textFieldState.edit { replace(0, length, items[index]) }

            if (listState.selectedKeys.size != 1 || listState.selectedItemIndex() != index) {
                // This guard condition should also help avoid issues caused by side effects
                // of setting new selected keys, as per the comment above.
                listState.selectedKeys = setOf(index)
            }
            onSelectedItemChange(index, items[index])
            scope.launch { listState.lazyListState.scrollToIndex(index) }
        } else {
            JewelLogger.getInstance("EditableListComboBox").trace("Ignoring item index $index as it's invalid")
        }
    }

    val contentPadding = JewelTheme.comboBoxStyle.metrics.popupContentPadding
    val popupMaxHeight =
        if (maxPopupHeight == Dp.Unspecified) {
            JewelTheme.comboBoxStyle.metrics.maxPopupHeight
        } else {
            maxPopupHeight
        }

    EditableComboBox(
        textFieldState = textFieldState,
        modifier = modifier,
        isEnabled = isEnabled,
        outline = outline,
        interactionSource = interactionSource,
        style = style,
        textStyle = textStyle,
        onArrowDownPress = {
            var currentSelectedIndex = listState.selectedItemIndex()

            // When there is a preview-selected item, pressing down will actually change the
            // selected value to the one underneath it (unless it's the last one)
            if (previewSelectedIndex >= 0 && previewSelectedIndex < items.lastIndex) {
                currentSelectedIndex = previewSelectedIndex
                previewSelectedIndex = -1
            }

            setSelectedItem((currentSelectedIndex + 1).coerceAtMost(items.lastIndex))
        },
        onArrowUpPress = {
            var currentSelectedIndex = listState.selectedItemIndex()

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
                itemContent = itemContent,
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
private fun SelectableLazyListState.selectedItemIndex(): Int = selectedKeys.firstOrNull() as Int? ?: -1

@Composable
private fun PopupContent(
    items: List<String>,
    previewSelectedItemIndex: Int,
    listState: SelectableLazyListState,
    popupMaxHeight: Dp,
    contentPadding: PaddingValues,
    onPreviewSelectedItemChange: (Int) -> Unit,
    onSelectedItemChange: (Int) -> Unit,
    itemContent: @Composable (text: String, isSelected: Boolean, isActive: Boolean) -> Unit,
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
                key = { itemIndex, _ -> itemIndex }, // TODO pass in from user?
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
                        // Items can be "actually" selected, or "preview" selected (e.g., hovered),
                        // but if we have a "preview" selection, we hide the "actual" selection
                        val showAsSelected =
                            (isSelected && previewSelectedItemIndex < 0) || previewSelectedItemIndex == index

                        itemContent(item, showAsSelected, isActive)
                    }
                },
            )
        }
    }
}
