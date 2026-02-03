// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    if (node1 is ProblemNodeI && node2 is ProblemNodeI) return compareI(node1, node2)
    if (node1 is ProblemNodeI) return -1 // problem node before other nodes
    if (node2 is ProblemNodeI) return +1
    if (sortFoldersFirst && node1 is FileNode && node2 is FileNode) {
      if (node1.file.isDirectory && !node2.file.isDirectory) return -1
      if (!node1.file.isDirectory && node2.file.isDirectory) return +1
    }
    return naturalCompare(node1.name, node2.name)
  }

  private fun compareI(node1: ProblemNodeI, node2: ProblemNodeI): Int {
    if (sortBySeverity) {
      val result = node2.getSeverity().compareTo(node1.getSeverity())
      if (result != 0) return result
    }
    return if (sortByName) {
      val result = naturalCompare(node1.getText(), node2.getText())
      if (result != 0) result else comparePosition(node1, node2)
    }
    else {
      val result = comparePosition(node1, node2)
      if (result != 0) result else naturalCompare(node1.getText(), node2.getText())
    }
  }

  private fun comparePosition(node1: ProblemNodeI, node2: ProblemNodeI): Int {
    val result = node1.getLine().compareTo(node2.getLine())
    return if (result != 0) result else node1.getColumn().compareTo(node2.getColumn())
  }
}
