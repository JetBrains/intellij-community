// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation.statistics

import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluatorImpl
import com.intellij.debugger.engine.evaluation.statistics.StatisticsEvaluationResult.FAILURE
import com.intellij.debugger.engine.evaluation.statistics.StatisticsEvaluationResult.SUCCESS
import com.intellij.debugger.ui.impl.watch.CompilingEvaluator
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.impl.evaluate.XEvaluationOrigin

object JavaDebuggerEvaluatorStatisticsCollector : CounterUsagesCollector() {

  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("java.debugger.evaluator", 1)

  // fields
  private val evaluatorField = EventFields.Enum<StatisticsEvaluator>("evaluator")
  private val resultField = EventFields.Enum<StatisticsEvaluationResult>("result")
  private val originField = EventFields.Enum<XEvaluationOrigin>("origin")

  // events
  private val evaluationEvent = GROUP.registerEvent("evaluation.result", evaluatorField, resultField, originField)

  @JvmStatic
  fun logEvaluationResult(project: Project?, evaluator: ExpressionEvaluator?, success: Boolean, origin: XEvaluationOrigin) {
    evaluationEvent.log(project, StatisticsEvaluator.fromEvaluator(evaluator), if (success) SUCCESS else FAILURE, origin)
  }
}

private enum class StatisticsEvaluator {
  COMPILING,
  INTERPRETING,
  EXTERNAL,
  ANY;

  companion object {
    fun fromEvaluator(evaluator: ExpressionEvaluator?): StatisticsEvaluator = when (evaluator) {
      is CompilingEvaluator -> COMPILING
      is ExpressionEvaluatorImpl -> {
        if (evaluator.isExternalEvaluator) EXTERNAL else INTERPRETING
      }
      else -> ANY
    }
  }
}

private enum class StatisticsEvaluationResult {
  SUCCESS,
  FAILURE
}
