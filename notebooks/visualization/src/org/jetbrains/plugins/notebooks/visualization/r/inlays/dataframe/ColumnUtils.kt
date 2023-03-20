/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.dataframe

import org.jetbrains.plugins.notebooks.visualization.r.inlays.dataframe.columns.Column

class ColumnUtils {
    companion object {

        /**
         * Checks that the Number is ordered (does not matters ascendant or descendant). Applicable only for numerical columns.
         * Column contains only null values will be ordered.
         */
        fun isColumnOrdered(column: Column<out Number>): Boolean {

            if(column.size < 2) {
                return true
            }

            var prev = -1
            var order: Boolean? = null

            for (i in 0 until column.size) {
                if(column.isNull(i))
                    continue

                if(prev==-1) {
                    prev = i
                    continue
                }

                if(order == null) {
                    order = column[prev].toDouble() <= column[i].toDouble()
                } else {
                    if (order != column[prev].toDouble() <= column[i].toDouble()) {
                        return false
                    }
                }

                prev = i
            }

            return true
        }
    }
}