// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import java.awt.Cursor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.OverflowBox
import org.jetbrains.jewel.foundation.OverflowBoxScope
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.lazy.table.LazyTableCellScope
import org.jetbrains.jewel.foundation.lazy.table.draggable.lazyTableCellDraggingOffset
import org.jetbrains.jewel.foundation.lazy.table.draggable.lazyTableDraggableColumnCell
import org.jetbrains.jewel.foundation.lazy.table.draggable.lazyTableDraggableRowCell
import org.jetbrains.jewel.foundation.lazy.table.selectable.TableSelectionUnit
import org.jetbrains.jewel.foundation.lazy.table.selectable.onSelectChanged
import org.jetbrains.jewel.foundation.lazy.table.selectable.selectableCell
import org.jetbrains.jewel.foundation.modifier.border
import org.jetbrains.jewel.foundation.modifier.onHover
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.foundation.state.CommonStateBitMask
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.ui.component.TableCellState.Companion.of
import org.jetbrains.jewel.ui.component.styling.TableCellColors
import org.jetbrains.jewel.ui.component.styling.TableMetrics
import org.jetbrains.jewel.ui.component.styling.TableStyle
import org.jetbrains.jewel.ui.theme.tableStyle

/**
 * Renders a table cell using the current [TableStyle].
 *
 * This is a generic cell container that handles selection, focus/hover visuals, optional resize handles and provides
 * the appropriate colors via [LocalContentColor]. Use [SimpleTextTableViewCell] for the common text case.
 *
 * @param modifier optional [Modifier] for the cell container.
 * @param selectable when true, the cell participates in table selection.
 * @param moveable when true, the cell participates in table row/column dragging.
 * @param alignment how the [content] is aligned within the cell.
 * @param style the [TableStyle] to use. Defaults to [JewelTheme.tableStyle].
 * @param onResizeWidth if provided, shows a horizontal drag handle and calls the lambda with the updated width in [Dp]
 *   while dragging.
 * @param onResizeHeight if provided, shows a vertical drag handle and calls the lambda with the updated height in [Dp]
 *   while dragging.
 * @param content the composable content of the cell. Runs inside an [OverflowBoxScope].
 * @receiver the [LazyTableCellScope] of the current table.
 */
@Composable
@ExperimentalJewelApi
@ApiStatus.Experimental
public fun LazyTableCellScope.TableViewCell(
    modifier: Modifier = Modifier,
    selectable: Boolean = true,
    moveable: Boolean = true,
    alignment: Alignment = Alignment.TopStart,
    style: TableStyle = JewelTheme.tableStyle,
    onResizeWidth: ((updatedSize: Dp) -> Unit)? = null,
    onResizeHeight: ((updatedSize: Dp) -> Unit)? = null,
    content: @Composable OverflowBoxScope.() -> Unit,
) {
    TableViewCellImpl(
        isSelectable = selectable,
        isMoveable = moveable,
        alignment = alignment,
        colors = style.colors.cell,
        metrics = style.metrics,
        modifier = modifier,
        onResizeWidth = onResizeWidth,
        onResizeHeight = onResizeHeight,
        content = content,
    )
}

