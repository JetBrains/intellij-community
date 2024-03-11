// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.logging.resolve

import com.intellij.lang.logging.resolve.PlaceholderLoggerType.SLF4J
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceProvider
import com.intellij.model.search.SearchRequest
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.uast.*

class JvmLoggerSymbolReferenceProvider : PsiSymbolReferenceProvider {
  override fun getReferences(element: PsiExternalReferenceHost, hints: PsiSymbolReferenceHints): Collection<PsiSymbolReference> {
    if (!hintsCheck(hints)) return listOf()

    val literalExpression = element.toUElementOfType<UExpression>() ?: return listOf()
    return getLogArgumentReferences(literalExpression) ?: emptyList()
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

fun getLogArgumentReferences(literalExpression: UExpression): List<PsiSymbolReference>? {
  val uCallExpression = literalExpression.getParentOfType<UCallExpression>() ?: return null

  val log4jHasImplementationForSlf4j = LoggingUtil.hasBridgeFromSlf4jToLog4j2(uCallExpression)

  val context = getPlaceholderContext(uCallExpression, LOGGER_RESOLVE_TYPE_SEARCHERS, log4jHasImplementationForSlf4j) ?: return null

  if (context.partHolderList.size > 1) return null
  val placeholderCountResult = solvePlaceholderCount(context.loggerType, context.placeholderParameters.size, context.partHolderList)
  if (placeholderCountResult.status != PlaceholdersStatus.EXACTLY || placeholderCountResult.placeholderRangesInPartHolderList.size != 1) return null

  val placeholderRanges = placeholderCountResult.placeholderRangesInPartHolderList.single()
  val parameterExpressions = context.placeholderParameters
  val zipped = placeholderRanges.rangeList.zip(parameterExpressions)

  val psiLiteralExpression = literalExpression.sourcePsi ?: return null
  val value = literalExpression.evaluateString() ?: return null

  val result = when (context.loggerType) {
    SLF4J -> {
      zipped.map { (range, parameter) ->
        if (range == null) return null
        val alignedRange = mapTextRange(psiLiteralExpression, value, range.startOffset, range.endOffset) ?: return null
        val parameterPsi = parameter.sourcePsi ?: return null
        JvmLoggerArgumentSymbolReference(psiLiteralExpression, alignedRange, parameterPsi)
      }
    }
    else -> null
  }

  return result
}

private fun mapTextRange(expression: PsiElement, value: String, from: Int, to: Int): TextRange? {
  val text = expression.text
  if (text == null) return null

  val offset = text.indexOf(value)

  if (offset == -1) return null

  return TextRange(from + offset, to + offset)
}