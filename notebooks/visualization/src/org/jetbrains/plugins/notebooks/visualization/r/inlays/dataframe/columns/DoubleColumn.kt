/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.dataframe.columns

class DoubleColumn(name: String, data: ArrayList<Double>) : Column<Double>(name, data) {

  override val type: Type<Double>
    get() = DoubleType

  override val isNumerical = true

  private var lazyRange : ClosedRange<Double>? = null

  override fun isNotNull(index: Int): Boolean = !data[index].isNaN()
  override fun isNull(index: Int): Boolean = data[index].isNaN()

  override fun getDouble(index: Int): Double {
    return data[index]
  }

  override fun getComparator(descendant: Boolean): Comparator<Int> {
    return if (descendant)
      Comparator { i1, i2 -> this[i2].compareTo(this[i1]) }
    else
      Comparator { i1, i2 -> this[i1].compareTo(this[i2]) }
  }

  override val range: ClosedRange<Double>
    get() {

      if(lazyRange != null) {
        return lazyRange!!
      }

      var min = Double.MAX_VALUE
      var max = Double.MIN_VALUE

      for (i in 0 until data.size) {

        if(data[i].isNaN()) {
          continue
        }

        if (min > data[i]) {
          min = data[i]
        }

        if (max < data[i]) {
          max = data[i]
        }
      }

      lazyRange = min..max
      return lazyRange!!
    }

  override fun iterator(): Iterator<Double> {
    return data.iterator()
  }
}