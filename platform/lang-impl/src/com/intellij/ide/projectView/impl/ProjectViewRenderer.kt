// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl

import com.intellij.ide.util.treeView.InplaceCommentAppender
import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SimpleTextAttributes

open class ProjectViewRenderer : NodeRenderer() {

  init {
    isOpaque = false
    isIconOpaque = false
    isTransparentIconBackground = true
  }

}
