// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hint

import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiBinaryFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import javax.swing.JLabel

/**
 * A single element shown in the Show Implementations view.
 *
 * @author yole
 */
abstract class ImplementationViewElement {
  abstract val project: Project
  abstract val isNamed: Boolean
  abstract val name: String?
  abstract val presentation: ItemPresentation?
  abstract val containingFile: PsiFile?
  abstract val text: String?
  abstract val containingMemberOrSelf: ImplementationViewElement
  abstract val elementForShowUsages: PsiElement?

  abstract fun renderToLabel(label: JLabel)
  abstract fun navigate(focusEditor: Boolean)
}

class PsiImplementationViewElement(val psiElement: PsiElement) : ImplementationViewElement() {
  override val project: Project
    get() = psiElement.project

  override val isNamed: Boolean
    get() = psiElement is PsiNamedElement

  override val name: String?
    get() = (psiElement as? PsiNamedElement)?.name

  override val presentation: ItemPresentation?
    get() = (psiElement as? NavigationItem)?.presentation

  override val containingFile: PsiFile?
    get() = psiElement.containingFile?.originalFile

  override val text: String?
    get() = ImplementationViewComponent.getNewText(psiElement)

  override fun renderToLabel(label: JLabel) {
    ElementLocationUtil.customizeElementLabel(psiElement, label)
  }

  override val containingMemberOrSelf: ImplementationViewElement
    get() {
      val parent = PsiTreeUtil.getStubOrPsiParent(psiElement)
      if (parent == null || parent == containingFile) {
        return this
      }
      return PsiImplementationViewElement(parent)
    }

  override fun navigate(focusEditor: Boolean) {
    val navigationElement = psiElement.navigationElement
    val file = navigationElement.containingFile?.originalFile ?: return
    val virtualFile = file.virtualFile ?: return
    val project = psiElement.getProject()
    val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
    val descriptor = OpenFileDescriptor(project, virtualFile, navigationElement.getTextOffset())
    fileEditorManager.openTextEditor(descriptor, focusEditor)
  }

  override val elementForShowUsages: PsiElement?
    get() = if (psiElement !is PsiBinaryFile) psiElement else null
}
