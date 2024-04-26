// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint

import com.intellij.navigation.NavigationItem
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usageView.UsageInfo
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.util.concurrency.ThreadingAssertions
import org.jetbrains.annotations.TestOnly
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

class PsiImplementationViewElement(psiElement: PsiElement) : ImplementationViewElement() {
  private val pointer = psiElement.createSmartPointer()

  init {
    ThreadingAssertions.assertBackgroundThread()
  }

  @TestOnly
  fun getPsiElement(): PsiElement? = pointer.element

  override val project: Project = psiElement.project

  override val isNamed: Boolean = psiElement is PsiNamedElement

  override val name: String? = (psiElement as? PsiNamedElement)?.name

  override val containingFile: VirtualFile? = psiElement.containingFile?.originalFile?.virtualFile

  override val text: String? = ImplementationViewComponent.getNewText(psiElement)

  override val presentableText: String =
    (psiElement as? NavigationItem)?.presentation?.presentableText ?: containingFile?.presentableName ?: ""


  override val containerPresentation: String? = (psiElement as? NavigationItem)?.presentation?.locationString

  private val locationIconRef = Ref<Icon>()
  override val locationText: String? = ElementLocationUtil.renderElementLocation(psiElement, locationIconRef)
  override val locationIcon: Icon? = locationIconRef.get()

  override val containingMemberOrSelf: ImplementationViewElement = run {
    val parent = PsiTreeUtil.getStubOrPsiParent(psiElement)
    if (parent == null || (parent is PsiFile && parent.virtualFile == containingFile)) {
      this
    }
    else PsiImplementationViewElement(parent)
  }

  override fun navigate(focusEditor: Boolean) {
    val psiElement = pointer.element ?: return
    val navigationElement = psiElement.navigationElement
    val file = navigationElement.containingFile?.originalFile ?: return
    val virtualFile = file.virtualFile ?: return
    val project = psiElement.project
    val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
    val descriptor = OpenFileDescriptor(project, virtualFile, navigationElement.textOffset)
    fileEditorManager.openTextEditor(descriptor, focusEditor)
  }

  override val elementForShowUsages: PsiElement?
    get() {
      val psiElement = pointer.element
      return if (psiElement !is PsiBinaryFile) psiElement else null
    }
}
