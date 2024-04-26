// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageDependencies.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.concurrency.Invoker
import com.intellij.util.concurrency.InvokerSupplier
import com.intellij.util.ui.tree.TreeModelListenerList
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener
import javax.swing.tree.TreePath
import kotlin.IllegalStateException

internal class BackgroundTreeModel(delegateProvider: () -> TreeModel) : javax.swing.tree.TreeModel, InvokerSupplier, Disposable {

  private val invoker = Invoker.forBackgroundThreadWithReadAction(this)

  // Can't just delegate listeners to the delegate because they're added early on the EDT.
  private val listeners = TreeModelListenerList()
  private val delegate: TreeModel by lazy {
    delegateProvider().also { result ->
      result.addTreeModelListener(object : TreeModelListener {
        override fun treeNodesChanged(e: TreeModelEvent) {
          listeners.treeNodesChanged(e)
        }

        override fun treeNodesInserted(e: TreeModelEvent) {
          listeners.treeNodesInserted(e)
        }

        override fun treeNodesRemoved(e: TreeModelEvent) {
          listeners.treeNodesRemoved(e)
        }

        override fun treeStructureChanged(e: TreeModelEvent) {
          listeners.treeNodesChanged(e)
        }
      })
    }
  }

  override fun getInvoker(): Invoker = invoker

  override fun dispose() {
    listeners.clear()
  }

  override fun getRoot(): Any {
    reportInvalidThread()
    val root = delegate.root
    (root as? PackageDependenciesNode?)?.update()
    return root
  }

  override fun getChild(parent: Any?, index: Int): Any {
    reportInvalidThread()
    val child = delegate.getChild(parent, index)
    (child as? PackageDependenciesNode?)?.update()
    return child
  }

  override fun getChildCount(parent: Any?): Int {
    reportInvalidThread()
    (parent as? PackageDependenciesNode?)?.update()
    return delegate.getChildCount(parent)
  }

  override fun isLeaf(node: Any?): Boolean {
    reportInvalidThread()
    (node as? PackageDependenciesNode?)?.update()
    return delegate.isLeaf(node)
  }

  override fun valueForPathChanged(path: TreePath?, newValue: Any?) {
    reportInvalidThread()
    delegate.valueForPathChanged(path, newValue)
  }

  override fun getIndexOfChild(parent: Any?, child: Any?): Int {
    reportInvalidThread()
    (parent as? PackageDependenciesNode?)?.update()
    return delegate.getIndexOfChild(parent, child)
  }

  override fun addTreeModelListener(l: TreeModelListener) {
    listeners.add(l)
  }

  override fun removeTreeModelListener(l: TreeModelListener) {
    listeners.remove(l)
  }

  private fun reportInvalidThread() {
    if (!invoker.isValidThread) {
      LOG.error(IllegalStateException("BackgroundTreeModel is used from an invalid thread: ${Thread.currentThread()}"))
    }
  }

}

private val LOG = logger<BackgroundTreeModel>()
