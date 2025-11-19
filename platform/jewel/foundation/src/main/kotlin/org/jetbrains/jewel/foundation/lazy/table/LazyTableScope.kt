// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy.table

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastRoundToInt

/**
 * Receiver scope for building the contents of a [LazyTable].
 *
 * Use this scope to declare columns and rows and to optionally configure their sizes. The order in which you add
 * columns and rows defines their indices.
 */
public interface LazyTableScope {
    /**
     * Adds a single column to the table.
     *
     * @param key an optional stable and unique key for this column. If `null`, a default key is generated. Supplying a
     *   stable key helps the table preserve state and reuse the column when the data set changes. Note that users can
     *   still provide a key at "cell-level", which will override this value.
     * @param contentType an optional type identifier for this column. Items with the same content type can be reused to
     *   improve performance during scrolling.
     * @param size optional [ColumnSize] constraints that influence the column's minimum and maximum width.
     */
    public fun column(key: Any? = null, contentType: Any? = null, size: ColumnSize? = null)

    /**
     * Adds multiple columns to the table.
     *
     * @param count the number of columns to add.
     * @param key a function that returns a stable key for a column at the given index. If `null`, default keys are
     *   used. Note that users can still provide a key at "cell-level", which will override this value.
     * @param contentType a function that returns a content type for the column at the given index. Columns with the
     *   same type can be reused to optimize scrolling performance. Defaults to `null`.
     * @param size a function that returns [ColumnSize] constraints for the column at the given index. If `null`, the
     *   table decides the size automatically.
     */
    public fun columns(
        count: Int,
        key: ((index: Int) -> Any)? = null,
        contentType: (index: Int) -> Any? = { null },
        size: ((index: Int) -> ColumnSize)? = null,
    )

    /**
     * Adds a single row to the table and defines its cells using the provided [cells] builder.
     *
     * @param key an optional stable and unique key for this row. If `null`, a default key is generated.
     * @param contentType an optional type identifier for this row. Rows with the same type can be reused to optimize
     *   scrolling performance.
     * @param size optional [RowSize] constraints that influence the row's minimum and maximum height.
     * @param cells a builder that is invoked with a [LazyTableRowScope] receiver to define the row's cells.
     */
    public fun row(
        key: Any? = null,
        contentType: Any? = null,
        size: RowSize? = null,
        cells: LazyTableRowScope.() -> Unit,
    )

    /**
     * Adds multiple rows to the table and defines their cells using the provided [cells] builder.
     *
     * @param count the number of rows to add.
     * @param key a function that returns a stable key for a row at the given index. If `null`, default keys are used.
     * @param contentType a function that returns a content type for the row at the given index. Rows with the same type
     *   can be reused to optimize scrolling performance. Defaults to `null`.
     * @param size a function that returns [RowSize] constraints for the row at the given index. If `null`, the table
     *   decides the size automatically.
     * @param cells a builder that is invoked with a [LazyTableRowScope] receiver for each row index to define cells.
     */
    public fun rows(
        count: Int,
        key: ((index: Int) -> Any)? = null,
        contentType: (index: Int) -> Any? = { null },
        size: ((index: Int) -> RowSize)? = null,
        cells: LazyTableRowScope.(index: Int) -> Unit,
    )

    /**
     * Describes how a column should be sized within a [LazyTable]. Implementations provide the minimum and maximum
     * width in pixels given the current [IntSize] of the table and the [Density].
     */
    public sealed class ColumnSize {
        /** Returns the minimum width for this column, in pixels. */
        public abstract fun minWidth(size: IntSize, density: Density): Int

        /** Returns the maximum width for this column, in pixels. */
        public abstract fun maxWidth(size: IntSize, density: Density): Int

        /** A column with a fixed width [width]. The same value is used for both minimum and maximum width. */
        public class Fixed(private val width: Dp) : ColumnSize() {
            override fun minWidth(size: IntSize, density: Density): Int = with(density) { width.roundToPx() }

            override fun maxWidth(size: IntSize, density: Density): Int = with(density) { width.roundToPx() }
        }

        /**
         * A column constrained by [minWidth] and [maxWidth].
         *
         * @param minWidth the minimum width for the column.
         * @param maxWidth the maximum width for the column.
         */
        public class Constrained(private val minWidth: Dp = 0.dp, private val maxWidth: Dp = Dp.Infinity) :
            ColumnSize() {
            override fun minWidth(size: IntSize, density: Density): Int = with(density) { minWidth.roundToPx() }

            override fun maxWidth(size: IntSize, density: Density): Int = with(density) { maxWidth.roundToPx() }
        }

