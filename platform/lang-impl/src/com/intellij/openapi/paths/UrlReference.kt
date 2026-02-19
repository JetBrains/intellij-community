// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.paths

import com.intellij.codeInsight.highlighting.HyperlinkAnnotator
import com.intellij.codeInsight.highlighting.PsiHighlightedReference
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.lang.annotation.AnnotationBuilder
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.navigation.NavigatableSymbol
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.NavigationRequests
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class UrlReference(private val element: PsiElement,
                   private val rangeInElement: TextRange,
                   val url: String) : PsiHighlightedReference {

  override fun getElement(): PsiElement = element

  override fun getRangeInElement(): TextRange = rangeInElement

  override fun resolveReference(): Collection<Symbol> = listOf(UrlSymbol(url))

  override fun highlightMessage(): @Nls String = HyperlinkAnnotator.getMessage()

  override fun highlightReference(annotationBuilder: AnnotationBuilder): AnnotationBuilder {
    return annotationBuilder.textAttributes(CodeInsightColors.INACTIVE_HYPERLINK_ATTRIBUTES)
  }
}

private class UrlSymbol(
  @NlsSafe private val url: String
) : Pointer<UrlSymbol>,
    NavigatableSymbol,
    NavigationTarget {

  override fun createPointer(): Pointer<out UrlSymbol> = this

  override fun dereference(): UrlSymbol = this

  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> = listOf(this)

  override fun computePresentation(): TargetPresentation = TargetPresentation
    .builder(url)
    .icon(AllIcons.General.Web)
    .presentation()

  override fun navigationRequest(): NavigationRequest? {
    // TODO support url request natively
    return NavigationRequests.getInstance().rawNavigationRequest(UrlNavigatable(url))
  }
}

private class UrlNavigatable(private val url: String) : Navigatable {
  override fun navigate(requestFocus: Boolean) = BrowserUtil.browse(url)
  override fun canNavigate(): Boolean = true
  override fun canNavigateToSource(): Boolean = false
}
