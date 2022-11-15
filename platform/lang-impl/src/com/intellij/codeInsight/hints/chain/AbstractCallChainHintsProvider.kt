// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.chain

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.layout.panel
import com.intellij.util.asSafely
import com.intellij.util.ui.JBUI
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.text.DefaultFormatter

abstract class AbstractCallChainHintsProvider<DotQualifiedExpression : PsiElement, ExpressionType, TypeComputationContext> :
  InlayHintsProvider<AbstractCallChainHintsProvider.Settings> {

  protected data class ExpressionWithType<ExpressionType>(val expression: PsiElement, val type: ExpressionType)

  override fun getCollectorFor(file: PsiFile, editor: Editor, settings: Settings, sink: InlayHintsSink): InlayHintsCollector? {
    if (file.project.isDefault) return null
    return object : FactoryInlayHintsCollector(editor) {
      override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
        if (file.project.service<DumbService>().isDumb) return true

        val topmostDotQualifiedExpression = element.safeCastUsing(dotQualifiedClass)
                                             // We will process the whole chain using topmost DotQualifiedExpression.
                                             // If the current one has parent then it means that it's not topmost DotQualifiedExpression
                                             ?.takeIf { it.getParentDotQualifiedExpression() == null }
                                            ?: return true

        val context = getTypeComputationContext(topmostDotQualifiedExpression)

        val reversedChain = topmostDotQualifiedExpression.assembleChainCall(context)
        if (reversedChain == null) return true

        if (checkIfShouldSkip(reversedChain, settings)) return true

        for ((expression, type) in reversedChain) {
          sink.addInlineElement(
            expression.textRange.endOffset,
            true,
            type.getInlayPresentation(expression, factory, file.project, context),
            false
          )
        }
        return true
      }
    }
  }

  override val name: String
    get() = CodeInsightBundle.message("inlay.hints.chain.call.chain")

  final override fun createConfigurable(settings: Settings): ImmediateConfigurable = object : ImmediateConfigurable {
    val uniqueTypeCountName = CodeInsightBundle.message("inlay.hints.chain.minimal.unique.type.count.to.show.hints")

    private val uniqueTypeCount = JBIntSpinner(1, 1, 10)

    override fun createComponent(listener: ChangeListener): JPanel {
      reset()
      // Workaround to get immediate change, not only when focus is lost. To be changed after moving to polling model
      val formatter = (uniqueTypeCount.editor as JSpinner.NumberEditor).textField.formatter as DefaultFormatter
      formatter.commitsOnValidEdit = true
      uniqueTypeCount.addChangeListener {
        handleChange(listener)
      }
      val panel = panel {
        row {
          label(uniqueTypeCountName)
          uniqueTypeCount(pushX)
        }
      }
      panel.border = JBUI.Borders.empty(5)
      return panel
    }

    override fun reset() {
      uniqueTypeCount.value = settings.uniqueTypeCount
    }

    private fun handleChange(listener: ChangeListener) {
      settings.uniqueTypeCount = uniqueTypeCount.number
      listener.settingsChanged()
    }
  }

  protected fun DotQualifiedExpression.assembleChainCall(context: TypeComputationContext): List<ExpressionWithType<ExpressionType>>? {
    var someTypeIsUnknown = false
    val reversedChain =
      generateSequence<PsiElement>(this) {
        it.skipParenthesesAndPostfixOperatorsDown()?.safeCastUsing(dotQualifiedClass)?.getReceiver()
      }
        .drop(1) // Except last to avoid builder.build() which has obvious type
        .filter { it.nextSibling.asSafely<PsiWhiteSpace>()?.textContains('\n') == true }
        .map { it to it.getType(context) }
        .takeWhile { (_, type) -> (type != null).also { if (!it) someTypeIsUnknown = true } }
        .map { (expression, type) -> ExpressionWithType<ExpressionType>(expression, type!!) }
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
    return if (someTypeIsUnknown) null else reversedChain
  }

  protected fun checkIfShouldSkip(chain: List<ExpressionWithType<ExpressionType>>, settings: Settings): Boolean {
    return chain.asSequence().distinctBy { it.type }.count() < settings.uniqueTypeCount
  }

  protected abstract fun ExpressionType.getInlayPresentation(
    expression: PsiElement,
    factory: PresentationFactory,
    project: Project,
    context: TypeComputationContext
  ): InlayPresentation

  protected abstract fun PsiElement.getType(context: TypeComputationContext): ExpressionType?

  protected abstract val dotQualifiedClass: Class<DotQualifiedExpression>

  /**
   * Implementation must NOT skip parentheses and postfix operators
   */
  protected abstract fun DotQualifiedExpression.getReceiver(): PsiElement?

  protected abstract fun DotQualifiedExpression.getParentDotQualifiedExpression(): DotQualifiedExpression?

  protected abstract fun PsiElement.skipParenthesesAndPostfixOperatorsDown(): PsiElement?

  protected abstract fun getTypeComputationContext(topmostDotQualifiedExpression: DotQualifiedExpression): TypeComputationContext

  protected fun <T> Any.safeCastUsing(clazz: Class<T>) = if (clazz.isInstance(this)) clazz.cast(this) else null

  final override fun createSettings() = Settings()

  data class Settings(var uniqueTypeCount: Int) {
    constructor() : this(2)
  }
}
