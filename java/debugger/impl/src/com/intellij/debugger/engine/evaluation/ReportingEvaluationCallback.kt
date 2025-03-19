// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation

import com.intellij.debugger.engine.evaluation.statistics.JavaDebuggerEvaluatorStatisticsCollector
import com.intellij.debugger.ui.impl.watch.EvaluationDescriptor
import com.intellij.debugger.ui.impl.watch.NodeDescriptorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.xdebugger.Obsolescent
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.ui.tree.nodes.XEvaluationOrigin

class ReportingEvaluationCallback(
  val project: Project,
  val delegate: XDebuggerEvaluator.XEvaluationCallback,
  val evaluationOrigin: XEvaluationOrigin
) : XDebuggerEvaluator.XEvaluationCallback, Obsolescent {

  override fun evaluated(result: XValue) {
    if (result is NodeDescriptorProvider) {
      (result.descriptor as? EvaluationDescriptor)?.logEvaluationResult(success = true)
    }
    delegate.evaluated(result)
  }

  override fun errorOccurred(errorMessage: String) {
    delegate.errorOccurred(errorMessage)
  }

  fun errorOccurred(@NlsContexts.DialogMessage errorMessage: String, descriptor: EvaluationDescriptor?) {
    descriptor.logEvaluationResult(success = false)
    errorOccurred(errorMessage)
  }

  override fun invalidExpression(error: @NlsContexts.DialogMessage String) {
    delegate.invalidExpression(error)
  }

  fun invalidExpression(error: @NlsContexts.DialogMessage String, descriptor: EvaluationDescriptor?) {
    descriptor.logEvaluationResult(success = false)
    invalidExpression(error)
  }

  private fun EvaluationDescriptor?.logEvaluationResult(success: Boolean) {
    val evaluator = this?.getUserData(EvaluationDescriptor.EXPRESSION_EVALUATOR_KEY)
    JavaDebuggerEvaluatorStatisticsCollector.logEvaluationResult(
      project,
      evaluator,
      success,
      evaluationOrigin
    )
  }

  override fun isObsolete(): Boolean {
    return delegate is Obsolescent && delegate.isObsolete
  }
}
