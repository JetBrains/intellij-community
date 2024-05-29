// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.workspace.projectView

import com.intellij.icons.ExpUiIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.BasePsiNode
import com.intellij.ide.projectView.impl.nodes.ExternalLibrariesNode
import com.intellij.ide.projectView.impl.nodes.ProjectViewProjectNode
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.workspace.Subproject
import com.intellij.ide.workspace.SubprojectDeleteProvider
import com.intellij.ide.workspace.getAllSubprojects
import com.intellij.ide.workspace.isWorkspace
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.project.stateStore
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import java.awt.Color
import kotlin.io.path.invariantSeparatorsPathString

private val WORKSPACE_NODE = DataKey.create<Boolean>("project.view.workspace.node")

internal fun isWorkspaceNode(e: AnActionEvent) = WORKSPACE_NODE.getData(e.dataContext) == true

internal class WorkspaceTreeStructureProvider(val project: Project) : TreeStructureProvider, DumbAware {
  override fun modify(parent: AbstractTreeNode<*>,
                      children: Collection<AbstractTreeNode<*>>,
                      settings: ViewSettings): Collection<AbstractTreeNode<*>> {

    if (parent is ProjectViewProjectNode && project.isWorkspace) {
      return overrideWorkspaceDirectory(children, settings, parent) ?: return children
    }
    return children
  }

  override fun getData(selected: MutableCollection<out AbstractTreeNode<*>>, dataId: String): Any? {
    if (WORKSPACE_NODE.`is`(dataId) && selected.firstOrNull() is WorkspaceNode) {
      return true
    }
    if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.`is`(dataId)) {
      val directoryNodes = selected.filterIsInstance<PsiDirectoryNode>()
      val workspaceNode = directoryNodes.firstNotNullOfOrNull { it.parent as? WorkspaceNode } ?: return null
      val subprojects = directoryNodes.mapNotNull { workspaceNode.getSubproject(it.value) }
      if (subprojects.isEmpty()) return null
      return SubprojectDeleteProvider(subprojects)
    }
    return null
  }

  private fun overrideWorkspaceDirectory(children: Collection<AbstractTreeNode<*>>,
                                         settings: ViewSettings,
                                         projectNode: ProjectViewProjectNode): Collection<AbstractTreeNode<*>>? {
    val (directoryNodes, otherNodes) = children.partitionByType<BasePsiNode<*>, AbstractTreeNode<*>>()

    val projectDirPath = project.stateStore.projectBasePath.invariantSeparatorsPathString
    val workspaceNode = directoryNodes.find { (it.value as? PsiDirectory)?.virtualFile?.path == projectDirPath }
    if (workspaceNode != null) { return null } // no need to override
    val virtualFile = LocalFileSystem.getInstance().findFileByPath(projectDirPath) ?: return null
    val workspaceDirectory = PsiManager.getInstance(project).findDirectory(virtualFile) ?: return null

    val newWorkspaceNode = WorkspaceNode(project, workspaceDirectory, settings, projectNode)
    return otherNodes + listOf(newWorkspaceNode)
  }

  private inline fun <reified U : T, T> Iterable<T>.partitionByType(): Pair<MutableList<U>, MutableList<T>> {
    val first = mutableListOf<U>()
    val second = mutableListOf<T>()
    for (element in this) {
      if (element is U) first.add(element)
      else second.add(element)
    }
    return Pair(first, second)
  }

  private class WorkspaceNode(project: Project, value: PsiDirectory, viewSettings: ViewSettings,
                              private val projectNode: ProjectViewProjectNode)
    : PsiDirectoryNode(project, value, viewSettings) {

    private val subprojectMap = HashMap<PsiDirectory, Subproject?>()

    override fun getChildrenImpl(): Collection<AbstractTreeNode<*>> {
      val subprojects = getAllSubprojects(project).associateBy { it.projectPath }
      subprojectMap.clear()
      val children = projectNode.children.filter { it !is ExternalLibrariesNode }
      val newChildren = ArrayList<AbstractTreeNode<*>>(children.size)
      for (child in children) {
        val directoryNode = child as? PsiDirectoryNode
        if (directoryNode == null) {
          newChildren.add(child)
          continue
        }
        val path = directoryNode.value.virtualFile.path
        val subproject = subprojects[path]
        subprojectMap[directoryNode.value] = subproject
        val node = if (subproject != null) SubprojectNode(directoryNode, subproject) else directoryNode
        newChildren.add(node)
      }
      return newChildren
    }

    override fun update(data: PresentationData) {
      projectNode.update(data)
      data.setIcon(ExpUiIcons.Nodes.Workspace)
    }

    override fun computeBackgroundColor(): Color? = null

    override fun contains(file: VirtualFile): Boolean {
      return projectNode.contains(file)
    }

    fun getSubproject(directory: PsiDirectory) = subprojectMap[directory]
  }

  private class SubprojectNode(original: PsiDirectoryNode,
                               private val subproject: Subproject):
    PsiDirectoryNode(original) {

    override fun update(data: PresentationData) {
      super.update(data)
      data.setIcon(subproject.handler.subprojectIcon)
    }
  }
}