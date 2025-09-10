package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.lazy.*
import org.jetbrains.jewel.foundation.modifier.onMove
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.util.JewelLogger
import org.jetbrains.jewel.ui.component.PopupManager
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.component.styling.ComboBoxStyle
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.theme.comboBoxStyle

/**
 * A customized version of org/jetbrains/jewel/ui/component/ListComboBox.kt
 * Displays an icon alongside the text using the [iconKey] parameter
 * Adds a text prefix to all displayed items using the [itemPrefix] parameter
 * Uses [WelcomeScreenCustomComboBox] as its underlying implementation instead of the standard ComboBox
 */
@OptIn(ExperimentalJewelApi::class)
@Composable
internal fun WelcomeScreenCustomListComboBox(
  iconKey: IconKey,
  itemPrefix: String,
  items: List<String>,
  modifier: Modifier = Modifier,
  isEnabled: Boolean = true,
  initialSelectedIndex: Int = 0,
  maxPopupHeight: Dp = Dp.Unspecified,
  minPopupWidth: Dp = Dp.Unspecified,
  interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
  style: ComboBoxStyle = JewelTheme.comboBoxStyle,
  textStyle: TextStyle = JewelTheme.defaultTextStyle,
  onSelectedItemChange: (Int, String) -> Unit = { _, _ -> },
  onPopupVisibleChange: (visible: Boolean) -> Unit = {},
  itemContent: @Composable (text: String, isSelected: Boolean, isActive: Boolean) -> Unit,
) {
    val listState = rememberSelectableLazyListState()
    var labelText by remember { mutableStateOf(itemPrefix + items[initialSelectedIndex]) }
    var previewSelectedIndex by remember { mutableIntStateOf(-1) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        // Select the first item in the list automatically when creating
        if (items.isNotEmpty()) {
            listState.selectedKeys = setOf(initialSelectedIndex.coerceIn(0, items.lastIndex))
        }
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

    fun setSelectedItem(index: Int) {
        if (index >= 0 && index <= items.lastIndex) {
            listState.selectedKeys = setOf(index)
            labelText = itemPrefix + items[index]
            //labelText = items[index]
            onSelectedItemChange(index, items[index])
            scope.launch { listState.lazyListState.scrollToIndex(index) }
            popupManager.setPopupVisible(false)
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

  WelcomeScreenCustomComboBox(
    modifier =
      modifier
        .onPreviewKeyEvent {
        if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

        if (it.key == Key.Enter || it.key == Key.NumPadEnter) {
          if (popupManager.isPopupVisible.value && previewSelectedIndex >= 0) {
            setSelectedItem(previewSelectedIndex)
            previewSelectedIndex = -1
            popupManager.setPopupVisible(false)
          }
          true
        }
        else {
          false
        }
      },
    isEnabled = isEnabled,
    iconKey = iconKey,
    labelText = labelText,
    maxPopupHeight = popupMaxHeight,
    minPopupWidth = minPopupWidth,
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
    ) {
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
