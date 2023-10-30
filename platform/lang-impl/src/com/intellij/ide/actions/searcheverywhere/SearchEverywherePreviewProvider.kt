// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.usageView.UsageInfo

interface SearchEverywherePreviewProvider {
  /**
   * Gets a psi element the preview should highlight and be scrolled to
   */
  fun getElement(project: Project, psiFile: PsiFile): PsiElement? = null

  companion object {
    private val EP_NAME: ExtensionPointName<SearchEverywherePreviewProvider> =
      ExtensionPointName.create("com.intellij.searchEverywherePreviewProvider")

    private fun getUsageElement(project: Project, psiFile: PsiFile): PsiElement? {
      return EP_NAME.extensionList.firstNotNullOfOrNull { it.getElement(project, psiFile) }
    }

    fun getUsage(selectedElement: Any): UsageInfo? {
      val element = PSIPresentationBgRendererWrapper.toPsi(selectedElement) ?: return null
      if (element is PsiFile) {
        val usageElement = getUsageElement(element.getProject(), element)
        if (usageElement != null) {
          return UsageInfo(usageElement)
        }
      }
      return UsageInfo(element)
    }
  }
}
