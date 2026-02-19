package com.intellij.microservices.http

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider
import com.intellij.microservices.HttpReferenceService
import com.intellij.microservices.MicroservicesBundle
import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import java.util.regex.Pattern

private val HEADER_NAME_PATTERN = Pattern.compile("[^:\\r\\n]+")

class HttpHeaderReference(element: PsiElement, range: TextRange)
  : PsiReferenceBase<PsiElement>(element, range), EmptyResolveMessageProvider {

  override fun getUnresolvedMessagePattern(): String = MicroservicesBundle.message("http.header.element.error")

  override fun resolve(): PsiElement? {
    val value = value
    if (!HEADER_NAME_PATTERN.matcher(value).matches()) return null

    return service<HttpReferenceService>().resolveHeaderReference(element, value)
  }

  override fun isReferenceTo(element: PsiElement): Boolean {
    return service<HttpReferenceService>().isReferenceToHeaderElement(element, value)
  }

  companion object {
    @JvmStatic
    fun forElement(injectionHost: PsiElement): Array<PsiReference> {
      return forElement(injectionHost, ElementManipulators.getValueTextRange(injectionHost))
    }

    @JvmStatic
    fun forElement(injectionHost: PsiElement, range: TextRange): Array<PsiReference> {
      return arrayOf(HttpHeaderReference(injectionHost, range))
    }
  }
}