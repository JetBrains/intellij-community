/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.dataframe

import org.jetbrains.plugins.notebooks.visualization.r.inlays.dataframe.columns.*
import java.awt.Dimension

class DataFrameImpl(private val columns: ArrayList<Column<*>>) : DataFrame() {

    override val dim: Dimension
        get() = Dimension(columns.size, columns[0].size)


    override fun getColumns(): List<Column<*>> {
        return columns
    }

    override operator fun get(columnName: String): Column<*> {
        return columns.find { column -> column.name == columnName }!!
    }

    override operator fun get(columnIndex: Int): Column<*> {
        return columns[columnIndex]
    }

    override fun has(columnName: String): Boolean {
        return columns.find { column -> column.name == columnName } != null
    }

    override fun sortBy(columnName: String, sortDescending: Boolean) {

        val sortingColumn = get(columnName)

        // sort indices according sorting column
        val indices = IntArray(columns[0].size) { index -> index }
        val sortedIndices = indices.sortedWith(sortingColumn.getComparator(sortDescending))

        // resort all columns according indices
        for (i in 0 until columns.size) {
            when (columns[i].type) {
                is IntType -> {
                    val intColumn = columns[i] as IntColumn
                    val data = ArrayList<Int>(intColumn.size)
                    (0 until sortedIndices.size).forEach { data.add(intColumn[sortedIndices[it]]) }
                    columns[i] = IntColumn(columns[i].name, data)
                }
                is DoubleType -> {
                    val doubleColumn = columns[i] as DoubleColumn
                    val data = ArrayList<Double>(doubleColumn.size)
                    (0 until sortedIndices.size).forEach { data.add(doubleColumn[sortedIndices[it]]) }
                    columns[i] = DoubleColumn(columns[i].name, data)
                }
                is StringType -> {
                    val stringColumn = columns[i] as StringColumn
                    val data = ArrayList<String?>(stringColumn.size)
                    (0 until sortedIndices.size).forEach { data.add(stringColumn[sortedIndices[it]]) }
                    columns[i] = StringColumn(columns[i].name, data)
                }
                else -> throw Exception("Unsupported column type ${columns[i].type} in sorting.")
            }
        }
    }

    override fun groupBy(columnName: String): DataFrame {

        val groupingColumn = columns.find { column -> column.name == columnName }!!

        // Grouping will not work currently on any Array-type column
        if(groupingColumn.type.isArray()) {
            throw Exception("Currently cannot group on array-type columns")
        }

        val alreadyProcessed = HashMap<Any?, Int>()

        val indexes = IntArray(groupingColumn.size)

        for ((i, value) in groupingColumn.withIndex()) {

            var pos = alreadyProcessed[value]

            if(pos == null) {
                pos = alreadyProcessed.size
                alreadyProcessed[value] = pos
            }

            indexes[i] = pos
        }

        val newColumnsSize = alreadyProcessed.size


        val newColumns = ArrayList<Column<*>>()

        for(column in columns) {

            if(column.name == columnName) {

                // Grouping column preserves their type

              val newColumn = when(column.type) {
                    is IntType -> {
                        val newData = IntArray(newColumnsSize)
                        alreadyProcessed.forEach{(key, value) -> newData[value] = key as Int}
                        IntColumn(column.name, newData.toCollection(ArrayList()))
                    }

                    is DoubleType -> {
                        val newData = DoubleArray(newColumnsSize)
                        alreadyProcessed.forEach{(key, value) -> newData[value] = key as Double}
                        DoubleColumn(column.name, newData.toCollection(ArrayList()))
                    }

                    is StringType -> {
                        val newData = Array(newColumnsSize) {""}
                        alreadyProcessed.forEach{(key, value) -> newData[value] = key as String}
                        StringColumn(column.name, newData.toCollection(ArrayList()))
                    }
                    else -> throw Exception("Unsupported column type ${column.type} in grouping.")
                }

                newColumns.add(newColumn)

            } else {

                val newColumn = when(column.type) {
                    is IntType -> {
                        val newData = ArrayList<ArrayList<Int>>(newColumnsSize)
                        for(i in 0 until newColumnsSize) {
                            newData.add(ArrayList())
                        }

                        for ((i, value) in (column as IntColumn).withIndex()) {
                            newData[indexes[i]].add(value)
                        }
                        IntArrayColumn(column.name, newData)
                    }

                    is DoubleType -> {
                        val newData = ArrayList<ArrayList<Double>>(newColumnsSize)
                        for(i in 0 until newColumnsSize) {
                            newData.add(ArrayList())
                        }

                        for ((i, value) in (column as DoubleColumn).withIndex()) {
                            newData[indexes[i]].add(value)
                        }
                        DoubleArrayColumn(column.name, newData)
                    }

                    is StringType -> {
                        val newData = ArrayList<ArrayList<String?>>(newColumnsSize)
                        for(i in 0 until newColumnsSize) {
                            newData.add(ArrayList())
                        }

                        for ((i, value) in (column as StringColumn).withIndex()) {
                            newData[indexes[i]].add(value)
                        }
                        StringArrayColumn(column.name, newData)
                    }
                    else -> throw Exception("Unsupported column type ${column.type} in grouping.")
                }

                newColumns.add(newColumn)
            }
        }

        return DataFrameImpl(newColumns)
    }
}