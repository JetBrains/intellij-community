package com.intellij.database.run.ui.treetable

import com.intellij.database.datagrid.*
import com.intellij.database.run.ui.DataAccessType


interface Node {
  val name: String
  val value: Any?
  fun getChildren(): List<Node>
  fun getChildrenCount(): Int
  fun isLeaf(): Boolean
}

internal sealed class NodeBase<out T : Node> : Node {
  protected abstract fun getWrapper(): ValueWrapper<T>

  override fun getChildren(): List<T> {
    return getWrapper().getChildren()
  }

  override fun getChildrenCount(): Int {
    return getWrapper().getChildCount()
  }

  override fun isLeaf(): Boolean {
    return getWrapper().isLeaf()
  }

  override val value: Any?
    get() = getWrapper().getValue()
}

internal class SimpleNode(override val name: String, private val wrapper: ValueWrapper<Node>) : NodeBase<Node>(), Node {
  override fun getWrapper() = wrapper
}

internal abstract class NodeWithCache<T : Node> : NodeBase<T>() {
  private var w: ValueWrapper<T>? = null

  protected abstract fun createWrapper(): ValueWrapper<T>

  final override fun getWrapper(): ValueWrapper<T> {
    var wrapper = w
    if (wrapper == null) {
      wrapper = createWrapper()
      w = wrapper
    }
    return wrapper
  }

  fun clearCache() {
    w = null
  }
}

internal class ColumnNode(override val name: String,
                          private val grid: DataGrid,
                          private val rowIdx: ModelIndex<GridRow>,
                          val columnIdx: ModelIndex<GridColumn>) : NodeWithCache<Node>() {

  override fun createWrapper(): ValueWrapper<Node> {
    val dataModel = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS)
    val row = dataModel.getRow(rowIdx)
    val value = row?.let { dataModel.getColumn(columnIdx)?.getValue(row) }
    return wrap(value)
  }
}

internal class RowNode(val rowIdx: ModelIndex<GridRow>, private val grid: DataGrid) : NodeWithCache<Node>() {
  override fun createWrapper(): ValueWrapper<Node> {
    return ColumnsWrapper(grid, rowIdx)
  }

  fun clearCachedValue() {
    (getWrapper() as LazyValueWrapper<*>).clearCachedValue()
  }

  override val name: String
    get() = GridUtil.getRowName(grid, rowIdx.asInteger())
}

fun dfs(root: Node, predicate: (List<Node>, Node) -> Boolean): Pair<List<Node>, Node>? {
  return dfs(root, mutableListOf()).firstOrNull { (path, node) -> predicate(path, node) }
}

fun dfs(root: Node): Sequence<Pair<List<Node>, Node>> = dfs(root, mutableListOf())

private fun dfs(root: Node, path: MutableList<Node>): Sequence<Pair<List<Node>, Node>> {
  return sequence {
    path.add(root)
    yield(Pair(path, root))
    for (child in root.getChildren()) {
      yieldAll(dfs(child, path))
    }
    path.removeAt(path.size - 1)
  }
}