/**
 * Convenience overload of [TableViewCell] to display a single-line text value, optionally with leading and/or trailing
 * icons.
 *
 * The text adopts the current [TableStyle] typography via [textStyle] by default and respects overflow, wrapping and
 * alignment parameters.
 *
 * @param text the text to display.
 * @param modifier optional modifier applied to the cell container.
 * @param textModifier modifier applied to the inner text composable.
 * @param selectable when true, the cell participates in table selection.
 * @param moveable when true, the cell participates in table row/column dragging.
 * @param alignment how content is aligned within the cell.
 * @param color text color. Use [Color.Unspecified] to inherit.
 * @param fontSize size of the text. Use [TextUnit.Unspecified] to inherit.
 * @param fontStyle font style or null to inherit.
 * @param fontWeight font weight or null to inherit.
 * @param fontFamily font family or null to inherit.
 * @param letterSpacing letter spacing. Use [TextUnit.Unspecified] to inherit.
 * @param textDecoration optional decoration (e.g., underline).
 * @param textAlign text alignment. Use [TextAlign.Unspecified] to inherit.
 * @param lineHeight line height. Use [TextUnit.Unspecified] to inherit.
 * @param overflow how to handle visual overflow.
 * @param softWrap whether the text can wrap at soft line breaks.
 * @param maxLines maximum number of lines for the text.
 * @param onTextLayout callback invoked with [TextLayoutResult] after layout.
 * @param leadingIcon optional icon shown at the start of the cell.
 * @param trailingIcon optional icon shown at the end of the cell.
 * @param onResizeWidth if provided, shows a horizontal drag handle and calls the lambda with the updated width in [Dp]
 *   while dragging.
 * @param onResizeHeight if provided, shows a vertical drag handle and calls the lambda with the updated height in [Dp]
 *   while dragging.
 * @param style the [TableStyle] to use.
 * @param textStyle the [TextStyle] applied to the text. Defaults to [JewelTheme.defaultTextStyle].
 * @receiver the [LazyTableCellScope] of the current table.
 */
@Composable
@ExperimentalJewelApi
@ApiStatus.Experimental
public fun LazyTableCellScope.SimpleTextTableViewCell(
    text: String,
    modifier: Modifier = Modifier,
    textModifier: Modifier = Modifier,
    selectable: Boolean = true,
    moveable: Boolean = true,
    alignment: Alignment = Alignment.TopStart,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign = TextAlign.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    onResizeWidth: ((updatedSize: Dp) -> Unit)? = null,
    onResizeHeight: ((updatedSize: Dp) -> Unit)? = null,
    style: TableStyle = JewelTheme.tableStyle,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
) {
    TableViewCell(modifier, selectable, moveable, alignment, style, onResizeWidth, onResizeHeight) {
        TableCellText(
            text = text,
            modifier = textModifier,
            color = color,
            fontSize = fontSize,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            textDecoration = textDecoration,
            textAlign = textAlign,
            lineHeight = lineHeight,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            onTextLayout = onTextLayout,
            style = textStyle,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
        )
    }
}

/**
 * Renders a non-selectable table header cell.
 *
 * This behaves like [TableViewCell] but uses header colors and does not participate in selection.
 *
 * @param modifier optional [Modifier] for the header cell container.
 * @param moveable when true, the cell participates in table row/column dragging.
 * @param alignment how the [content] is aligned within the header.
 * @param style the [TableStyle] to use. Defaults to [JewelTheme.tableStyle].
 * @param onResizeWidth if provided, shows a horizontal drag handle and calls the lambda with the updated width in [Dp]
 *   while dragging.
 * @param onResizeHeight if provided, shows a vertical drag handle and calls the lambda with the updated height in [Dp]
 *   while dragging.
 * @param content the composable content of the header. Runs inside an [OverflowBoxScope].
 * @receiver the [LazyTableCellScope] of the current table.
 */
@Composable
@ExperimentalJewelApi
@ApiStatus.Experimental
public fun LazyTableCellScope.TableViewHeaderCell(
    modifier: Modifier = Modifier,
    moveable: Boolean = true,
    alignment: Alignment = Alignment.TopStart,
    style: TableStyle = JewelTheme.tableStyle,
    onResizeWidth: ((updatedSize: Dp) -> Unit)? = null,
    onResizeHeight: ((updatedSize: Dp) -> Unit)? = null,
    content: @Composable OverflowBoxScope.() -> Unit,
) {
    TableViewCellImpl(
        isSelectable = false,
        isMoveable = moveable,
        alignment = alignment,
        colors = style.colors.header,
        metrics = style.metrics,
        modifier = modifier,
        onResizeWidth = onResizeWidth,
        onResizeHeight = onResizeHeight,
        content = content,
    )
}

