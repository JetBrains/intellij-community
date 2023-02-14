/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.dataframe.columns

import com.intellij.openapi.util.NlsSafe

abstract class Column<T>(@NlsSafe val name: String, protected val data: ArrayList<T>) : Iterable<T>{

    /** Number of rows. */
    val size: Int = data.size

    abstract val type: Type<T>

    open val isNumerical : Boolean = false

    fun toList(): List<T> {
        return data
    }

    operator fun get(index: Int): T {
        return data[index]
    }

    abstract fun isNotNull(index: Int): Boolean

    abstract fun isNull(index: Int): Boolean

    /**
     * Special comparator for data frame sorting functionality.
     */
    abstract fun getComparator(descendant: Boolean) : Comparator<Int>

    /**
     * Returns double representation for the column element.
     * In simple Int and Double cases this is element value itself
     * In the other cases this is index value
     * */
    abstract fun getDouble(index: Int) : Double

    @Suppress("UNCHECKED_CAST")
    fun <T> cast(): Column<T> {
        return this as Column<T>
    }

    /**
     * For numerical column - range from min to max column value,
     * For other columns this is range from 0..size.
     * If numerical column isNotNull only null values, returns 0..0 range.
     */
    abstract val range : ClosedRange<Double>
}

