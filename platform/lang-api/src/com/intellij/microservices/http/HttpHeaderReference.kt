package com.intellij.microservices.http

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider
import com.intellij.microservices.MicroservicesBundle
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase

class HttpHeaderReference(element: PsiElement, range: TextRange)
  : PsiReferenceBase<PsiElement>(element, range), EmptyResolveMessageProvider {

  override fun getUnresolvedMessagePattern(): String = MicroservicesBundle.message("http.header.element.error")

  override fun resolve(): PsiElement? {
    val value = value
    if (!HttpHeadersDictionary.HEADER_NAME_PATTERN.matcher(value).matches()) return null

    return HttpHeaderElement(element, value)
  }

  override fun isReferenceTo(element: PsiElement): Boolean {
    return element is HttpHeaderElement && element.name == value
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