// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl

import com.intellij.ide.util.treeView.InplaceCommentAppender
import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SimpleTextAttributes

open class ProjectViewRenderer : NodeRenderer() {

  private val myInplaceCommentAppender = InplaceCommentAppenderImpl()

  init {
    isOpaque = false
    isIconOpaque = false
    isTransparentIconBackground = true
  }

  @Suppress("MemberVisibilityCanBePrivate") // used in Rider
  @Deprecated("This function is no longer called from the platform code. " +
              "If your node extends AbstractPsiBasedNode, you don't need to call this (it handles comments in the BGT update). " +
              "If your node extends AbstractTreeNode, override AbstractTreeNode.appendInplaceComments(). " +
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
