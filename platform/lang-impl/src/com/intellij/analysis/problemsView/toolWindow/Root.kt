// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

abstract class Root(val panel: ProblemsViewPanel)
  : Node(panel.project), ProblemsCollector, Disposable {

  private val nodes = mutableMapOf<VirtualFile, FileNode>()

  private val nodesCache = ProblemsNodeCache<Pair<FileNode, Problem>> {
    ProblemNode(it.first, it.first.file, it.second)
  }

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
    return getChildren(node)
  }

  open fun getChildren(node: FileNode): Collection<Node> = getNodesForProblems(getFileProblems(node.file).map { Pair(node, it) })

  protected fun getNodesForProblems(fileProblems: List<Pair<FileNode, Problem>>): List<Node> = nodesCache.getNodes(fileProblems)

  override fun problemAppeared(problem: Problem) = when (problem) {
    !is FileProblem -> structureChanged()
    else -> {
      val file = problem.file
      // add new file node if it does not exist
      when (null == synchronized(nodes) { nodes[file] }) {
        true -> fileAppeared(file)
        else -> fileUpdated(file)
      }
    }
  }

  override fun problemDisappeared(problem: Problem) = when (problem) {
    !is FileProblem -> structureChanged()
    else -> {
      val file = problem.file
      // remove old file node if no more corresponding problems
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
    val node = synchronized(nodes) { nodes.remove(file) }
    if (node != null) structureChanged()
  }

  private fun fileUpdated(file: VirtualFile) {
    TreeUtil.promiseVisit(panel.tree, FileNodeFinder(file)).onSuccess { path ->
      path?.let { structureChanged(it) }
    }
  }

  open fun structureChanged(path: TreePath? = null) {
    panel.updateToolWindowContent()
    panel.treeModel.structureChanged(path)
  }

  private fun onValidThread(task: () -> Unit) {
    panel.treeModel.invoker.invoke {
      if (panel.treeModel.isRoot(this)) task()
    }
  }
}