/**
 * Convenience overload of [TableViewHeaderCell] to display a header text, optionally with leading and/or trailing
 * icons.
 *
 * @param text the header text to display.
 * @param modifier optional modifier applied to the header container.
 * @param textModifier modifier applied to the inner text composable.
 * @param moveable when true, the cell participates in table row/column dragging.
 * @param alignment how content is aligned within the header.
 * @param color text color. Use [Color.Unspecified] to inherit.
 * @param fontSize size of the text. Use [TextUnit.Unspecified] to inherit.
 * @param fontStyle font style or null to inherit.
 * @param fontWeight font weight or null to inherit.
 * @param fontFamily font family or null to inherit.
 * @param letterSpacing letter spacing. Use [TextUnit.Unspecified] to inherit.
 * @param textDecoration optional decoration (e.g., underline).
 * @param textAlign text alignment. Use [TextAlign.Unspecified] to inherit.
 * @param lineHeight line height. Use [TextUnit.Unspecified] to inherit.
 * @param overflow how to handle visual overflow.
 * @param softWrap whether the text can wrap at soft line breaks.
 * @param maxLines maximum number of lines for the text.
 * @param onTextLayout callback invoked with [TextLayoutResult] after layout.
 * @param leadingIcon optional icon shown at the start of the header.
 * @param trailingIcon optional icon shown at the end of the header.
 * @param onResizeWidth if provided, shows a horizontal drag handle and calls the lambda with the updated width in [Dp]
 *   while dragging.
 * @param onResizeHeight if provided, shows a vertical drag handle and calls the lambda with the updated height in [Dp]
 *   while dragging.
 * @param style the [TableStyle] to use.
 * @param textStyle the [TextStyle] applied to the text. Defaults to [JewelTheme.defaultTextStyle].
 * @receiver the [LazyTableCellScope] of the current table.
 */
@Composable
@ExperimentalJewelApi
@ApiStatus.Experimental
public fun LazyTableCellScope.SimpleTextTableViewHeaderCell(
    text: String,
    modifier: Modifier = Modifier,
    textModifier: Modifier = Modifier,
    moveable: Boolean = true,
    alignment: Alignment = Alignment.TopStart,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign = TextAlign.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    onResizeWidth: ((updatedSize: Dp) -> Unit)? = null,
    onResizeHeight: ((updatedSize: Dp) -> Unit)? = null,
    style: TableStyle = JewelTheme.tableStyle,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
) {
    TableViewHeaderCell(modifier, moveable, alignment, style, onResizeWidth, onResizeHeight) {
        TableCellText(
            text = text,
            modifier = textModifier,
            color = color,
            fontSize = fontSize,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            textDecoration = textDecoration,
            textAlign = textAlign,
            lineHeight = lineHeight,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            onTextLayout = onTextLayout,
            style = textStyle,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
        )
    }
}

@Composable
public fun LazyTableCellScope.EmptyCell(
    modifier: Modifier = Modifier,
    selectable: Boolean = true,
    moveable: Boolean = true,
    style: TableStyle = JewelTheme.tableStyle,
    onResizeWidth: ((updatedSize: Dp) -> Unit)? = null,
    onResizeHeight: ((updatedSize: Dp) -> Unit)? = null,
) {
    TableViewCell(modifier, selectable, moveable, Alignment.TopStart, style, onResizeWidth, onResizeHeight) {}
}

