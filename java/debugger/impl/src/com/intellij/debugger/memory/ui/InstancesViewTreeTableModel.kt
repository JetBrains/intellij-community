// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.ui

import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.memory.ui.SizedReferenceInfo.SizedValueDescriptor
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.treeStructure.treetable.TreeTableModel
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import com.intellij.xdebugger.memory.ui.InstancesTree
import javax.swing.JTree
import javax.swing.SwingConstants
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.tree.DefaultTreeModel

class InstancesViewTreeTableModel(val tree: InstancesTree) : DefaultTreeModel(tree.root), TreeTableModel, TreeModelListener {
  init {
    tree.treeModel.addTreeModelListener(this)
  }

  override fun getRoot(): Any {
    return tree.treeModel.root
  }

  override fun getChild(parent: Any?, index: Int): Any {
    return tree.treeModel.getChild(parent, index)
  }

  override fun getChildCount(parent: Any?): Int {
    return tree.treeModel.getChildCount(parent)
  }

  override fun isLeaf(node: Any?): Boolean {
    return tree.treeModel.isLeaf(node)
  }

  override fun getIndexOfChild(parent: Any?, child: Any?): Int {
    return tree.treeModel.getIndexOfChild(parent, child)
  }

  override fun getColumnCount(): Int =
    Columns.values().size

  override fun getColumnName(column: Int): String =
    Columns.values()[column].getColumnName()

  override fun getColumnClass(column: Int): Class<*> =
    Columns.values()[column].getColumnClass()

  override fun getValueAt(node: Any?, column: Int): Any? =
    Columns.values()[column].getValue(node)

  enum class Columns {
    NODE {
      override fun getValue(node: Any?): Any? = null
      override fun getColumnClass(): Class<*> = TreeTableModel::class.java
      override fun getColumnName(): String = "Instances"
    },
    SHALLOW_SIZE {
      override fun getValue(node: Any?): Long? =
        node?.getSizedValueDescriptor()?.shallowSize

      override fun getColumnClass(): Class<*> = java.lang.Long::class.java
      override fun getColumnName(): String = "Shallow Size"
    },
    RETAINED_SIZE {
      override fun getValue(node: Any?): Long? =
        node?.getSizedValueDescriptor()?.retainedSize

      override fun getColumnClass(): Class<*> = java.lang.Long::class.java
      override fun getColumnName(): String = "Retained Size"
    };

    abstract fun getValue(node: Any?): Any?
    abstract fun getColumnClass(): Class<*>

    @NlsContexts.ColumnName
    abstract fun getColumnName(): String
  }

  override fun isCellEditable(node: Any?, column: Int) = false

  override fun setValueAt(aValue: Any?, node: Any?, column: Int) {
  }

  override fun setTree(newTree: JTree?) {
  }

  fun createTableCellRenderer() =
    object : DefaultTableCellRenderer() {
      init {
        horizontalAlignment = SwingConstants.RIGHT
      }

      override fun setValue(value: Any?) {
        if (value !is Long) {
          return super.setValue(value)
        }
        super.setValue(StringUtil.formatFileSize(value))
      }
    }

  override fun treeNodesChanged(e: TreeModelEvent) = fireTreeNodesChanged(e.source, e.path, e.childIndices, e.children)
  override fun treeNodesInserted(e: TreeModelEvent) = fireTreeNodesInserted(e.source, e.path, e.childIndices, e.children)
  override fun treeNodesRemoved(e: TreeModelEvent) = fireTreeNodesRemoved(e.source, e.path, e.childIndices, e.children)
  override fun treeStructureChanged(e: TreeModelEvent) = fireTreeStructureChanged(e.source, e.path, e.childIndices, e.children)
}

private fun Any.getSizedValueDescriptor(): SizedValueDescriptor? {
  if (this !is XValueNodeImpl) return null
  val value = this.valueContainer as? JavaValue ?: return null
  return value.descriptor as? SizedValueDescriptor ?: return null
}
