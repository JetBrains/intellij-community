// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy.table

import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

internal class LazyTableMeasureResult(
    val firstFloatingCell: LazyTableMeasuredItem?,
    val firstFloatingCellScrollOffset: IntOffset,
    val canVerticalScrollForward: Boolean,
    val canVerticalScrollBackward: Boolean,
    val consumedVerticalScroll: Float,
    val canHorizontalScrollForward: Boolean,
    val canHorizontalScrollBackward: Boolean,
    val consumedHorizontalScroll: Float,
    measureResult: MeasureResult,
    override val floatingItemsInfo: List<LazyTableItemInfo>,
    override val pinnedColumnsInfo: List<LazyTableItemInfo>,
    override val pinnedRowsInfo: List<LazyTableItemInfo>,
    override val pinnedItemsInfo: List<LazyTableItemInfo>,
    override val viewportStartOffset: IntOffset,
    override val viewportEndOffset: IntOffset,
    override val viewportCellSize: IntSize,
    override val columns: Int,
    override val rows: Int,
    override val pinnedColumns: Int,
    override val pinnedRows: Int,
    override val pinnedColumnsWidth: Int,
    override val pinnedRowsHeight: Int,
    override val horizontalSpacing: Int,
    override val verticalSpacing: Int,
) : LazyTableLayoutInfo, MeasureResult by measureResult {
    override val viewportSize: IntSize
        get() = IntSize(width, height)
}
