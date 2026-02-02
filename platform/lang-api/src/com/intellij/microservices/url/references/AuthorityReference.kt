package com.intellij.microservices.url.references

import com.intellij.microservices.HttpReferenceService
import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.ResolveResult

class AuthorityReference @JvmOverloads constructor(
  val givenValue: String?,
  host: PsiLanguageInjectionHost,
  range: TextRange,
  val customNavigate: ((UrlSegmentReference) -> Unit)? = null,
) : PsiReferenceBase.Poly<PsiElement>(host, range, false), UrlSegmentReference {
  override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> =
    PsiElementResolveResult.createResults(resolve())

  // resolve in any case
  override fun resolve(): PsiElement {
    return service<HttpReferenceService>().resolveAuthorityReference(this, element, givenValue ?: value, customNavigate)
  }

  override fun toString(): String {
    val refValue = givenValue ?: value
    return "AuthorityReference($refValue, $rangeInElement)"
  }
}