@Composable
private fun LazyTableCellScope.TableViewCellImpl(
    isSelectable: Boolean,
    isMoveable: Boolean,
    alignment: Alignment,
    colors: TableCellColors,
    metrics: TableMetrics,
    onResizeWidth: ((updatedSize: Dp) -> Unit)?,
    onResizeHeight: ((updatedSize: Dp) -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable OverflowBoxScope.() -> Unit,
) {
    var state by remember { mutableStateOf(TableCellState.of()) }

    val layoutInfo = tableState.layoutInfo

    val (columnIndex, rowIndex) = position
    val (columnKey, rowKey) = key

    val isPinnedColumn by rememberUpdatedState(columnIndex < layoutInfo.pinnedColumns)
    val isPinnedRow by rememberUpdatedState(rowIndex < layoutInfo.pinnedRows)
    val isStripe by rememberUpdatedState((rowIndex - layoutInfo.pinnedRows) % 2 == 1)

    val handleInteractionSource = remember { MutableInteractionSource() }
    val isHandleHovered by handleInteractionSource.collectIsHoveredAsState()

    val selectionModifier =
        if (isSelectable) {
            Modifier.selectableCell(
                columnKey,
                rowKey,
                when {
                    isPinnedColumn && isPinnedRow -> TableSelectionUnit.All
                    isPinnedColumn -> TableSelectionUnit.Column
                    isPinnedRow -> TableSelectionUnit.Row
                    else -> TableSelectionUnit.Cell
                },
            )
        } else {
            Modifier
        }

    val dragModifier =
        if (isMoveable) {
            when {
                isPinnedColumn && isPinnedRow -> {
                    Modifier
                }

                isPinnedColumn -> {
                    Modifier.lazyTableDraggableRowCell(rowKey)
                }

                isPinnedRow -> {
                    Modifier.lazyTableDraggableColumnCell(columnKey)
                }

                else -> {
                    Modifier.lazyTableCellDraggingOffset(columnKey, rowKey)
                }
            }
        } else {
            Modifier
        }

    val backgroundColor by colors.backgroundFor(state, isStripe)
    val contentColor by colors.contentFor(state, isStripe)
    val borderWidth by metrics.borderWidthFor(state)

    OverflowBox(
        modifier =
            Modifier.then(dragModifier)
                .then(selectionModifier)
                .onSelectChanged(columnKey, rowKey) { state = state.copy(selected = it) }
                .onFocusChanged { state = state.copy(focused = it.hasFocus) }
                .onHover { state = state.copy(hovered = it) }
                .background(backgroundColor)
                .border(Stroke.Alignment.Outside, borderWidth, colors.borderColor)
                .then(modifier),
        contentAlignment = alignment,
        overflowEnabled = !isHandleHovered,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) { content() }

        if (!isOverflowing) {
            if (onResizeWidth != null) {
                DragHandle(
                    orientation = Orientation.Horizontal,
                    onDrag = onResizeWidth,
                    interactionSource = handleInteractionSource,
                )
            }

            if (onResizeHeight != null) {
                DragHandle(
                    orientation = Orientation.Vertical,
                    onDrag = onResizeHeight,
                    interactionSource = handleInteractionSource,
                )
            }
        }
    }
}

@Composable
private fun OverflowBoxScope.TableCellText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    fontStyle: FontStyle?,
    fontWeight: FontWeight?,
    fontFamily: FontFamily?,
    letterSpacing: TextUnit,
    textDecoration: TextDecoration?,
    textAlign: TextAlign,
    lineHeight: TextUnit,
    overflow: TextOverflow,
    softWrap: Boolean,
    maxLines: Int,
    onTextLayout: (TextLayoutResult) -> Unit,
    leadingIcon: (@Composable () -> Unit)?,
    trailingIcon: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    style: TextStyle = JewelTheme.defaultTextStyle,
) {
    if (leadingIcon != null) {
        Box(Modifier.size(16.dp).align(Alignment.CenterStart), contentAlignment = Alignment.Center) { leadingIcon() }
    }

    val padding = if (leadingIcon != null || trailingIcon != null) 16.dp else 0.dp

    Text(
        text = text,
        modifier = Modifier.padding(start = padding, end = padding).then(modifier),
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        onTextLayout = onTextLayout,
        style = style,
    )

    if (trailingIcon != null) {
        Box(Modifier.size(16.dp).align(Alignment.CenterEnd), contentAlignment = Alignment.Center) { trailingIcon() }
    }
}

