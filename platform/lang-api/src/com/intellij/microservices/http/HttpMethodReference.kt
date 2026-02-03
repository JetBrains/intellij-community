package com.intellij.microservices.http

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.microservices.HttpReferenceService
import com.intellij.microservices.MicroservicesBundle
import com.intellij.microservices.url.HTTP_METHODS
import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.intellij.util.ArrayUtil

class HttpMethodReference(element: PsiElement, range: TextRange)
  : PsiReferenceBase<PsiElement>(element, range), EmptyResolveMessageProvider {

  override fun resolve(): PsiElement? {
    val value = value
    if (value.isBlank()) return null

    return service<HttpReferenceService>().resolveHttpMethod(element, value, rangeInElement)
  }

  override fun getUnresolvedMessagePattern(): String {
    return MicroservicesBundle.message("http.method.element.error")
  }

  override fun isReferenceTo(element: PsiElement): Boolean {
    return service<HttpReferenceService>().isReferenceToHttpMethod(element, value)
  }

  override fun getVariants(): Array<Any> {
    return ArrayUtil.toObjectArray(HTTP_METHODS.map {
      LookupElementBuilder.create(it).withIcon(AllIcons.Nodes.PpWeb)
    })
  }
}