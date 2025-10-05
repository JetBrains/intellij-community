// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.newStructureView

import com.intellij.ide.TreeExpander
import com.intellij.ide.impl.StructureViewWrapperImpl
import com.intellij.ide.structureView.StructureViewFactory
import com.intellij.ide.structureView.StructureViewFactoryEx
import com.intellij.ide.structureView.impl.StructureViewComposite
import com.intellij.ide.structureView.logical.ExternalElementsProvider
import com.intellij.ide.structureView.logical.LogicalStructureElementsProvider
import com.intellij.ide.structureView.logical.impl.LogicalStructureViewTreeElement
import com.intellij.ide.structureView.logical.model.ExtendedLogicalObject
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiTarget
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.JTree

internal class StructureViewExpander(val project: Project): TreeExpander {

  override fun canCollapse(): Boolean {
    return getActualTree() != null
  }

  override fun canExpand(): Boolean {
    return getActualTree() != null
  }

  override fun collapseAll() {
    val tree = getActualTree() ?: return
    TreeUtil.collapseAll(tree, false, 1)
    afterExpandOrCollapse()
  }

  override fun expandAll() {
    val tree = getActualTree() ?: return
    val rootFile = (StructureViewComponent.unwrapValue(tree.model.root) as? PsiElement)?.containingFile?.virtualFile
    TreeUtil.promiseExpand(tree, Int.Companion.MAX_VALUE) { path ->
      val structureElement = StructureViewComponent.unwrapWrapper(path.lastPathComponent)
      if (structureElement is LogicalStructureViewTreeElement<*>) {
        val logicalModel = structureElement.getLogicalAssembledModel().model
        if (logicalModel is ExtendedLogicalObject) return@promiseExpand false
      }
      val pathObject = StructureViewComponent.unwrapValue(path.lastPathComponent)
      when (pathObject) {
        is LogicalStructureElementsProvider<*, *> -> pathObject !is ExternalElementsProvider<*, *>
        is PsiElement -> rootFile == null || pathObject.containingFile?.virtualFile == rootFile
        is PsiTarget if rootFile != null && pathObject.isValid -> pathObject.navigationElement.containingFile?.virtualFile == rootFile
        else -> true
      }
    }.onSuccess { afterExpandOrCollapse() }
  }

  private fun afterExpandOrCollapse() {
    val myLayeredPane = getActualStructureView()?.content as? StructureViewComponent.MyLayeredPane
    myLayeredPane?.hideToolbar()
  }

  private fun getActualTree(): JTree? {
    return getActualStructureView()?.tree
  }

  private fun getActualStructureView(): StructureViewComponent? {
    val wrapper = (StructureViewFactory.getInstance(project) as? StructureViewFactoryEx)
                    ?.structureViewWrapper as? StructureViewWrapperImpl ?: return null
    val structureView = wrapper.getStructureView() ?: return null
    if (structureView is StructureViewComponent) return structureView
    if (structureView is StructureViewComposite) {
      for (descriptor in structureView.structureViews) {
        val oneOfViews = descriptor.structureView as? StructureViewComponent ?: continue
        val tree = oneOfViews.tree
        if (tree.isShowing) return oneOfViews
      }
    }
    return null
  }
}