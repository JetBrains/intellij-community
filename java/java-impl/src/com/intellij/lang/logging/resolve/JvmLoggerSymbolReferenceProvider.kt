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
import com.intellij.psi.PsiElement
import com.intellij.util.logging.*
import com.intellij.util.logging.PlaceholderLoggerType.*
import org.jetbrains.uast.*

class JvmLoggerSymbolReferenceProvider : PsiSymbolReferenceProvider {
  override fun getReferences(element: PsiExternalReferenceHost, hints: PsiSymbolReferenceHints): Collection<PsiSymbolReference> {
    if (!hintsCheck(hints)) return emptyList()

    val literalExpression = element.toUElementOfType<UExpression>() ?: return emptyList()
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
  if (literalExpression != context.logStringArgument || context.partHolderList.size > 1) return null

  val placeholderCountResult = solvePlaceholderCount(context.loggerType, context.placeholderParameters.size, context.partHolderList)
  if (placeholderCountResult.status != PlaceholdersStatus.EXACTLY || placeholderCountResult.placeholderRangesInPartHolderList.size != 1) return null

  val placeholderRanges = placeholderCountResult.placeholderRangesInPartHolderList.single()
  val rangeWithParameterList = placeholderRanges.rangeList.zip(context.placeholderParameters)
  val psiLiteralExpression = literalExpression.sourcePsi ?: return null
  val value = literalExpression.evaluateString() ?: return null

  val offset = getOffsetInText(psiLiteralExpression, value) ?: return null
  val placeholderParametersSize = context.placeholderParameters.size

  val loggerReferenceList = rangeWithParameterList.map { (range, parameter) ->
    if (range == null) return null
    val alignedRange = range.shiftTextRange(offset)
    val parameterPsi = parameter.sourcePsi ?: return null
    JvmLoggerArgumentSymbolReference(psiLiteralExpression, alignedRange, parameterPsi)
  }

  return when (context.loggerType) {
    SLF4J, LOG4J_OLD_STYLE, LOG4J_FORMATTED_STYLE -> {
      loggerReferenceList.take(if (context.lastArgumentIsException) placeholderParametersSize - 1 else placeholderParametersSize)
    }
    SLF4J_EQUAL_PLACEHOLDERS, LOG4J_EQUAL_PLACEHOLDERS -> {
      loggerReferenceList
    }
    else -> null
  }
}

private fun TextRange.shiftTextRange(shift: Int): TextRange = TextRange(this.startOffset + shift, this.endOffset + shift)

private fun getOffsetInText(expression: PsiElement, value: String): Int? {
  val text = expression.text
  if (text == null) return null

  val offset = text.indexOf(value)

  if (offset == -1) return null

  return offset
}