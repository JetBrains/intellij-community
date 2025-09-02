// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util

import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.ProjectViewProjectNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

@ApiStatus.Internal
open class TreeFileChooserSupport(val project: Project) {
  companion object {
    fun getInstance(project: Project): TreeFileChooserSupport = project.service<TreeFileChooserSupport>()
  }

  open fun createRoot(settings: ViewSettings): AbstractTreeNode<*> {
    return ProjectViewProjectNode(project, settings)
  }

  open fun getVirtualFile(path: TreePath): VirtualFile? {
    val userObject = getUserObjectFromPath(path)
    if (userObject is ProjectViewNode<*>) {
      return userObject.virtualFile
    }

    return null
  }

  protected fun getUserObjectFromPath(path: TreePath): Any? {
    val node = path.lastPathComponent as DefaultMutableTreeNode
    val userObject = node.getUserObject()
    return userObject
  }
}