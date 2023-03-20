// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.FileProblem
import com.intellij.analysis.problemsView.Problem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.tree.TreePath

internal interface ProblemsTreeVisitor : TreeVisitor {
  override fun visit(path: TreePath) = when (val node = TreeUtil.getLastUserObject(path)) {
    is Root -> visitRoot(node)
    is FileNode -> visitFile(node)
    is GroupNode -> visitGroup(node)
    is ProblemNode -> visitProblem(node)
    else -> TreeVisitor.Action.SKIP_CHILDREN
  }

  fun visitRoot(root: Root) = TreeVisitor.Action.CONTINUE
  fun visitFile(node: FileNode): TreeVisitor.Action
  fun visitGroup(node: GroupNode) = TreeVisitor.Action.SKIP_CHILDREN
  fun visitProblem(node: ProblemNode) = TreeVisitor.Action.SKIP_CHILDREN
}


internal class FileNodeFinder(private val file: VirtualFile) : ProblemsTreeVisitor {
  override fun visitFile(node: FileNode) = when (node.file) {
    file -> TreeVisitor.Action.INTERRUPT
    else -> TreeVisitor.Action.SKIP_CHILDREN
  }
}

internal class ProblemNodeFinder(private val problem: Problem) : ProblemsTreeVisitor {
  override fun visitFile(node: FileNode) = when {
    problem !is FileProblem -> TreeVisitor.Action.SKIP_CHILDREN
    node.file == problem.file -> TreeVisitor.Action.CONTINUE
    else -> TreeVisitor.Action.SKIP_CHILDREN
  }

  override fun visitGroup(node: GroupNode) = when (node.group) {
    problem.group -> TreeVisitor.Action.CONTINUE
    else -> TreeVisitor.Action.SKIP_CHILDREN
  }

  override fun visitProblem(node: ProblemNode) = when (node.problem) {
    problem -> TreeVisitor.Action.INTERRUPT
    else -> TreeVisitor.Action.SKIP_CHILDREN
  }
}
