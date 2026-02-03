package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.window.PopupPositionProvider
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.lazy.SelectableLazyColumn
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState
import org.jetbrains.jewel.foundation.lazy.SelectionMode
import org.jetbrains.jewel.foundation.lazy.itemsIndexed
import org.jetbrains.jewel.foundation.lazy.rememberSelectableLazyListState
import org.jetbrains.jewel.foundation.lazy.tree.NoopListKeyActions
import org.jetbrains.jewel.foundation.lazy.visibleItemsRange
import org.jetbrains.jewel.foundation.modifier.onMove
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.util.JewelLogger
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.styling.ComboBoxStyle
import org.jetbrains.jewel.ui.component.styling.PopupContainerStyle
import org.jetbrains.jewel.ui.disabledAppearance
import org.jetbrains.jewel.ui.theme.comboBoxStyle
import org.jetbrains.jewel.ui.theme.popupContainerStyle

/**
 * A non-editable dropdown list component that follows the standard visual styling.
 *
 * Provides a selectable list of items in a dropdown format. When clicked, displays a popup with the list of items.
 * Supports keyboard navigation, item selection, and custom item rendering. The selected item is displayed in the main
 * control.
 *
 * It is **strongly** recommended to provide a fixed width for the component, by using modifiers such as `width`,
 * `weight`, `fillMaxWidth`, etc. If the component does not have a fixed width, it will size itself based on the label
 * content. This means the width of the component will change based on the selected item's label.
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
 * @param popupModifier Modifier to be applied to the popup of the combo box
 * @param enabled Controls whether the combo box can be interacted with
 * @param outline The outline style to be applied to the combo box
 * @param maxPopupHeight The maximum height of the popup list
 * @param maxPopupWidth The maximum width of the popup list. If not set, it will match the width of the combo box
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
@Suppress("ContentSlotReused")
public fun <T : Any> ListComboBox(
    items: List<T>,
    selectedIndex: Int,
    onSelectedItemChange: (Int) -> Unit,
    itemKeys: (Int, T) -> Any,
    modifier: Modifier = Modifier,
    popupModifier: Modifier = Modifier,
    enabled: Boolean = true,
    outline: Outline = Outline.None,
    maxPopupHeight: Dp = Dp.Unspecified,
    maxPopupWidth: Dp = Dp.Unspecified,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: ComboBoxStyle = JewelTheme.comboBoxStyle,
    onPopupVisibleChange: (visible: Boolean) -> Unit = {},
    listState: SelectableLazyListState =
        rememberSelectableLazyListState(selectedIndex.takeIfInBoundsOrZero(items.indices)),
    itemContent: @Composable (item: T, isSelected: Boolean, isActive: Boolean) -> Unit,
) {
    ListComboBoxImpl(
        items = items,
        selectedIndex = selectedIndex,
        onSelectedItemChange = onSelectedItemChange,
        itemKeys = itemKeys,
        modifier = modifier,
        popupModifier = popupModifier,
        enabled = enabled,
        outline = outline,
        maxPopupHeight = maxPopupHeight,
        maxPopupWidth = maxPopupWidth,
        interactionSource = interactionSource,
        style = style,
        onPopupVisibleChange = onPopupVisibleChange,
        listState = listState,
        labelContent = { item ->
            if (item != null) {
                itemContent(item, false, false)
            }
        },
        itemContent = { _, item, isSelected, isActive -> itemContent(item, isSelected, isActive) },
    )
}

/**
 * A non-editable dropdown list component that follows the standard visual styling.
 *
 * Provides a selectable list of items in a dropdown format. When clicked, displays a popup with the list of items.
 * Supports keyboard navigation, item selection, and custom item rendering. The selected item is displayed in the main
 * control.
 *
 * It is **strongly** recommended to provide a fixed width for the component, by using modifiers such as `width`,
 * `weight`, `fillMaxWidth`, etc. If the component does not have a fixed width, it will size itself based on the label
 * content. This means the width of the component will change based on the selected item's label.
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
@Deprecated(
    "Deprecated in favor of the method with 'popupModifier' and 'maxPopupWidth' parameters",
    level = DeprecationLevel.HIDDEN,
)
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
    listState: SelectableLazyListState =
        rememberSelectableLazyListState(selectedIndex.takeIfInBoundsOrZero(items.indices)),
    itemContent: @Composable (item: T, isSelected: Boolean, isActive: Boolean) -> Unit,
) {
    ListComboBox(
        items = items,
        selectedIndex = selectedIndex,
        onSelectedItemChange = onSelectedItemChange,
        itemKeys = itemKeys,
        modifier = modifier,
        popupModifier = Modifier,
        enabled = enabled,
        outline = outline,
        maxPopupHeight = maxPopupHeight,
        maxPopupWidth = Dp.Unspecified,
        interactionSource = interactionSource,
        style = style,
        onPopupVisibleChange = onPopupVisibleChange,
        listState = listState,
        itemContent = itemContent,
    )
}

/**
 * A non-editable dropdown list component that follows the standard visual styling.
 *
 * Provides a selectable list of items in a dropdown format. When clicked, displays a popup with the list of items.
 * Supports keyboard navigation, item selection, and custom item rendering. The selected item is displayed in the main
 * control.
 *
 * It is **strongly** recommended to provide a fixed width for the component, by using modifiers such as `width`,
 * `weight`, `fillMaxWidth`, etc. If the component does not have a fixed width, it will size itself based on the label
 * content. This means the width of the component will change based on the selected item's label.
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
 * @param popupModifier Modifier to be applied to the popup of the combo box
 * @param enabled Controls whether the combo box can be interacted with
 * @param outline The outline style to be applied to the combo box
 * @param maxPopupHeight The maximum height of the popup list
 * @param maxPopupWidth The maximum width of the popup list
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
    @Nls items: List<String>,
    selectedIndex: Int,
    onSelectedItemChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    popupModifier: Modifier = Modifier,
    enabled: Boolean = true,
    outline: Outline = Outline.None,
    maxPopupHeight: Dp = Dp.Unspecified,
    maxPopupWidth: Dp = Dp.Unspecified,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: ComboBoxStyle = JewelTheme.comboBoxStyle,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    onPopupVisibleChange: (visible: Boolean) -> Unit = {},
    itemKeys: (Int, String) -> Any = { _, item -> item },
    listState: SelectableLazyListState =
        rememberSelectableLazyListState(selectedIndex.takeIfInBoundsOrZero(items.indices)),
) {
    ListComboBoxImpl(
        items = items,
        selectedIndex = selectedIndex,
        onSelectedItemChange = onSelectedItemChange,
        itemKeys = itemKeys,
        modifier = modifier,
        enabled = enabled,
        outline = outline,
        maxPopupHeight = maxPopupHeight,
        maxPopupWidth = maxPopupWidth,
        interactionSource = interactionSource,
        style = style,
        onPopupVisibleChange = onPopupVisibleChange,
        listState = listState,
        popupModifier = popupModifier,
        labelContent = { item -> ComboBoxLabelText(item.orEmpty(), textStyle, style, enabled) },
        itemContent = { _, item, isSelected, isActive ->
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

/**
 * A non-editable dropdown list component that follows the standard visual styling.
 *
 * Provides a selectable list of items in a dropdown format. When clicked, displays a popup with the list of items.
 * Supports keyboard navigation, item selection, and custom item rendering. The selected item is displayed in the main
 * control.
 *
 * It is **strongly** recommended to provide a fixed width for the component, by using modifiers such as `width`,
 * `weight`, `fillMaxWidth`, etc. If the component does not have a fixed width, it will size itself based on the label
 * content. This means the width of the component will change based on the selected item's label.
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
@Deprecated(
    "Deprecated in favor of the method with 'popupModifier' and 'maxPopupWidth' parameters",
    level = DeprecationLevel.HIDDEN,
)
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
    listState: SelectableLazyListState =
        rememberSelectableLazyListState(selectedIndex.takeIfInBoundsOrZero(items.indices)),
) {
    ListComboBox(
        items = items,
        selectedIndex = selectedIndex,
        onSelectedItemChange = onSelectedItemChange,
        modifier = modifier,
        popupModifier = Modifier,
        enabled = enabled,
        outline = outline,
        maxPopupHeight = maxPopupHeight,
        maxPopupWidth = Dp.Unspecified,
        interactionSource = interactionSource,
        style = style,
        textStyle = textStyle,
        onPopupVisibleChange = onPopupVisibleChange,
        itemKeys = itemKeys,
        listState = listState,
    )
}

/**
 * An editable dropdown list component that follows the standard visual styling.
 *
 * Provides a text field with a dropdown list of suggestions. Users can either select from the list or type their own
 * value. Supports keyboard navigation, item selection, and custom item rendering. The selected or entered text is
 * displayed in the editable text field.
 *
 * It is **strongly** recommended to provide a fixed width for the component, by using modifiers such as `width`,
 * `weight`, `fillMaxWidth`, etc. If the component does not have a fixed width, it will size itself based on the label
 * content. This means the width of the component will change based on the selected item's label.
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
    popupModifier: Modifier = Modifier,
    enabled: Boolean = true,
    outline: Outline = Outline.None,
    maxPopupHeight: Dp = Dp.Unspecified,
    maxPopupWidth: Dp = Dp.Unspecified,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: ComboBoxStyle = JewelTheme.comboBoxStyle,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    onPopupVisibleChange: (visible: Boolean) -> Unit = {},
    itemKeys: (Int, String) -> Any = { _, item -> item },
    listState: SelectableLazyListState =
        rememberSelectableLazyListState(selectedIndex.takeIfInBoundsOrZero(items.indices)),
) {
    val density = LocalDensity.current
    var comboBoxSize by remember { mutableStateOf(DpSize.Zero) }

    val textFieldState = rememberTextFieldState(items.getOrNull(selectedIndex).orEmpty())
    var hoveredItemIndex by remember { mutableIntStateOf(-1) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(itemKeys) {
        // Select the first item in the list when creating
        listState.selectedKeys = setOf(itemKeys(selectedIndex, items.getOrNull(selectedIndex).orEmpty()))
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
        modifier =
            modifier.onSizeChanged { comboBoxSize = with(density) { DpSize(it.width.toDp(), it.height.toDp()) } },
        popupModifier = popupModifier,
        maxPopupHeight = popupMaxHeight,
        maxPopupWidth = maxPopupWidth.takeOrElse { comboBoxSize.width },
        enabled = enabled,
        outline = outline,
        interactionSource = interactionSource,
        style = style,
        textStyle = textStyle,
        onArrowDownPress = {
            var currentSelectedIndex = listState.selectedItemIndex(items, itemKeys)

            // When there is a preview-selected item, pressing down will actually change the
            // selected value to the one underneath it (unless it's the last one)
            if (hoveredItemIndex >= 0 && hoveredItemIndex < items.lastIndex) {
                currentSelectedIndex = hoveredItemIndex
                @Suppress("AssignedValueIsNeverRead")
                hoveredItemIndex = -1
            }

            setSelectedItem((currentSelectedIndex + 1).coerceAtMost(items.lastIndex))
        },
        onArrowUpPress = {
            var currentSelectedIndex = listState.selectedItemIndex(items, itemKeys)

            // When there is a preview-selected item, pressing up will actually change the
            // selected value to the one above it (unless it's the first one)
            if (hoveredItemIndex > 0) {
                currentSelectedIndex = hoveredItemIndex
                @Suppress("AssignedValueIsNeverRead")
                hoveredItemIndex = -1
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
                        hoveredItemIndex = -1
                        onPopupVisibleChange(it)
                    },
                    name = "EditableListComboBoxPopup",
                )
            },
        popupContent = {
            PopupContent(
                items = items,
                currentlySelectedIndex = selectedIndex,
                previewSelectedItemIndex = hoveredItemIndex,
                listState = listState,
                contentPadding = contentPadding,
                comboBoxSize = comboBoxSize,
                onHoveredItemChange = {
                    if (it >= 0 && hoveredItemIndex != it) {
                        @Suppress("AssignedValueIsNeverRead")
                        hoveredItemIndex = it
                    }
                },
                onSelectedItemChange = ::setSelectedItem,
                itemKeys = itemKeys,
                itemContent = { _, item, isSelected, isActive ->
                    SimpleListItem(text = item, selected = isSelected, active = isActive, iconContentDescription = item)
                },
            )
        },
    )
}

/**
 * An editable dropdown list component that follows the standard visual styling.
 *
 * Provides a text field with a dropdown list of suggestions. Users can either select from the list or type their own
 * value. Supports keyboard navigation, item selection, and custom item rendering. The selected or entered text is
 * displayed in the editable text field.
 *
 * It is **strongly** recommended to provide a fixed width for the component, by using modifiers such as `width`,
 * `weight`, `fillMaxWidth`, etc. If the component does not have a fixed width, it will size itself based on the label
 * content. This means the width of the component will change based on the selected item's label.
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
@Deprecated(
    "Deprecated in favor of the method with 'popupModifier' and 'maxPopupWidth' parameters",
    level = DeprecationLevel.HIDDEN,
)
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
    listState: SelectableLazyListState =
        rememberSelectableLazyListState(selectedIndex.takeIfInBoundsOrZero(items.indices)),
) {
    EditableListComboBox(
        items = items,
        selectedIndex = selectedIndex,
        onSelectedItemChange = onSelectedItemChange,
        modifier = modifier,
        popupModifier = Modifier,
        enabled = enabled,
        outline = outline,
        maxPopupHeight = maxPopupHeight,
        maxPopupWidth = Dp.Unspecified,
        interactionSource = interactionSource,
        style = style,
        textStyle = textStyle,
        onPopupVisibleChange = onPopupVisibleChange,
        itemKeys = itemKeys,
        listState = listState,
    )
}

private suspend fun LazyListState.scrollToIndex(itemIndex: Int) {
    val isFirstItemFullyVisible = firstVisibleItemScrollOffset == 0

    // If there are no visible items, just return
    val lastItemInfo = layoutInfo.visibleItemsInfo.lastOrNull() ?: return
    val lastItemInfoSize = lastItemInfo.size
    val isLastItemFullyVisible = layoutInfo.viewportEndOffset - lastItemInfo.offset >= lastItemInfoSize

    when {
        itemIndex < visibleItemsRange.first -> scrollToItem(itemIndex.coerceAtLeast(0))
        itemIndex == visibleItemsRange.first && !isFirstItemFullyVisible -> scrollToItem(itemIndex)
        itemIndex == visibleItemsRange.last && !isLastItemFullyVisible -> {
            scrollToItem(itemIndex, -(layoutInfo.viewportEndOffset - lastItemInfoSize))
        }
        itemIndex > visibleItemsRange.last -> {
            scrollToItem(itemIndex, -(layoutInfo.viewportEndOffset - lastItemInfoSize))
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
internal fun <T : Any> ListComboBoxImpl(
    items: List<T>,
    selectedIndex: Int,
    onSelectedItemChange: (Int) -> Unit,
    itemKeys: (Int, T) -> Any,
    enabled: Boolean,
    outline: Outline,
    maxPopupHeight: Dp,
    maxPopupWidth: Dp,
    interactionSource: MutableInteractionSource,
    style: ComboBoxStyle,
    onPopupVisibleChange: (visible: Boolean) -> Unit,
    listState: SelectableLazyListState,
    labelContent: @Composable (item: T?) -> Unit,
    modifier: Modifier = Modifier,
    popupModifier: Modifier = Modifier,
    horizontalPopupAlignment: Alignment.Horizontal = Alignment.Start,
    popupStyle: PopupContainerStyle = JewelTheme.popupContainerStyle,
    popupPositionProvider: PopupPositionProvider =
        AnchorVerticalMenuPositionProvider(
            contentOffset = popupStyle.metrics.offset,
            contentMargin = popupStyle.metrics.menuMargin,
            alignment = horizontalPopupAlignment,
            density = LocalDensity.current,
        ),
    itemContent: @Composable (index: Int, item: T, isSelected: Boolean, isActive: Boolean) -> Unit,
) {
    LaunchedEffect(itemKeys) {
        val item = items.getOrNull(selectedIndex)
        if (item != null) {
            listState.selectedKeys = setOf(itemKeys(selectedIndex, item))
        } else {
            listState.selectedKeys = emptySet()
        }
    }

    val density = LocalDensity.current
    var comboBoxSize by remember { mutableStateOf(DpSize.Zero) }
    var currentComboBoxSize by remember { mutableStateOf(DpSize.Zero) }

    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    var hoveredItemIndex by remember { mutableIntStateOf(selectedIndex) }
    val scope = rememberCoroutineScope()

    fun setSelectedItem(index: Int) {
        if (index == currentSelectedIndex) return

        if (index >= 0 && index <= items.lastIndex) {
            listState.selectedKeys = setOf(itemKeys(index, items[index]))
            onSelectedItemChange(index)
            scope.launch { listState.lazyListState.scrollToIndex(index) }
        } else {
            JewelLogger.getInstance("ListComboBox").trace("Ignoring item index $index as it's invalid")
        }
    }

    fun resetPreviewSelectedIndex() {
        hoveredItemIndex = -1
    }

    fun navigateDown() {
        if (items.isEmpty()) return
        var currentSelection = listState.selectedItemIndex(items, itemKeys)
        // When there is a preview-selected item, pressing down will actually change the
        // selected value to the one underneath it (unless it's the last one)
        if (hoveredItemIndex >= 0 && hoveredItemIndex < items.lastIndex) {
            currentSelection = hoveredItemIndex
            resetPreviewSelectedIndex()
        }
        setSelectedItem((currentSelection + 1).coerceAtMost(items.lastIndex))
    }

    fun navigateUp() {
        if (items.isEmpty()) return
        var currentSelection = listState.selectedItemIndex(items, itemKeys)
        // When there is a preview-selected item, pressing up will actually change the
        // selected value to the one above it (unless it's the first one)
        if (hoveredItemIndex > 0) {
            currentSelection = hoveredItemIndex
            resetPreviewSelectedIndex()
        }
        setSelectedItem((currentSelection - 1).coerceAtLeast(0))
    }

    fun selectHome(): Boolean {
        if (items.isEmpty()) return false
        setSelectedItem(0)
        resetPreviewSelectedIndex()
        return true
    }

    fun selectEnd(): Boolean {
        if (items.isEmpty()) return false
        setSelectedItem(items.lastIndex)
        resetPreviewSelectedIndex()
        return true
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

    fun commitSelectionFromHoverOrMapped() {
        val mappedIndex = listState.selectedItemIndex(items, itemKeys)
        val targetIndex =
            when {
                hoveredItemIndex >= 0 -> hoveredItemIndex
                mappedIndex >= 0 -> mappedIndex
                else -> null
            }
        if (targetIndex != null) {
            setSelectedItem(targetIndex)
            resetPreviewSelectedIndex()
        }
    }

    fun handlePopupKeyDown(event: androidx.compose.ui.input.key.KeyEvent): Boolean =
        if (!popupManager.isPopupVisible.value) {
            false
        } else {
            when (event.key) {
                Key.MoveHome,
                Key.Home -> selectHome()
                Key.MoveEnd -> selectEnd()
                Key.DirectionDown -> {
                    navigateDown()
                    true
                }
                Key.DirectionUp -> {
                    navigateUp()
                    true
                }
                Key.Enter,
                Key.NumPadEnter -> {
                    commitSelectionFromHoverOrMapped()
                    popupManager.setPopupVisible(false)
                    true
                }
                else -> false
            }
        }

    // This logic ensures that the popup size does not change based on the combo box size if the popup is visible
    LaunchedEffect(currentComboBoxSize, popupManager.isPopupVisible.value) {
        if (!popupManager.isPopupVisible.value) {
            comboBoxSize = currentComboBoxSize
        }
    }

    ComboBoxImpl(
        modifier =
            modifier
                .onSizeChanged { currentComboBoxSize = with(density) { DpSize(it.width.toDp(), it.height.toDp()) } }
                .onPreviewKeyEvent {
                    if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    return@onPreviewKeyEvent handlePopupKeyDown(it)
                },
        popupModifier = popupModifier,
        enabled = enabled,
        maxPopupHeight = popupMaxHeight,
        maxPopupWidth = maxPopupWidth.takeOrElse { comboBoxSize.width },
        onArrowDownPress = down@{
                if (popupManager.isPopupVisible.value) return@down
                navigateDown()
            },
        onArrowUpPress = up@{
                if (popupManager.isPopupVisible.value) return@up
                navigateUp()
            },
        style = style,
        interactionSource = interactionSource,
        outline = outline,
        popupManager = popupManager,
        horizontalPopupAlignment = horizontalPopupAlignment,
        popupStyle = popupStyle,
        popupPositionProvider = popupPositionProvider,
        labelContent = { labelContent(items.getOrNull(selectedIndex)) },
        popupContent = {
            PopupContent(
                items = items,
                previewSelectedItemIndex = hoveredItemIndex,
                currentlySelectedIndex = selectedIndex,
                listState = listState,
                contentPadding = contentPadding,
                comboBoxSize = comboBoxSize,
                onHoveredItemChange = {
                    if (it >= 0 && hoveredItemIndex != it) {
                        hoveredItemIndex = it
                    }
                },
                onSelectedItemChange = { index: Int -> setSelectedItem(index) },
                itemKeys = itemKeys,
                itemContent = { index, item, isSelected, isActive -> itemContent(index, item, isSelected, isActive) },
            )
        },
    )
}

@Composable
private fun <T : Any> PopupContent(
    items: List<T>,
    currentlySelectedIndex: Int,
    previewSelectedItemIndex: Int,
    listState: SelectableLazyListState,
    contentPadding: PaddingValues,
    comboBoxSize: DpSize,
    onHoveredItemChange: (Int) -> Unit,
    onSelectedItemChange: (Int) -> Unit,
    itemKeys: (Int, T) -> Any,
    itemContent: @Composable (index: Int, item: T, isSelected: Boolean, isActive: Boolean) -> Unit,
) {
    VerticallyScrollableContainer(scrollState = listState.lazyListState as ScrollableState) {
        SelectableLazyColumn(
            modifier =
                Modifier.fillMaxWidth().testTag("Jewel.ComboBox.List").thenIf(items.isEmpty()) {
                    heightIn(
                        min =
                            comboBoxSize.height +
                                contentPadding.calculateTopPadding() +
                                contentPadding.calculateBottomPadding()
                    )
                },
            contentPadding = if (items.isNotEmpty()) contentPadding else PaddingValues(),
            selectionMode = SelectionMode.Single,
            state = listState,
            onSelectedIndexesChange = { selectedItemsIndexes ->
                val selectedIndex = selectedItemsIndexes.firstOrNull()
                if (selectedIndex != null) onSelectedItemChange(selectedIndex)
            },
            // Disable inner list keyboard navigation while popup is visible; navigation is handled at ComboBox level
            keyActions = NoopListKeyActions,
        ) {
            itemsIndexed(
                items = items,
                key = { itemIndex, item -> itemKeys(itemIndex, item) },
                itemContent = { index, item ->
                    Box(
                        modifier =
                            Modifier.thenIf(!listState.isScrollInProgress) {
                                onMove {
                                    if (previewSelectedItemIndex != index) {
                                        onHoveredItemChange(index)
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
                        itemContent(index, item, showAsSelected, true)
                    }
                },
            )
        }
    }

    LaunchedEffect(Unit) {
        // Only run the call when the list is actually visible
        val visibleItems = snapshotFlow { listState.visibleItemsRange }.filter { it.first >= 0 && it.last >= 0 }.first()

        val indexToShow = currentlySelectedIndex.takeIfInBoundsOrZero(items.indices)
        if (indexToShow !in visibleItems) {
            listState.lazyListState.scrollToIndex(indexToShow)
        }
    }
}

internal fun Int.takeIfInBoundsOrZero(acceptedIndices: IntRange) = if (this in acceptedIndices) this else 0
