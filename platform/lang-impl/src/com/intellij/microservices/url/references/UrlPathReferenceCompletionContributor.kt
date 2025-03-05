// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.url.references

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceUtil
import com.intellij.util.SmartList

internal class UrlPathReferenceCompletionContributor : CompletionContributor() {

  private fun mkLookup(element: Any, lookupString: String) =
    LookupElementBuilder.create(element, lookupString)
      .withIcon(AllIcons.General.Web)

  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val containingFile = parameters.position.containingFile
    val multiReference = containingFile.findReferenceAt(parameters.offset) ?: return

    val schemeReference = PsiReferenceUtil.findReferenceOfClass(multiReference, SchemeReference::class.java)
    val authorityReference = PsiReferenceUtil.findReferenceOfClass(multiReference, AuthorityReference::class.java)

    if (authorityReference != null) {
      val prefix = CompletionUtil.findReferencePrefix(parameters) ?: ""
      val authCompletion = result.withPrefixMatcher(result.prefixMatcher.cloneWithPrefix(prefix))
      val schemaHint = schemeReference?.let { sr -> sr.givenValue.takeIf { it in sr.supportedSchemes } }
      for (authority in getAvailableAuthoritiesForFile(containingFile, schemaHint)) {
        authCompletion.consume(mkLookup(authority, authority.name))
      }
      if (schemeReference?.rangeInElement != authorityReference.rangeInElement) return
    }

    PsiReferenceUtil.findReferenceOfClass(multiReference, UrlPathReference::class.java)?.let { urlPathReference ->
      for (lookupElement in urlPathReference.getVariantsIterator()) {
        ProgressManager.checkCanceled()
        result.consume(lookupElement)
      }
    }

    if (schemeReference != null) {
      val supportedSchemes = schemeReference.supportedSchemes
      for (scheme in supportedSchemes) {
        result.consume(mkLookup(scheme, scheme))
      }
      for (authority in getAvailableAuthoritiesForFile(containingFile, null)) {
        for (scheme in supportedSchemes) {
          val schemeAndHost = scheme + authority.name
          result.consume(PrioritizedLookupElement.withPriority(mkLookup(schemeAndHost, schemeAndHost), -20.0))
        }
      }
    }
  }

  private fun getAvailableAuthoritiesForFile(containingFile: PsiFile, schema: String?): Sequence<AuthorityPomTarget> =
    getAvailableAuthorities(containingFile.project, schema).asSequence() + collectAuthorityReferences(containingFile, schema)

  private fun collectAuthorityReferences(file: PsiFile, schema: String?): Sequence<AuthorityPomTarget> =
    SyntaxTraverser.psiTraverser(file).filter(PsiLanguageInjectionHost::class.java)
      .asSequence().flatMap { host -> collectAuthRefsForGivenSchema(host, schema) }
      .mapNotNull { it.givenValue?.takeIf { it.isNotBlank() }?.let { AuthorityPomTarget(it) } }

  private fun collectAuthRefsForGivenSchema(host: PsiLanguageInjectionHost, schema: String?): List<AuthorityReference> {
    val schemeRefs = SmartList<SchemeReference>()
    val authRefs = SmartList<AuthorityReference>()
    host.references.asSequence().flatMap { PsiReferenceUtil.unwrapMultiReference(it) }.forEach { ref ->
      when (ref) {
        is AuthorityReference -> authRefs.add(ref)
        is SchemeReference -> schemeRefs.add(ref)
      }
    }
    if (schema == null) return authRefs

    return authRefs.filter { ar ->
      schemeRefs.any {
        it.rangeInElement.contains(ar.rangeInElement.startOffset - 1) && it.givenValue == schema
      }
    }
  }
}