// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.lang.LanguageStructureViewBuilder
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.usageView.UsageInfo

interface SearchEverywherePreviewPrimaryUsageFinder {
  
  companion object {
    @JvmField val EP_NAME: ExtensionPointName<SearchEverywherePreviewPrimaryUsageFinder> = ExtensionPointName("com.intellij.searchEverywherePreviewPrimaryUsageFinder")
  }
  
  fun findPrimaryUsageInfo(psiFile: PsiFile): Pair<UsageInfo, Disposable?>?
}

class PreviewPrimaryUsageFinderImpl : SearchEverywherePreviewPrimaryUsageFinder {
  override fun findPrimaryUsageInfo(psiFile: PsiFile): Pair<UsageInfo, Disposable?>? {
    val structureViewBuilder = LanguageStructureViewBuilder.getInstance().getStructureViewBuilder(psiFile);
    if (structureViewBuilder !is TreeBasedStructureViewBuilder) return null;

    val structureViewModel = structureViewBuilder.createStructureViewModel(null);
    val structuredViewModelDisposable = Disposable { Disposer.dispose(structureViewModel); }

    val firstChild = structureViewModel.root.children.firstOrNull()
    if (firstChild !is StructureViewTreeElement) return Pair(UsageInfo(psiFile), structuredViewModelDisposable)

    val firstChildElement = firstChild.value
    if (firstChildElement !is PsiElement) return Pair(UsageInfo(psiFile), structuredViewModelDisposable)

    return Pair(UsageInfo(firstChildElement), structuredViewModelDisposable)
  }
}