@Composable
private fun BoxScope.DragHandle(
    orientation: Orientation,
    interactionSource: MutableInteractionSource,
    onDrag: (updatedSize: Dp) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 4.dp,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    var parentSize by remember { mutableStateOf(DpSize.Unspecified) }

    Box(
        modifier
            .onGloballyPositioned { coordinates ->
                parentSize =
                    with(density) { coordinates.parentCoordinates?.size?.toSize()?.toDpSize() ?: DpSize.Unspecified }
            }
            .thenIf(orientation == Orientation.Horizontal) {
                align(Alignment.CenterEnd)
                    .width(size)
                    .height(parentSize.height)
                    .graphicsLayer(translationX = with(density) { (size / 2).toPx() })
            }
            .thenIf(orientation == Orientation.Vertical) {
                align(Alignment.BottomCenter)
                    .height(size)
                    .width(parentSize.width)
                    .graphicsLayer(translationY = with(density) { (size / 2).toPx() })
            }
            .hoverable(interactionSource)
            .pointerHoverIcon(
                PointerIcon(
                    Cursor(
                        if (orientation == Orientation.Horizontal) Cursor.E_RESIZE_CURSOR else Cursor.S_RESIZE_CURSOR
                    )
                )
            )
            .draggable(
                orientation = orientation,
                state =
                    rememberDraggableState {
                        with(density) {
                            val dragChange =
                                it.toDp() *
                                    if (
                                        orientation == Orientation.Horizontal && layoutDirection == LayoutDirection.Ltr
                                    ) {
                                        -1
                                    } else {
                                        1
                                    }

                            when (orientation) {
                                Orientation.Horizontal -> (parentSize.width + dragChange).coerceAtLeast(0.dp)
                                Orientation.Vertical -> (parentSize.height + dragChange).coerceAtLeast(0.dp)
                            }
                        }
                    },
            )
            .pointerInput(orientation) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onDrag(
                        with(density) {
                            val dragChange =
                                if (orientation == Orientation.Horizontal) {
                                        if (layoutDirection == LayoutDirection.Ltr) {
                                            change.position.x
                                        } else {
                                            -change.position.x
                                        }
                                    } else {
                                        change.position.y
                                    }
                                    .toDp()

                            when (orientation) {
                                Orientation.Horizontal -> (parentSize.width + dragChange).coerceAtLeast(0.dp)
                                Orientation.Vertical -> (parentSize.height + dragChange).coerceAtLeast(0.dp)
                            }
                        }
                    )
                }
            }
    )
}

/**
 * Immutable bitmask-backed state for a table cell.
 *
 * A [TableCellState] contains flags describing common UI states for a cell: focused, hovered and selected. Use [of] to
 * create an instance and [copy] to derive a new one by changing selected flags.
 */
@Immutable
@JvmInline
@Stable
public value class TableCellState(public val state: ULong) {
    /** Whether the cell is currently focused. */
    public val isFocused: Boolean
        get() = state and CommonStateBitMask.Focused != 0UL

    /** Whether the cell is currently hovered by the pointer. */
    public val isHovered: Boolean
        get() = state and CommonStateBitMask.Hovered != 0UL

    /** Whether the cell is currently selected. */
    public val isSelected: Boolean
        get() = state and CommonStateBitMask.Selected != 0UL

    /**
     * Returns a copy of this [TableCellState] with optionally updated flags.
     *
     * @param focused whether the cell is focused.
     * @param hovered whether the cell is hovered.
     * @param selected whether the cell is selected.
     */
    public fun copy(
        focused: Boolean = isFocused,
        hovered: Boolean = isHovered,
        selected: Boolean = isSelected,
    ): TableCellState = of(focused = focused, hovered = hovered, selected = selected)

    override fun toString(): String =
        "TableCellState(state=$state, isFocused=$isFocused, isHovered=$isHovered, isSelected=$isSelected)"

    public companion object {
        /**
         * Creates a [TableCellState] with the provided flags.
         *
         * @param focused whether the cell is focused.
         * @param hovered whether the cell is hovered.
         * @param selected whether the cell is selected.
         */
        public fun of(focused: Boolean = false, hovered: Boolean = false, selected: Boolean = false): TableCellState =
            TableCellState(
                (if (focused) CommonStateBitMask.Focused else 0UL) or
                    (if (hovered) CommonStateBitMask.Hovered else 0UL) or
                    (if (selected) CommonStateBitMask.Selected else 0UL)
            )
    }
}
