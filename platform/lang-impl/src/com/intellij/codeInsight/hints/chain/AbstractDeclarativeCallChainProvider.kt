// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.chain

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.codeInsight.hints.declarative.impl.NoPresentableEntriesException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace

abstract class AbstractDeclarativeCallChainProvider<DotQualifiedExpression : PsiElement, ExpressionType, TypeComputationContext> : InlayHintsProvider {
  companion object {
    private val logger = logger<AbstractDeclarativeCallChainProvider<*, *, *>>()
  }

  override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? {
    if (!isAvailable(file, editor)) return null
    return Collector(file)
  }

  protected data class ExpressionWithType<ExpressionType>(val expression: PsiElement, val type: ExpressionType)


  inner class Collector(private val file: PsiFile) : SharedBypassCollector {
    override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
      val topmostDotQualifiedExpression = element.safeCastUsing(dotQualifiedClass)
                                            // We will process the whole chain using topmost DotQualifiedExpression.
                                            // If the current one has parent then it means that it's not topmost DotQualifiedExpression
                                            ?.takeIf { it.getParentDotQualifiedExpression() == null }
                                          ?: return


      val context = getTypeComputationContext(topmostDotQualifiedExpression)

      var someTypeIsUnknown = false
      val reversedChain =
        generateSequence<PsiElement>(topmostDotQualifiedExpression) {
          it.skipParenthesesAndPostfixOperatorsDown()?.safeCastUsing(dotQualifiedClass)?.getReceiver()
        }
          .drop(1) // Except last to avoid builder.build() which has obvious type
          .filter { (it.nextSibling as? PsiWhiteSpace)?.textContains('\n') == true }
          .map { it to it.getType(context) }
          .takeWhile { (_, type) -> (type != null).also { if (!it) someTypeIsUnknown = true } }
          .map { (expression, type) -> ExpressionWithType(expression, type!!) }
          .windowed(2, partialWindows = true) { it.first() to it.getOrNull(1) }
          .filter { (expressionWithType, prevExpressionWithType) ->
            if (prevExpressionWithType == null) {
              // Show type for expression in call chain on the first line only if it's dot qualified
              dotQualifiedClass.isInstance(expressionWithType.expression.skipParenthesesAndPostfixOperatorsDown())
            }
            else {
              expressionWithType.type != prevExpressionWithType.type ||
              !dotQualifiedClass.isInstance(prevExpressionWithType.expression.skipParenthesesAndPostfixOperatorsDown())
            }
          }
          .map { it.first }
          .toList()
      if (someTypeIsUnknown) return
      if (isChainUnacceptable(reversedChain)) return

      if (reversedChain.asSequence().distinctBy { it.type }.count() < uniqueTypeCount) return

      for ((expression, type) in reversedChain) {
        try {
          sink.addPresentation(InlineInlayPosition(expression.textRange.endOffset, relatedToPrevious = true), hasBackground = true) {
            type.buildTree(expression, file.project, context, this)
          }
        } catch (e: NoPresentableEntriesException) {
          logger.warn("No presentable entries for type: ${presentableType(type)}", e)
        }
      }
      return
    }
  }

  protected abstract fun ExpressionType.buildTree(
    expression: PsiElement,
    project: Project,
    context: TypeComputationContext,
    treeBuilder: PresentationTreeBuilder
  )

  protected open fun isAvailable(file: PsiFile, editor: Editor): Boolean {
    return true
  }

  protected abstract fun PsiElement.getType(context: TypeComputationContext): ExpressionType?

  protected abstract val dotQualifiedClass: Class<DotQualifiedExpression>

  protected open fun isChainUnacceptable(chain: List<ExpressionWithType<ExpressionType & Any>>) : Boolean {
    return false
  }

  /**
   * Implementation must NOT skip parentheses and postfix operators
   */
  protected abstract fun DotQualifiedExpression.getReceiver(): PsiElement?

  protected abstract fun DotQualifiedExpression.getParentDotQualifiedExpression(): DotQualifiedExpression?

  protected abstract fun PsiElement.skipParenthesesAndPostfixOperatorsDown(): PsiElement?

  protected abstract fun getTypeComputationContext(topmostDotQualifiedExpression: DotQualifiedExpression): TypeComputationContext

  protected open fun presentableType(type: ExpressionType) : String {
    return type.toString()
  }

  protected val uniqueTypeCount: Int
    get() = 2

  private fun <T> Any.safeCastUsing(clazz: Class<T>) = if (clazz.isInstance(this)) clazz.cast(this) else null

}