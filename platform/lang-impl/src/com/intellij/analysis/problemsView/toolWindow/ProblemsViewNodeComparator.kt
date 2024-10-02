// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.openapi.util.text.StringUtil.naturalCompare
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class ProblemsViewNodeComparator(
  private val sortFoldersFirst: Boolean,
  private val sortBySeverity: Boolean,
  private val sortByName: Boolean)
  : Comparator<Node?> {

  override fun compare(node1: Node?, node2: Node?): Int {
    if (node1 === node2) return 0
    if (node1 == null) return +1
    if (node2 == null) return -1
    if (node1 is ProblemNode && node2 is ProblemNode) return compare(node1, node2)
    if (node1 is ProblemNode) return -1 // problem node before other nodes
    if (node2 is ProblemNode) return +1
    if (sortFoldersFirst && node1 is FileNode && node2 is FileNode) {
      if (node1.file.isDirectory && !node2.file.isDirectory) return -1
      if (!node1.file.isDirectory && node2.file.isDirectory) return +1
    }
    return naturalCompare(node1.name, node2.name)
  }

  private fun compare(node1: ProblemNode, node2: ProblemNode): Int {
    if (sortBySeverity) {
      val result = node2.severity.compareTo(node1.severity)
      if (result != 0) return result
    }
    return if (sortByName) {
      val result = naturalCompare(node1.text, node2.text)
      if (result != 0) result else comparePosition(node1, node2)
    }
    else {
      val result = comparePosition(node1, node2)
      if (result != 0) result else naturalCompare(node1.text, node2.text)
    }
  }

  private fun comparePosition(node1: ProblemNode, node2: ProblemNode): Int {
    val result = node1.line.compareTo(node2.line)
    return if (result != 0) result else node1.column.compareTo(node2.column)
  }
}
