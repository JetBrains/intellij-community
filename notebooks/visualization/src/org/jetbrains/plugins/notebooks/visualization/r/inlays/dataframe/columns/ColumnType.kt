/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.dataframe.columns

sealed class Type<out T> {
  open fun mkNullable(): Type<T?> = this
  fun isNullable(): Boolean = mkNullable() == this
  open fun isArray(): Boolean = false

  /** Upgrade method is used in loading csv data from Zeppelin. */
  open fun upgrade(type: Type<*>? ) : Type<*>? {
     return null
  }

  open fun createDataArray() : ArrayList<*> {
    return ArrayList<T>()
  }

  abstract fun createDataColumn(name: String,data: ArrayList<*>) : Column<*>

  abstract fun appendToDataArray(array: ArrayList<*>, data: String)
}

object IntArrayType : Type<ArrayList<Int>>() {
  override fun isArray(): Boolean = true

  @Suppress("UNCHECKED_CAST")
  override fun createDataColumn(name: String, data: ArrayList<*>) : Column<*> {
    return IntArrayColumn(name, data as ArrayList<ArrayList<Int>>)
  }

  override fun appendToDataArray(array: ArrayList<*>, data: String) {
    throw Exception("Should not be called")
  }
}

object DoubleArrayType : Type<ArrayList<Double>>() {
  override fun isArray(): Boolean = true

  @Suppress("UNCHECKED_CAST")
  /** data should be ArrayList<ArrayList<Double>> */
  override fun createDataColumn(name: String, data: ArrayList<*>) : Column<*> {
    return DoubleArrayColumn(name, data as ArrayList<ArrayList<Double>>)
  }

  override fun appendToDataArray(array: ArrayList<*>, data: String) {
    throw Exception("Should not be called")
  }
}

object StringArrayType : Type<ArrayList<String?>>() {
  override fun isArray(): Boolean = true

  @Suppress("UNCHECKED_CAST")
  /** data should be ArrayList<ArrayList<String?>> */
  override fun createDataColumn(name: String, data: ArrayList<*>) : Column<*> {
    return StringArrayColumn(name, data as ArrayList<ArrayList<String?>>)
  }

  override fun appendToDataArray(array: ArrayList<*>, data: String) {
    throw Exception("Should not be called")
  }
}

object IntType : Type<Int>() {

  /** IntType could be upgraded to
   * DoubleType if incoming type is DoubleType
   * StringType if incoming type is any other. */
  override fun upgrade(type: Type<*>? ) : Type<*>? {
    return when(type) {
      null -> IntType
      IntType -> IntType
      DoubleType -> DoubleType
      else -> StringType
    }
  }

  @Suppress("UNCHECKED_CAST")
  /** data should be ArrayList<Int> */
  override fun createDataColumn(name: String, data: ArrayList<*>) : Column<*> {
    return IntColumn(name, data as ArrayList<Int>)
  }

  @Suppress("UNCHECKED_CAST")
  /** data should be ArrayList<Int> */
  override fun appendToDataArray(array: ArrayList<*>, data: String) {
    (array as ArrayList<Int>).add(if( data.isEmpty() || data == "null") Int.MIN_VALUE else data.toInt())
  }
}

object DoubleType : Type<Double>() {

  override fun upgrade(type: Type<*>? ) : Type<*>? {
    return when(type) {
      null -> DoubleType
      IntType -> DoubleType
      DoubleType ->DoubleType
      else -> StringType
    }
  }

  @Suppress("UNCHECKED_CAST")
  /** data should be ArrayList<Double> */
  override fun createDataColumn(name: String, data: ArrayList<*>) : Column<*> {
    return DoubleColumn(name, data as ArrayList<Double>)
  }

  @Suppress("UNCHECKED_CAST")
  /** data should be ArrayList<Double> */
  override fun appendToDataArray(array: ArrayList<*>, data: String) {
    (array as ArrayList<Double>).add(if( data.isEmpty() || data == "null") Double.NaN else data.toDouble())
  }
}

object StringType : Type<String?>() {

  override fun upgrade(type: Type<*>? ) : Type<*>? {
    return StringType
  }

  @Suppress("UNCHECKED_CAST")
  /** data should be ArrayList<String?> */
  override fun createDataColumn(name: String, data: ArrayList<*>) : Column<*> {
    return StringColumn(name, data as ArrayList<String?>)
  }

  @Suppress("UNCHECKED_CAST")
  /** data should be ArrayList<String?> */
  override fun appendToDataArray(array: ArrayList<*>, data: String) {
    (array as ArrayList<String?>).add(data)
  }
}
