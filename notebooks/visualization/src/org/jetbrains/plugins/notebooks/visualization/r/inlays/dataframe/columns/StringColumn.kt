/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.dataframe.columns

class StringColumn(name: String, data: ArrayList<String?>) : Column<String?>(name, data) {

  override val type: Type<String?>
    get() = StringType

  override fun isNotNull(index: Int): Boolean = data[index] != null
  override fun isNull(index: Int): Boolean = data[index] == null

  override fun getDouble(index: Int): Double {
    return index.toDouble()
  }

  override fun getComparator(descendant: Boolean): Comparator<Int> {
    return if (descendant)
      Comparator { i1, i2 -> if(this[i2] != null && this[i1] != null) this[i2]!!.compareTo(this[i1]!!) else i2.compareTo(i1) }
    else
      Comparator { i1, i2 -> if(this[i2] != null && this[i1] != null)  this[i1]!!.compareTo(this[i2]!!) else i1.compareTo(i2) }
  }

  override val range: ClosedRange<Double>
    get() {
      return 0.0..size.toDouble()
    }

  override fun iterator(): Iterator<String?> {
    return data.iterator()
  }
}