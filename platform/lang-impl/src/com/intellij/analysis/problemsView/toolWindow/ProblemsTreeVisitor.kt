// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.FileProblem
import com.intellij.analysis.problemsView.Problem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.tree.TreePath

internal interface ProblemsTreeVisitor : TreeVisitor {
  override fun visit(path: TreePath): TreeVisitor.Action = when (val node = TreeUtil.getLastUserObject(path)) {
    is Root -> visitRoot(node)
    is FileNode -> visitFile(node)
    is ProblemsViewGroupNode -> visitGroup(node)
    is ProblemsContextNode -> visitContext(node)
    is ProblemsContextGroupNode -> visitContextGroup(node)
    is ProblemNode -> visitProblem(node)
    else -> TreeVisitor.Action.SKIP_CHILDREN
  }

  fun visitRoot(root: Root): TreeVisitor.Action = TreeVisitor.Action.CONTINUE
  fun visitFile(node: FileNode): TreeVisitor.Action
  fun visitGroup(node: ProblemsViewGroupNode): TreeVisitor.Action = TreeVisitor.Action.SKIP_CHILDREN
  fun visitContext(node: ProblemsContextNode): TreeVisitor.Action = TreeVisitor.Action.SKIP_CHILDREN
  fun visitContextGroup(node: ProblemsContextGroupNode): TreeVisitor.Action = TreeVisitor.Action.SKIP_CHILDREN
  fun visitProblem(node: ProblemNode): TreeVisitor.Action = TreeVisitor.Action.SKIP_CHILDREN
}


internal class FileNodeFinder(private val file: VirtualFile) : ProblemsTreeVisitor {
  override fun visitFile(node: FileNode): TreeVisitor.Action = when (node.file) {
    file -> TreeVisitor.Action.INTERRUPT
    else -> TreeVisitor.Action.SKIP_CHILDREN
  }
}

internal class ProblemNodeFinder(private val problem: Problem) : ProblemsTreeVisitor {
  override fun visitFile(node: FileNode): TreeVisitor.Action = when {
    problem !is FileProblem -> TreeVisitor.Action.SKIP_CHILDREN
    node.file == problem.file -> TreeVisitor.Action.CONTINUE
    else -> TreeVisitor.Action.SKIP_CHILDREN
  }

  override fun visitGroup(node: ProblemsViewGroupNode): TreeVisitor.Action = when (node.group) {
    problem.group -> TreeVisitor.Action.CONTINUE
    else -> TreeVisitor.Action.SKIP_CHILDREN
  }

  override fun visitContext(node: ProblemsContextNode): TreeVisitor.Action = when (node.contextGroup) {
    problem.contextGroup -> TreeVisitor.Action.CONTINUE
    else -> TreeVisitor.Action.SKIP_CHILDREN
  }

  override fun visitContextGroup(node: ProblemsContextGroupNode): TreeVisitor.Action = when (node.group) {
    problem.group -> TreeVisitor.Action.CONTINUE
    else -> TreeVisitor.Action.SKIP_CHILDREN
  }

  override fun visitProblem(node: ProblemNode): TreeVisitor.Action = when (node.problem) {
    problem -> TreeVisitor.Action.INTERRUPT
    else -> TreeVisitor.Action.SKIP_CHILDREN
  }
}
