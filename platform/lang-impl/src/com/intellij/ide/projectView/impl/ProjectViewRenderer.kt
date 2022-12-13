// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl

import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.ui.UISettings
import com.intellij.ide.util.treeView.InplaceCommentAppender
import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.JTree

open class ProjectViewRenderer : NodeRenderer() {

  private val myInplaceCommentAppender = InplaceCommentAppenderImpl()

  init {
    isOpaque = false
    isIconOpaque = false
    isTransparentIconBackground = true
  }

  override fun customizeCellRenderer(tree: JTree,
                                     value: Any?,
                                     selected: Boolean,
                                     expanded: Boolean,
                                     leaf: Boolean,
                                     row: Int,
                                     hasFocus: Boolean) {
    super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus)

    val userObject = TreeUtil.getUserObject(value)
    if (userObject is ProjectViewNode<*> && UISettings.getInstance().showInplaceComments) {
      appendInplaceComments(userObject)
    }
  }

  private fun appendInplaceComments(node: ProjectViewNode<*>) {
    if (node.hasInplaceCommentProducer()) {
      return // the node handles inplace comments by itself (the right way)
    }
    val parentNode = node.parent
    val content = node.value
    if (content is PsiFileSystemItem || content !is PsiElement || parentNode != null && parentNode.value is PsiDirectory) {
      appendInplaceComments(node.project, node.virtualFile)
    }
  }

  @Suppress("MemberVisibilityCanBePrivate") // used in Rider
  @Deprecated("This function is called on EDT, but can be potentially slow, which can lead to UI freezes. " +
              "If your node extends AbstractPsiBasedNode, you don't need to call this (it handles comments in the BGT update). " +
              "If your node extends AbstractTreeNode, override getInplaceCommentProducer. " +
              "Otherwise, override update() and append inline comments to the presentation there. " +
              "Use the global appendInplaceComments function from ProjectViewInplaceCommentProducerImpl.kt to handle the actual comment creation.")
  fun appendInplaceComments(project: Project?, file: VirtualFile?) {
    appendInplaceComments(myInplaceCommentAppender, project, file)
  }

  private inner class InplaceCommentAppenderImpl : InplaceCommentAppender {
    override fun append(text: String, attributes: SimpleTextAttributes) {
      this@ProjectViewRenderer.append(text, attributes)
    }
  }

}
