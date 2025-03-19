package com.intellij.microservices.url.parameters

import com.intellij.microservices.HttpReferenceService
import com.intellij.microservices.url.references.UrlPathContext
import com.intellij.microservices.url.references.UrlSegmentReference
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments
import com.intellij.openapi.util.TextRange
import com.intellij.pom.PomRenameableTarget
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.util.mkAttachments

interface QueryParameterNameTarget : PomRenameableTarget<Any?> {
  fun toElement(forceFindUsagesOnNavigate: Boolean): PsiElement
}

class QueryParameterNameReference(
  val context: UrlPathContext,
  host: PsiLanguageInjectionHost,
  rangeInElement: TextRange = ElementManipulators.getValueTextRange(host),
  private val forceFindUsagesOnNavigate: Boolean = false,
) : PsiReferenceBase<PsiElement>(host, rangeInElement, false), UrlSegmentReference {

  private val queryParameterPomTarget: QueryParameterNameTarget? by lazy {
    if (context.resolveRequests.none()) return@lazy null

    service<HttpReferenceService>().createQueryParameterNameTarget(context, value, element.project)
  }

  override fun resolve(): PsiElement? = queryParameterPomTarget?.toElement(forceFindUsagesOnNavigate)

  override fun getValue(): String {
    try {
      return super.getValue()
    }
    catch (e: Exception) {
      if (e is ControlFlowException) throw e
      // Diagnostics for IDEA-319862
      throw RuntimeExceptionWithAttachments(e, *mkAttachments(element))
    }
  }

  override fun getVariants(): Array<Any> {
    return service<HttpReferenceService>().getQueryParameterNameVariants(queryParameterPomTarget)
  }
}