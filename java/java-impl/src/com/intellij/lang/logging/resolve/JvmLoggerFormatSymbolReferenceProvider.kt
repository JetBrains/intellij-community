// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.logging.resolve

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceProvider
import com.intellij.model.search.SearchRequest
import com.intellij.openapi.project.Project
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.toUElementOfType

class JvmLoggerFormatSymbolReferenceProvider : PsiSymbolReferenceProvider {
  override fun getReferences(element: PsiExternalReferenceHost, hints: PsiSymbolReferenceHints): Collection<PsiSymbolReference> {
    if (!hintsCheck(hints)) return listOf()

    val literalExpression = element.toUElementOfType<ULiteralExpression>() ?: return listOf()
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

fun getLogArgumentReferences(literalExpression: ULiteralExpression): List<PsiSymbolReference> {
  val callExpression = literalExpression.getParentOfType<UCallExpression>()

  return listOf()
}