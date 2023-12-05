// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.lang.LanguageStructureViewBuilder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.usageView.UsageInfo

interface SearchEverywherePreviewProvider

object SearchEverywherePreview {
  @JvmStatic
  fun getFileFirstUsage(selectedElement: Any): UsageInfo? {
    val psiElement = PSIPresentationBgRendererWrapper.toPsi(selectedElement) ?: return null

    val file = psiElement as? PsiFile ?: return UsageInfo(psiElement)

    return UsageInfo(((LanguageStructureViewBuilder.INSTANCE.getStructureViewBuilder(file)
      ?.createStructureView(null, file.project))
      ?.treeModel?.root?.children?.firstOrNull() as? StructureViewTreeElement)
                       ?.value as? PsiElement ?: file)
  }
}
