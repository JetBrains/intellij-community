package com.intellij.database.run.ui.treetable

import com.intellij.database.datagrid.*
import com.intellij.database.extractors.toJson
import com.intellij.database.run.ReservedCellValue
import com.intellij.database.run.ui.DataAccessType
import com.intellij.openapi.util.Ref

interface ValueWrapper<out T : Node> {
  fun getValue(): Any?
  fun getChildren(): List<T>
  fun getChildCount(): Int
  fun isLeaf(): Boolean
}

internal abstract class LazyValueWrapper<out T : Node> : ValueWrapper<T> {
  private val children = lazy { createChildren() }
  private var value: Ref<Any?>? = null
  private val isLeafCached = lazy { estimateIsLeaf() }

  protected abstract fun createChildren(): List<T>
  protected abstract fun createValue(): Any?
  protected abstract fun estimateChildCount(): Int
  protected abstract fun estimateIsLeaf(): Boolean

  final override fun getChildren(): List<T> {
    return children.value
  }

  final override fun getValue(): Any? {
    var v = value
    if (v == null) {
      v = Ref(createValue())
      value = v
    }
    return v.get()
  }

  fun clearCachedValue() {
    value = null
  }

  final override fun getChildCount(): Int {
    return if (children.isInitialized()) children.value.size
    else estimateChildCount()
  }

  final override fun isLeaf(): Boolean {
    return isLeafCached.value
  }
}

internal class MapWrapper(private val map: Map<Any?, Any?>) : LazyValueWrapper<SimpleNode>() {
  override fun createValue(): Any {
    return if (map is LinkedHashMap<Any?, Any?>) map else LinkedHashMap(map)
  }

  override fun createChildren(): List<SimpleNode> {
    return map.entries.map { (key, value) ->
      SimpleNode(key?.toString() ?: "", wrap(value))
    }
  }

  override fun estimateChildCount(): Int = map.size
  override fun estimateIsLeaf(): Boolean = map.isEmpty()
}

internal class SimpleWrapper(private val v: Any?) : LazyValueWrapper<SimpleNode>() {
  override fun createValue(): Any? = v
  override fun createChildren(): List<SimpleNode> = emptyList()
  override fun estimateChildCount() = 0
  override fun estimateIsLeaf(): Boolean = true
}

internal class ListWrapper(private val v: List<Any?>) : LazyValueWrapper<SimpleNode>() {
  override fun createValue(): Any = v

  override fun createChildren(): List<SimpleNode> {
    return v.mapIndexed { index, v -> SimpleNode(index.toString(), wrap(v)) }
  }

  override fun estimateChildCount(): Int = v.size
  override fun estimateIsLeaf(): Boolean = v.isEmpty()
}

internal class ArrayWrapper(private val v: Array<Any?>) : LazyValueWrapper<SimpleNode>() {
  override fun createValue() = v

  override fun createChildren(): List<SimpleNode> {
    return v.mapIndexed { index, v -> SimpleNode(index.toString(), wrap(v)) }
  }

  override fun estimateChildCount(): Int = v.size
  override fun estimateIsLeaf(): Boolean = v.isEmpty()
}

internal class ColumnsWrapper(private val grid: DataGrid, private val rowIdx: ModelIndex<GridRow>) : LazyValueWrapper<Node>() {
  override fun createValue(): String {
    val formatter = grid.objectFormatter
    val mode = GridHelper.get(grid).getDefaultMode()
    return children()
      .take(MAX_NUMBER_OF_VALUES_TO_SHOW)
      .joinToString(prefix = "{", postfix = "}") { (column, value) ->
        val json = toJson(value, formatter, mode)
        "${toJson(column.name, formatter, mode)}: $json"
      }
  }

  override fun createChildren(): List<Node> {
    return children()
      .mapTo(mutableListOf()) { (column, _) ->
        ColumnNode(column.name, grid, rowIdx, ModelIndex.forColumn(grid, column.columnNumber))
      }
  }

  private fun children(): Sequence<Pair<GridColumn, Any?>> {
    val model = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS)
    val row = model.getRow(rowIdx) ?: return emptySequence()
    return model.columnsAsIterable.asSequence().mapNotNull { column ->
      when (val value = column.getValue(row)) {
        ReservedCellValue.UNSET -> null
        else -> Pair(column, value)
      }
    }
  }

  override fun estimateChildCount(): Int = children().count()
  override fun estimateIsLeaf(): Boolean = !children().any()
}

private const val MAX_NUMBER_OF_VALUES_TO_SHOW = 100

@Suppress("UNCHECKED_CAST")
internal fun wrap(value: Any?): ValueWrapper<SimpleNode> {
  return when (value) {
    is Map<*, *> -> MapWrapper(value as Map<Any?, Any?>)
    is Array<*> -> ArrayWrapper(value as Array<Any?>)
    is List<*> -> ListWrapper(value as List<Any?>)
    else -> SimpleWrapper(value)
  }
}
