// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.FileProblem
import com.intellij.analysis.problemsView.Problem
import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES
import com.intellij.ui.tree.LeafState
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.tree.TreePath

internal abstract class Root(val panel: ProblemsViewPanel)
  : Node(panel.project), ProblemsCollector, Disposable {

  private val nodes = mutableMapOf<VirtualFile, FileNode>()

  override fun dispose() = Unit

  override fun getLeafState() = LeafState.NEVER

  override fun getName() = panel.getName(0)

  override fun update(project: Project, presentation: PresentationData) {
    presentation.addText(name, REGULAR_ATTRIBUTES)
  }

  override fun getChildren(): Collection<Node> {
    val children = mutableListOf<Node>()
    val files = getProblemFiles()
    synchronized(nodes) {
      files.forEach { children += nodes.computeIfAbsent(it) { file -> FileNode(this, file) } }
    }
    return children
  }

  open fun getChildren(file: VirtualFile): Collection<Node> {
    val node = synchronized(nodes) { nodes[file] } ?: return emptyList()
    return getFileProblems(node.file).map { ProblemNode(node, it) }
  }

  override fun problemAppeared(problem: Problem) = when (problem) {
    !is FileProblem -> structureChanged()
    else -> {
      val file = problem.file
      when (1 == getFileProblemCount(file)) {
        true -> fileAppeared(file)
        else -> fileUpdated(file)
      }
    }
  }

  override fun problemDisappeared(problem: Problem) = when (problem) {
    !is FileProblem -> structureChanged()
    else -> {
      val file = problem.file
      when (0 == getFileProblemCount(file)) {
        true -> fileDisappeared(file)
        else -> fileUpdated(file)
      }
    }
  }

  override fun problemUpdated(problem: Problem) {
    TreeUtil.promiseVisit(panel.tree, ProblemNodeFinder(problem)).onSuccess { path ->
      val node = TreeUtil.getLastUserObject(ProblemNode::class.java, path) ?: return@onSuccess
      onValidThread { if (node.update()) panel.treeModel.nodeChanged(node.getPath()) }
    }
  }

  private fun fileAppeared(file: VirtualFile) {
    structureChanged()
    TreeUtil.promiseExpand(panel.tree, FileNodeFinder(file))
  }

  private fun fileDisappeared(file: VirtualFile) {
    synchronized(nodes) { nodes.remove(file) }
    structureChanged()
  }

  private fun fileUpdated(file: VirtualFile) {
    TreeUtil.promiseVisit(panel.tree, FileNodeFinder(file)).onSuccess { path ->
      path?.let { structureChanged(it) }
    }
  }

  private fun structureChanged(path: TreePath? = null) {
    panel.updateToolWindowContent()
    panel.treeModel.structureChanged(path)
  }

  private fun onValidThread(task: () -> Unit) {
    panel.treeModel.invoker.invoke {
      if (panel.treeModel.isRoot(this)) task()
    }
  }
}
