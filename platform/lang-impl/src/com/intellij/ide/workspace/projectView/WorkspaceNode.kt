// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.workspace.projectView

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.ExternalLibrariesNode
import com.intellij.ide.projectView.impl.nodes.ProjectViewProjectNode
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory

class WorkspaceNode(project: Project, value: PsiDirectory, viewSettings: ViewSettings,
                    private val projectNode: ProjectViewProjectNode)
  : PsiDirectoryNode(project, value, viewSettings) {

  override fun getChildrenImpl(): Collection<AbstractTreeNode<*>> {
    return projectNode.children.filter { it !is ExternalLibrariesNode }
  }

  override fun update(data: PresentationData) {
    projectNode.update(data)
  }
}