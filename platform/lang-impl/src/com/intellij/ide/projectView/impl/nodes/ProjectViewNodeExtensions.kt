// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl.nodes

import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.vfs.VirtualFile

internal fun AbstractTreeNode<*>.getVirtualFileForNodeOrItsPSI(): VirtualFile? =
  if (this is ProjectViewNode) {
    virtualFile ?: if (this is AbstractPsiBasedNode) virtualFileForValue else null
  }
  else {
    null
  }
