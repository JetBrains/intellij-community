// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.chain

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace

abstract class AbstractDeclarativeCallChainProvider<DotQualifiedExpression : PsiElement, ExpressionType, TypeComputationContext> : InlayHintsProvider {

  override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? {
    return Collector(file)
  }

  inner class Collector(private val file: PsiFile) : SharedBypassCollector {
    override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
      val topmostDotQualifiedExpression = element.safeCastUsing(dotQualifiedClass)
                                            // We will process the whole chain using topmost DotQualifiedExpression.
                                            // If the current one has parent then it means that it's not topmost DotQualifiedExpression
                                            ?.takeIf { it.getParentDotQualifiedExpression() == null }
                                          ?: return

      data class ExpressionWithType(val expression: PsiElement, val type: ExpressionType)

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

      if (reversedChain.asSequence().distinctBy { it.type }.count() < uniqueTypeCount) return

      for ((expression, type) in reversedChain) {
        sink.addPresentation(InlineInlayPosition(expression.textRange.endOffset, relatedToPrevious = true), hasBackground = true) {
          type.buildTree(expression, file.project, context, this)
        }
      }
      return
    }
  }

  override fun createCollectorForPreview(file: PsiFile, editor: Editor): InlayHintsCollector {
    return Collector(file)
  }

  protected abstract fun ExpressionType.buildTree(
    expression: PsiElement,
    project: Project,
    context: TypeComputationContext,
    treeBuilder: PresentationTreeBuilder
  )

  protected abstract fun PsiElement.getType(context: TypeComputationContext): ExpressionType?

  protected abstract val dotQualifiedClass: Class<DotQualifiedExpression>

  /**
   * Implementation must NOT skip parentheses and postfix operators
   */
  protected abstract fun DotQualifiedExpression.getReceiver(): PsiElement?

  protected abstract fun DotQualifiedExpression.getParentDotQualifiedExpression(): DotQualifiedExpression?

  protected abstract fun PsiElement.skipParenthesesAndPostfixOperatorsDown(): PsiElement?

  protected abstract fun getTypeComputationContext(topmostDotQualifiedExpression: DotQualifiedExpression): TypeComputationContext

  protected val uniqueTypeCount: Int
    get() = 2

  private fun <T> Any.safeCastUsing(clazz: Class<T>) = if (clazz.isInstance(this)) clazz.cast(this) else null

}