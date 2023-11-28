// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint

import com.intellij.navigation.NavigationItem
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiBinaryFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usageView.UsageInfo
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import javax.swing.Icon

/**
 * A single element shown in the Show Implementations view.
 */
abstract class ImplementationViewElement {
  abstract val project: Project
  abstract val isNamed: Boolean
  @get:NlsSafe
  abstract val name: String?
  @get:NlsSafe
  abstract val presentableText: String

  @get:NlsSafe
  open val containerPresentation: String? = null

  abstract val containingFile: VirtualFile?
  @get:NlsSafe
  abstract val text: String?
  @get:NlsSafe
  abstract val locationText: String?
  abstract val locationIcon: Icon?
  abstract val containingMemberOrSelf: ImplementationViewElement
  abstract val elementForShowUsages: PsiElement?

  abstract fun navigate(focusEditor: Boolean)

  open val usage: Usage?
    get() {
      return UsageInfo2UsageAdapter(UsageInfo(elementForShowUsages ?: return null))
    }
}

class PsiImplementationViewElement(val psiElement: PsiElement) : ImplementationViewElement() {
  override val project: Project
    get() = psiElement.project

  override val isNamed: Boolean
    get() = psiElement is PsiNamedElement

  override val name: String?
    get() = (psiElement as? PsiNamedElement)?.name

  override val containingFile: VirtualFile?
    get() = psiElement.containingFile?.originalFile?.virtualFile

  override val text: String?
    get() = ImplementationViewComponent.getNewText(psiElement)

  override val presentableText: String = runReadAction {
    val presentation = (psiElement as? NavigationItem)?.presentation
    val vFile = containingFile ?: return@runReadAction ""
    val presentableName = vFile.presentableName
    if (presentation == null) {
      return@runReadAction presentableName
    }
    val elementPresentation = presentation.presentableText
    if (elementPresentation == null) {
      return@runReadAction presentableName
    }
    return@runReadAction elementPresentation
  }

  override val containerPresentation: String? = runReadAction { (psiElement as? NavigationItem)?.presentation?.locationString }

  private val locationIconRef = Ref<Icon>()
  override val locationText: String? = runReadAction { ElementLocationUtil.renderElementLocation(psiElement, locationIconRef) }
  override val locationIcon: Icon? = locationIconRef.get()

  override val containingMemberOrSelf: ImplementationViewElement = runReadAction {
    val parent = PsiTreeUtil.getStubOrPsiParent(psiElement)
    if (parent == null || (parent is PsiFile && parent.virtualFile == containingFile)) {
      this
    }
    else PsiImplementationViewElement(parent)
  }

  override fun navigate(focusEditor: Boolean) {
    val navigationElement = psiElement.navigationElement
    val file = navigationElement.containingFile?.originalFile ?: return
    val virtualFile = file.virtualFile ?: return
    val project = psiElement.project
    val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
    val descriptor = OpenFileDescriptor(project, virtualFile, navigationElement.textOffset)
    fileEditorManager.openTextEditor(descriptor, focusEditor)
  }

  override val elementForShowUsages: PsiElement?
    get() = if (psiElement !is PsiBinaryFile) psiElement else null
}
