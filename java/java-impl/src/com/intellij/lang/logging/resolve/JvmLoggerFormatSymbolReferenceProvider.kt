// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.logging.resolve

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceProvider
import com.intellij.model.search.SearchRequest
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiCallExpression
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.util.PsiTreeUtil

class JvmLoggerFormatSymbolReferenceProvider : PsiSymbolReferenceProvider {
  override fun getReferences(element: PsiExternalReferenceHost, hints: PsiSymbolReferenceHints): Collection<PsiSymbolReference> {
    if (!hintsCheck(hints)) return listOf()
    val literalExpression = element as? PsiLiteralExpression ?: return listOf()
    return getLogArgumentReferences(literalExpression)
  }

  private fun hintsCheck(hints: PsiSymbolReferenceHints): Boolean {
    if (!hints.referenceClass.isAssignableFrom(JvmLoggerArgumentSymbolReference::class.java)) return false
    val targetClass = hints.targetClass
    if (targetClass != null && !targetClass.isAssignableFrom(JvmLoggerArgumentSymbol::class.java)) return false
    val target = hints.target
    return target == null || target is JvmLoggerArgumentSymbol
  }

  override fun getSearchRequests(project: Project, target: Symbol): Collection<SearchRequest> {
    return listOf()
  }

}

fun getLogArgumentReferences(element: PsiLiteralExpression): List<PsiSymbolReference> {
  val literalExpression = element as? PsiLiteralExpression ?: return listOf()
  val callExpression = PsiTreeUtil.getParentOfType(literalExpression, PsiCallExpression::class.java) ?: return listOf()

  val arguments = callExpression.argumentList?.expressions ?: return mutableListOf()
  val firstIdx = literalExpression.text.indexOfFirst { it == '{' }
  if (firstIdx == -1) {
    return emptyList()
  }
  return listOf(JvmLoggerArgumentSymbolReference(literalExpression, TextRange(firstIdx, firstIdx + 2), arguments[1]))
}