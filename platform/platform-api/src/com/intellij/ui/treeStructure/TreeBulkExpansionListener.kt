// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.treeStructure

import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.tree.TreePath

/**
 * Represents a listener for bulk expansion and collapse events of a tree.
 *
 * When a bulk operation is started/ended, one of the four bulk notification methods
 * is called. The `path` parameter for such events will be `null`.
 *
 * For individual expand/collapse events, regular [TreeExpansionListener] methods
 * are called, but instead of [TreeExpansionEvent], [TreeBulkExpansionEvent] instances
 * are passed, and the [TreeBulkExpansionEvent.isBulkOperationInProgress] flag can be
 * checked whether those individual operations are a part of a bulk operation.
 */
interface TreeBulkExpansionListener : TreeExpansionListener {
  fun treeBulkExpansionStarted(event: TreeBulkExpansionEvent) {
  }

  fun treeBulkExpansionEnded(event: TreeBulkExpansionEvent) {
  }

  fun treeBulkCollapseStarted(event: TreeBulkExpansionEvent) {
  }

  fun treeBulkCollapseEnded(event: TreeBulkExpansionEvent) {
  }
}

class TreeBulkExpansionEvent(
  source: Any,
  path: TreePath?,
  val isBulkOperationInProgress: Boolean,
) : TreeExpansionEvent(source, path)
