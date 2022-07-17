/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.dataframe

import org.jetbrains.plugins.notebooks.visualization.r.inlays.dataframe.columns.*

/**
 * Converts tab separated string into DataFrame
 * ToDo this class should be partially replaced by CsvTypeParser from HDFS plugin.
 */
class DataFrameCSVAdapter {

  data class ColumnInfo(val name: String, var type: Type<*>? = null)

  companion object {

    /**
     * Cell type currently could be only IntType, DoubleType or StringType.
     * It could also be null, but this case handled out of this function.
     */
    private fun getCellType(data: String): Type<*>? {
      return when {
          data == "null" -> null
          data.toIntOrNull() != null -> IntType
          data.toDoubleOrNull() != null -> DoubleType
          else -> StringType
      }
    }

    /**
     * Calculates updated column type basing on previous column type and cell type.
     * Column type should not be == cellType
     */
    private fun getColumnType(columnType: Type<*>?, cellType: Type<*>?): Type<*>? {

      // If column type is null this means that the type is not defined yet.
      if (columnType == null) {
        return cellType
      }

      return columnType.upgrade(cellType)
    }

    private fun getColumnsInfo(data: String): List<ColumnInfo> {

      val columnsInfo = ArrayList<ColumnInfo>()

      var previous = 0
      for (i in 0 until data.length) {
        if (data[i] != '\n' && data[i] != '\t') {
          continue
        }
        columnsInfo.add(ColumnInfo(data.substring(previous, i)))
        previous = i + 1

        if (data[i] == '\n') {
          break
        }
      }

      var column = 0
      var linesCount = 0
      for (i in previous until data.length) {
        if (data[i] != '\n' && data[i] != '\t') {
          continue
        }

        val cellType: Type<*>?
        if (previous == i) {
          // empty value (special case to skip taking a substring)
          cellType = null
        }
        else {
          // non-empty value
          cellType = getCellType(data.substring(previous, i))
        }

        val columnType = columnsInfo[column].type

        // initially column type is null and should be updated
        if (columnType != cellType) {
          columnsInfo[column].type = getColumnType(columnType, cellType)
        }

        previous = i + 1

        if (data[i] == '\n') {
          column = 0
          linesCount++
        }
        else {
          column++
        }
      }

      // It could be that we have all completely empty or null column. In this case we cannot detect it's type.
      for (columnInfo in columnsInfo) {
        if (columnInfo.type == null) {
          columnInfo.type = StringType
        }
      }

      return columnsInfo
    }

    // We have several ways
    // 1. We can start to fill columns data in the same loop where we are identifying column type and if the type changes, convert processed data.
    // 2. Separate loop for column data
    private fun readData(data: String, columnsInfo: List<ColumnInfo>): ArrayList<Column<*>> {

      val columns = ArrayList<ArrayList<*>>(columnsInfo.size)

      for (i in 0 until columnsInfo.size) {
        columns.add(columnsInfo[i].type!!.createDataArray())
      }

      // Skip first line (header).
      var previous = data.indexOf('\n') + 1

      var column = 0
      for (i in previous until data.length) {

        if (data[i] != '\n' && data[i] != '\t') {
          continue
        }

        columnsInfo[column].type!!.appendToDataArray(columns[column], data.substring(previous, i))

        previous = i + 1

        if (data[i] == '\n') {
          // fill last columns if data not exists (even for incorrect csv file all columns should contain same count of elements)
          while (column + 1 < columnsInfo.size) {
            column++
            columnsInfo[column].type!!.appendToDataArray(columns[column], "")
          }
          column = 0
        }
        else {
          column++
        }
      }

      val realColumns = ArrayList<Column<*>>(columnsInfo.size)
      for (i in 0 until columnsInfo.size) {
        realColumns.add(columnsInfo[i].type!!.createDataColumn(columnsInfo[i].name, columns[i]))
      }

      return realColumns
    }

    /**
     * Data should be "tab separated lines" like:
     *
     * age	job	marital	education	balance
     * 30	unemployed	married	primary	1787
     * 33	services	married	secondary	4789
     *
     * In the case of null values we will get null as string.
     *
     * first_column	second_column
     * s	1
     * null	2
     * sa	null
     *
     * Currently we have a problems when data contains /t. In this case data processing breaks down.
     *
     * First line are always header, others - data.
     */
    fun fromCsvString(data: String): DataFrame {
      var innerData =  data
      // valid tsv string always ends with /n, but somehow zeppelin sends us data without /n
      if(!data.isEmpty() && data[data.length-1] != '\n') {
        innerData += '\n'
      }

      val columnsInfo = getColumnsInfo(innerData)
      val res = readData(innerData, columnsInfo)
      return DataFrameImpl(res)
    }
  }
}