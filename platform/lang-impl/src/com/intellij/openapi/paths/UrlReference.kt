// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.paths

import com.intellij.codeInsight.highlighting.HyperlinkAnnotator
import com.intellij.codeInsight.highlighting.PsiHighlightedReference
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.lang.annotation.AnnotationBuilder
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.model.presentation.PresentableSymbol
import com.intellij.model.presentation.SymbolPresentation
import com.intellij.navigation.*
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement

@Suppress("NonDefaultConstructor")
class UrlReference(private val element: PsiElement,
                   private val rangeInElement: TextRange,
                   val url: String) : PsiHighlightedReference {

  override fun getElement(): PsiElement = element

  override fun getRangeInElement(): TextRange = rangeInElement

  override fun resolveReference(): Collection<Symbol> = listOf(UrlSymbol(url))

  override fun highlightMessage() = HyperlinkAnnotator.getMessage()

  override fun highlightReference(annotationBuilder: AnnotationBuilder): AnnotationBuilder {
    return annotationBuilder.textAttributes(CodeInsightColors.INACTIVE_HYPERLINK_ATTRIBUTES)
  }
}

@Suppress("NonDefaultConstructor")
private class UrlSymbol(
  @NlsSafe private val url: String
) : Pointer<UrlSymbol>,
    PresentableSymbol,
    NavigatableSymbol,
    NavigationTarget {

  override fun createPointer(): Pointer<out UrlSymbol> = this

  override fun dereference(): UrlSymbol = this

  override fun getSymbolPresentation(): SymbolPresentation = SymbolPresentation.create(AllIcons.General.Web, url, url)

  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> = listOf(this)

  override fun getTargetPresentation(): TargetPresentation = TODO(
    "In all known cases the symbol doesn't appear in the disambiguation popup, " +
    "because this symbol is usually alone, so no popup required. Implement this method when needed."
  )

  override fun navigationRequest(): NavigationRequest? {
    // TODO support url request natively
    return NavigationService.instance().rawNavigationRequest(UrlNavigatable(url))
  }
}

private class UrlNavigatable(private val url: String) : Navigatable {
  override fun navigate(requestFocus: Boolean) = BrowserUtil.browse(url)
  override fun canNavigate(): Boolean = true
  override fun canNavigateToSource(): Boolean = false
}
