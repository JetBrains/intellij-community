/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.dataframe.columns

class DoubleArrayColumn(name: String, data: ArrayList<ArrayList<Double>>) : Column<ArrayList<Double>>(name, data) {

  override val type: Type<ArrayList<Double>>
    get() = DoubleArrayType

  override fun isNotNull(index: Int): Boolean = true
  override fun isNull(index: Int): Boolean = false

  override fun getDouble(index: Int): Double {
    return index.toDouble()
  }

  override fun getComparator(descendant: Boolean): Comparator<Int> {
    throw Exception("Should not be called")
  }

  override val range: ClosedRange<Double>
    get() {
      return 0.0..size.toDouble()
    }

  override fun iterator(): Iterator<ArrayList<Double>> {
    return data.iterator()
  }
}