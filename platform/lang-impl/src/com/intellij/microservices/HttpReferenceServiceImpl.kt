// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.microservices.http.HttpHeaderElement
import com.intellij.microservices.http.HttpMethodElement
import com.intellij.microservices.mime.MimeTypePsiElement
import com.intellij.microservices.url.HttpMethods
import com.intellij.microservices.url.parameters.PathVariableDefinitionsSearcher
import com.intellij.microservices.url.parameters.PathVariablePsiElement
import com.intellij.microservices.url.parameters.PathVariableUsagesProvider
import com.intellij.microservices.url.parameters.QueryParameterNameTarget
import com.intellij.microservices.url.references.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.pom.PomTargetPsiElement
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.ResolveResult
import com.intellij.util.ReflectionUtil

internal class HttpReferenceServiceImpl : HttpReferenceService {
  override fun resolveMimeReference(referenceElement: PsiElement, text: String): PsiElement {
    return MimeTypePsiElement(referenceElement, text)
  }

  override fun isReferenceToMimeElement(element: PsiElement, text: String): Boolean {
    return element is MimeTypePsiElement && element.name == text
  }

  override fun resolveHeaderReference(referenceElement: PsiElement, text: String): PsiElement {
    return HttpHeaderElement(referenceElement, text)
  }

  override fun isReferenceToHeaderElement(element: PsiElement, text: String): Boolean {
    return element is HttpHeaderElement && element.name == text
  }

  override fun resolveHttpMethod(referenceElement: PsiElement, text: String, rangeInElement: TextRange): PsiElement {
    return HttpMethodElement(referenceElement, text, rangeInElement)
  }

  override fun isReferenceToHttpMethod(element: PsiElement, text: String): Boolean {
    return element is HttpMethodElement && element.name == text
  }

  override fun resolveAuthorityReference(
    reference: UrlSegmentReference,
    referenceElement: PsiElement,
    refValue: String,
    customNavigate: ((UrlSegmentReference) -> Unit)?,
  ): PsiElement {
    return AuthorityReferenceFakeElement(
      referenceElement.project,
      AuthorityPomTarget(refValue),
      customNavigate?.let { { it.invoke(reference) } }
    )
  }

  override fun createUrlPathTarget(context: UrlPathContext, isAtEnd: Boolean, project: Project): UrlPathReferenceTarget {
    return UrlPathReferenceUnifiedPomTarget(context, isAtEnd, project)
  }

  override fun isReferenceToUrlPathTarget(element: PsiElement): Boolean {
    return element is UrlTargetInfoFakeElement
  }

  override fun createSearchableUrlElement(project: Project, context: UrlPathContext): NavigatablePsiElement {
    val pomTarget = UrlPathReferenceUnifiedPomTarget(context, project)
    return UrlTargetInfoFakeElement(project, pomTarget, null, context.isDeclaration)
  }

  override fun getUrlFromPomTargetPsi(psiElement: PsiElement): UrlPathReference? {
    return (psiElement as? UrlTargetInfoFakeElement)?.reference
  }

  override fun createQueryParameterNameTarget(context: UrlPathContext, refValue: String, project: Project): QueryParameterNameTarget {
    return QueryParameterNamePomTarget(UrlPathReferenceUnifiedPomTarget(context, project), project, refValue)
  }

  override fun getQueryParameterNameVariants(target: QueryParameterNameTarget?): Array<Any> {
    return (target as? QueryParameterNamePomTarget)?.urlPathReferenceUnifiedPomTarget
      ?.resolvedTargets?.asSequence()
      .orEmpty()
      .filter { it.methods.isEmpty() || it.methods.contains(HttpMethods.GET) }
      .flatMap { it.queryParameters.asSequence() }
      .map { param -> LookupElementBuilder.create(param.name).withIcon(AllIcons.Nodes.Parameter) }
      .toList()
      .toTypedArray()
  }

  override fun resolvePathVariableDeclaration(
    value: String,
    referenceElement: PsiElement,
    range: TextRange,
    usagesProvider: PathVariableUsagesProvider,
  ): PomTargetPsiElement {
    return PathVariablePsiElement.create(value, referenceElement, range, usagesProvider)
  }

  override fun canResolveToPathVariableDeclaration(elementClass: Class<out PsiElement>): Boolean {
    return ReflectionUtil.isAssignable(PathVariablePsiElement::class.java, elementClass)
  }

  override fun resolvePathVariableUsage(variableName: String, referenceElement: PsiElement, searcher: PathVariableDefinitionsSearcher): Array<ResolveResult> {
    val merge = PathVariablePsiElement.merge(
      searcher.getPathVariables(referenceElement)
        .map { it as PathVariablePsiElement }
        .filter { o -> variableName == o.name }
        .map { v-> v.navigatingToDeclaration() }
        .toList()
    )
    if (merge == null) return ResolveResult.EMPTY_ARRAY
    return PsiElementResolveResult.createResults(merge)
  }

  override fun isReferenceToPathVariableDeclaration(element: PsiElement): Boolean {
    return element is PathVariablePsiElement
  }
}