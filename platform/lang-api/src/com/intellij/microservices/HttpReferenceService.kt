// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices

import com.intellij.microservices.url.parameters.PathVariableDefinitionsSearcher
import com.intellij.microservices.url.parameters.PathVariableUsagesProvider
import com.intellij.microservices.url.parameters.QueryParameterNameTarget
import com.intellij.microservices.url.references.UrlPathContext
import com.intellij.microservices.url.references.UrlPathReference
import com.intellij.microservices.url.references.UrlPathReferenceTarget
import com.intellij.microservices.url.references.UrlSegmentReference
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.pom.PomTargetPsiElement
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveResult
import org.jetbrains.annotations.ApiStatus

/**
 * Separates implementation of URL / HTTP references resolve and navigation from API.
 */
@ApiStatus.Internal
interface HttpReferenceService {
  fun resolveMimeReference(referenceElement: PsiElement, text: String): PsiElement

  fun isReferenceToMimeElement(element: PsiElement, text: String): Boolean

  fun resolveHeaderReference(referenceElement: PsiElement, text: String): PsiElement

  fun isReferenceToHeaderElement(element: PsiElement, text: String): Boolean

  fun resolveHttpMethod(referenceElement: PsiElement, text: String, rangeInElement: TextRange): PsiElement

  fun isReferenceToHttpMethod(element: PsiElement, text: String): Boolean

  fun resolveAuthorityReference(
    reference: UrlSegmentReference,
    referenceElement: PsiElement,
    refValue: String,
    customNavigate: ((UrlSegmentReference) -> Unit)? = null,
  ): PsiElement

  fun createUrlPathTarget(context: UrlPathContext, isAtEnd: Boolean, project: Project): UrlPathReferenceTarget

  fun isReferenceToUrlPathTarget(element: PsiElement): Boolean

  fun createSearchableUrlElement(project: Project, context: UrlPathContext): NavigatablePsiElement

  fun getUrlFromPomTargetPsi(psiElement: PsiElement): UrlPathReference?

  fun createQueryParameterNameTarget(context: UrlPathContext, refValue: String, project: Project): QueryParameterNameTarget

  fun getQueryParameterNameVariants(target: QueryParameterNameTarget?): Array<Any>

  fun resolvePathVariableDeclaration(
    value: String,
    referenceElement: PsiElement,
    range: TextRange,
    usagesProvider: PathVariableUsagesProvider,
  ): PomTargetPsiElement

  fun canResolveToPathVariableDeclaration(elementClass: Class<out PsiElement>): Boolean

  fun resolvePathVariableUsage(
    variableName: String,
    referenceElement: PsiElement,
    searcher: PathVariableDefinitionsSearcher
  ): Array<ResolveResult>

  fun isReferenceToPathVariableDeclaration(element: PsiElement): Boolean
}
