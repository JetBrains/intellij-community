// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.logging

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiTypes
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.siyeh.ig.psiutils.TypeUtils
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

internal class LoggingStringPartEvaluator {

  /**
   * @param text       - null if it is a literal, which is not String or Character
   * @param isConstant - it is a constant
   */
  internal data class PartHolder(val text: String?, val isConstant: Boolean)

  private data class Context(val depth: Int, val maxParts: Int)
  companion object {
    fun calculateValue(expression: UExpression): List<PartHolder>? {
      if (!isString(expression)) return null
      return tryJoin(recursiveCalculateValue(expression, Context(depth = 10, maxParts = 20)))
    }

    private fun recursiveCalculateValue(expression: UExpression?,
                                        initContext: Context): List<PartHolder> {
      ProgressManager.checkCanceled()
      if (expression == null) {
        return listOf(PartHolder(null, false))
      }
      val context = initContext.copy(depth = initContext.depth - 1)
      if (context.maxParts <= 0 || context.depth <= 0) {
        return listOf(PartHolder(null, false))
      }
      if (!isString(expression)) {
        return listOf(PartHolder(null, true))
      }
      return when (expression) {
        is ULiteralExpression -> getFromLiteralExpression(expression)
        is UPolyadicExpression -> getFromPolyadicExpression(expression, context)
        is UParenthesizedExpression -> recursiveCalculateValue(expression.skipParenthesizedExprDown(), context)
        is USimpleNameReferenceExpression -> getFromReferenceExpression(expression, context)
        else -> listOf(PartHolder(null, false))
      }
    }

    private fun getFromLiteralExpression(element: ULiteralExpression): List<PartHolder> {
      if (isString(element)) {
        return listOf(PartHolder(element.value?.toString(), true))
      }
      return listOf(PartHolder(null, true))
    }

    private fun getFromPolyadicExpression(expression: UPolyadicExpression,
                                          context: Context): List<PartHolder> {
      if (!isString(expression)) {
        return listOf(PartHolder(null, true))
      }
      if (expression.operator != UastBinaryOperator.PLUS) {
        return listOf(PartHolder(null, false))
      }
      val result = mutableListOf<PartHolder>()
      for (operand in expression.operands) {
        result.addAll(recursiveCalculateValue(operand, context.copy(maxParts = context.maxParts - expression.operands.size + 1)))
        if (result.size >= context.maxParts) {
          result.add(PartHolder(null, false))
          return result
        }
      }
      return result
    }

    private fun tryJoin(holders: List<PartHolder>?): List<PartHolder>? {
      if (holders == null) return null
      val result = mutableListOf<PartHolder>()
      for (holder in holders) {
        if (holder.isConstant && holder.text != null && result.size > 0 && result.last().isConstant && result.last().text != null) {
          val newPart = PartHolder(result.last().text + holder.text, true)
          result.removeLast()
          result.add(newPart)
        }
        else {
          result.add(holder)
        }
      }
      return result
    }

    private fun getFromReferenceExpression(expression: USimpleNameReferenceExpression,
                                           context: Context): List<PartHolder> {
      val resolvedUElement = expression.resolveToUElement()
      if (resolvedUElement is UField && resolvedUElement.isFinal) {
        return recursiveCalculateValue(resolvedUElement.uastInitializer, context)
      }
      if (resolvedUElement is ULocalVariable && isNotAssignment(resolvedUElement)) {
        return recursiveCalculateValue(resolvedUElement.uastInitializer, context)
      }
      return listOf(PartHolder(null, false))
    }

    private fun isNotAssignment(localVariable: ULocalVariable): Boolean {
      val containingUMethod = localVariable.getContainingUMethod() ?: return false
      val sourcePsi = containingUMethod.javaPsi
      val project = sourcePsi.project
      return CachedValuesManager.getManager(project).getCachedValue(sourcePsi, CachedValueProvider {
        val visitor = object : AbstractUastVisitor() {
          val used: MutableSet<ULocalVariable> = mutableSetOf()
          override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
            if (node.operator == UastBinaryOperator.ASSIGN) {
              val leftOperand = node.leftOperand
              if (leftOperand is USimpleNameReferenceExpression) {
                val resolveToUElement = leftOperand.resolveToUElement()
                if (resolveToUElement is ULocalVariable) {
                  used.add(resolveToUElement)
                }
              }
            }
            return super.visitBinaryExpression(node)
          }
        }
        val method = sourcePsi.toUElement()
        method?.accept(visitor)
        return@CachedValueProvider CachedValueProvider.Result.create(visitor.used, PsiModificationTracker.MODIFICATION_COUNT)
      }).contains(localVariable).not()
    }

    private fun isString(element: UExpression): Boolean {
      val expressionType = element.getExpressionType()
      return TypeUtils.isJavaLangString(expressionType) || PsiTypes.charType() == expressionType
    }
  }
}