        /**
         * A column sized as a [percent] of the available table width. Both minimum and maximum widths are computed from
         * the percentage of [size.width].
         *
         * @param percent the fraction of the table's width to occupy (typically between `0f` and `1f`).
         */
        public class Percent(private val percent: Float) : ColumnSize() {
            override fun minWidth(size: IntSize, density: Density): Int = (size.width * percent).fastRoundToInt()

            override fun maxWidth(size: IntSize, density: Density): Int = (size.width * percent).fastRoundToInt()
        }
    }

    /**
     * Describes how a row should be sized within a [LazyTable]. Implementations provide the minimum and maximum height
     * in pixels given the current [IntSize] of the table and the [Density].
     */
    public sealed class RowSize {
        /** Returns the minimum height for this row, in pixels. */
        public abstract fun minHeight(size: IntSize, density: Density): Int

        /** Returns the maximum height for this row, in pixels. */
        public abstract fun maxHeight(size: IntSize, density: Density): Int

        /** A row with a fixed height [height]. The same value is used for both minimum and maximum height. */
        public class Fixed(private val height: Dp) : RowSize() {
            override fun minHeight(size: IntSize, density: Density): Int = with(density) { height.roundToPx() }

            override fun maxHeight(size: IntSize, density: Density): Int = with(density) { height.roundToPx() }
        }

        /**
         * A row constrained by [minHeight] and [maxHeight].
         *
         * @param minHeight the minimum height for the row.
         * @param maxHeight the maximum height for the row.
         */
        public class Constrained(private val minHeight: Dp = 0.dp, private val maxHeight: Dp = Dp.Infinity) :
            RowSize() {
            override fun minHeight(size: IntSize, density: Density): Int = with(density) { minHeight.roundToPx() }

            override fun maxHeight(size: IntSize, density: Density): Int = with(density) { maxHeight.roundToPx() }
        }

        /**
         * A row sized as a [percent] of the available table height. Both minimum and maximum heights are computed from
         * the percentage of [size.height].
         *
         * @param percent the fraction of the table's height to occupy (typically between `0f` and `1f`).
         */
        public class Percent(public val percent: Float) : RowSize() {
            override fun minHeight(size: IntSize, density: Density): Int = (size.height * percent).fastRoundToInt()

            override fun maxHeight(size: IntSize, density: Density): Int = (size.height * percent).fastRoundToInt()
        }
    }
}

/** Receiver scope used inside [LazyTableScope.row] and [LazyTableScope.rows] to declare the row's cells. */
public interface LazyTableRowScope {
    /**
     * Adds a single cell to the current row.
     *
     * @param key an optional stable key provider for the cell. If `null`, a default key is used.
     * @param contentType an optional type identifier for this cell. Cells with the same type can be reused.
     * @param content the composable content of the cell. It is invoked with a [LazyTableCellScope] receiver.
     */
    public fun cell(key: Any? = null, contentType: Any? = null, content: @Composable LazyTableCellScope.() -> Unit)

    /**
     * Adds multiple cells to the current row.
     *
     * @param count the number of cells to add.
     * @param key a function that returns a stable key for the cell at the given index. If `null`, default keys are
     *   used. If not defined, it will attempt to fall-back to the key defined in the 'column' definition from the
     *   'LazyTableScope'.
     * @param contentType a function that returns a content type for the cell at the given index. Cells with the same
     *   type can be reused to optimize performance.
     * @param content the composable content for a cell at a given [index]. It is invoked with a [LazyTableCellScope]
     *   receiver.
     */
    public fun cells(
        count: Int,
        key: ((index: Int) -> Any)? = null,
        contentType: (index: Int) -> Any? = { null },
        content: @Composable LazyTableCellScope.(index: Int) -> Unit,
    )
}

/**
 * Scope available when composing a single table cell.
 *
 * Provides access to the [tableState], the cell [position] (column and row indices), and the cell [key].
 */
public interface LazyTableCellScope : LazyTableItemScope {
    /** The [LazyTableState] associated with the enclosing table. */
    public val tableState: LazyTableState

    /**
     * The zero-based position of this cell as [IntOffset], where [IntOffset.x] is the column index and [IntOffset.y] is
     * the row index.
     */
    public val position: IntOffset

    /** A pair of keys representing, respectively, the column key and the row key for this cell. */
    public val key: Pair<Any?, Any?>
}
