// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ide.IdeBundle
import org.jetbrains.annotations.Nls
import javax.swing.tree.DefaultMutableTreeNode

open class LoadingNode @JvmOverloads constructor(text: @Nls String = getText()) : DefaultMutableTreeNode(text) {
  companion object {
    @JvmStatic fun getText(): @Nls String = IdeBundle.message("treenode.loading")
  }
}
