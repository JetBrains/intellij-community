// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ide.IdeBundle
import com.intellij.ui.treeStructure.TreeNodePresentation
import com.intellij.ui.treeStructure.TreeNodePresentationImpl
import com.intellij.ui.treeStructure.TreeNodeTextFragmentImpl
import com.intellij.ui.treeStructure.TreeNodeViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.annotations.Nls
import javax.swing.tree.DefaultMutableTreeNode

open class LoadingNode @JvmOverloads constructor(text: @Nls String = getText()) : DefaultMutableTreeNode(text), TreeNodeViewModel {
  companion object {
    @JvmStatic fun getText(): @Nls String = IdeBundle.message("treenode.loading")
  }

  private val presentationValue = TreeNodePresentationImpl(
    isLeaf = true,
    icon = null,
    mainText = userObject as String,
    fullText = listOf(TreeNodeTextFragmentImpl(userObject as String, SimpleTextAttributes.GRAY_ATTRIBUTES)),
    toolTip = null,
  )

  override val presentation: Flow<TreeNodePresentation> = flowOf(presentationValue)

  override val children: Flow<List<TreeNodeViewModel>>
    get() = flowOf(emptyList())

  override fun presentationSnapshot(): TreeNodePresentation = presentationValue
}
