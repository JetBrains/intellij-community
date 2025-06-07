package com.intellij.database.run.ui.treetable

import com.intellij.database.DataGridBundle
import com.intellij.database.datagrid.*
import com.intellij.database.run.ui.DataAccessType
import com.intellij.ui.tree.BaseTreeModel
import com.intellij.ui.treeStructure.treetable.TreeTableModel
import com.intellij.util.ui.ColumnInfo
import javax.swing.JTree
import javax.swing.tree.TreePath

/**
 * @author Liudmila Kornilova
 */
class GridTreeTableModel(private val myGrid: DataGrid) : BaseTreeModel<Node>(), TreeTableModel, GridModel.Listener<GridRow, GridColumn> {

  private val myColumns: Array<ColumnInfo<Node, *>> = arrayOf(FieldNameColumnInfo(), ValueColumnInfo())
  private val root = object : NodeWithCache<RowNode>() {
    override val name = "ROOT"

    override fun createWrapper() = object : LazyValueWrapper<RowNode>() {
      override fun createValue(): Any? = null

      override fun createChildren(): List<RowNode> {
        val model = myGrid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS)
        return model.rowIndices.asList().map { idx ->
          RowNode(idx, myGrid)
        }
      }

      override fun estimateChildCount(): Int {
        return myGrid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).rowIndices.size()
      }

      override fun estimateIsLeaf(): Boolean = false
    }
  }

  override fun isLeaf(node: Any?): Boolean {
    return (node as? Node)?.isLeaf() ?: super.isLeaf(node)
  }

  override fun getChildCount(node: Any?): Int {
    return (node as? Node)?.let { node.getChildrenCount() } ?: 0
  }

  override fun getColumnCount(): Int {
    return myColumns.size
  }

  override fun getColumnName(column: Int): String {
    return myColumns[column].name
  }

  override fun getValueAt(node: Any, column: Int): Any? {
    return myColumns[column].valueOf(node as Node)
  }

  override fun getColumnClass(column: Int): Class<*> {
    return myColumns[column].columnClass
  }

  override fun isCellEditable(node: Any, column: Int): Boolean {
    return myColumns[column].isCellEditable(node as Node)
  }

  override fun setValueAt(aValue: Any, node: Any, column: Int) = Unit

  override fun setTree(tree: JTree) = Unit

  override fun getRoot(): Node {
    return root
  }

  override fun getChildren(parent: Any?): List<Node> {
    return (parent as? Node)?.getChildren() ?: emptyList()
  }

  override fun columnsAdded(columns: ModelIndexSet<GridColumn>) {
    columnsChanged()
  }

  override fun columnsRemoved(columns: ModelIndexSet<GridColumn>) {
    columnsChanged()
  }

  private fun columnsChanged() {
    val rows = root.getChildren()
    val indices = IntArray(rows.size) { it }
    rows.forEach { it.clearCache() }
    treeNodesChanged(TreePath(root), indices, null)
  }

  override fun rowsAdded(rows: ModelIndexSet<GridRow>) {
    val indices = rows.asArray()
    root.clearCache()
    treeNodesInserted(TreePath(root), indices, null)
  }

  override fun rowsRemoved(rows: ModelIndexSet<GridRow>) {
    val indices = rows.asArray()
    root.clearCache()
    treeNodesRemoved(TreePath(root), indices, null)
  }

  override fun cellsUpdated(rows: ModelIndexSet<GridRow>, columns: ModelIndexSet<GridColumn>, place: GridRequestSource.RequestPlace?) {
    val rowSet = rows.asArray().toSet()
    val columnSet = columns.asArray().toSet()
    root.getChildren().asSequence()
      .filter { row -> rowSet.contains(row.rowIdx.asInteger()) }
      .forEach { row ->
        row.clearCachedValue()
        dfs(row)
          .filter { (_, cell) -> cell is ColumnNode && columnSet.contains(cell.columnIdx.asInteger()) }
          .forEach { (_, cell) -> (cell as ColumnNode).clearCache() }
      }

    val paths = root.getChildren().asSequence()
      .filter { row -> rowSet.contains(row.rowIdx.asInteger()) }
      .flatMap { row -> row.getChildren()
        .filter { column -> column is ColumnNode && columnSet.contains(column.columnIdx.asInteger()) }
        .map { column -> TreePath(root).pathByAddingChild(row).pathByAddingChild(column) }
      }.toList()

    if (paths.size > 1) {
      treeStructureChanged(TreePath(root), null, null)
    }
    else if (paths.size == 1) {
      treeStructureChanged(paths[0], null, null)
    }
  }

  private class FieldNameColumnInfo : ColumnInfo<Node, String>(DataGridBundle.message("column.name.field")) {
    override fun valueOf(o: Node): String = o.name
    override fun getColumnClass(): Class<*> = TreeTableModel::class.java
  }

  private class ValueColumnInfo : ColumnInfo<Node, Any>(DataGridBundle.message("column.name.value")) {
    override fun valueOf(o: Node): Any? = o.value
    override fun getColumnClass(): Class<*> = Any::class.java
  }
}
