// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.logs

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.platform.ml.impl.DescribedLevelScheme
import com.intellij.platform.ml.impl.MLTask
import com.intellij.platform.ml.impl.MLTaskApproach.Companion.findMlTaskApproach
import com.intellij.platform.ml.impl.apiPlatform.MLApiPlatform
import com.intellij.platform.ml.impl.apiPlatform.ReplaceableIJPlatform
import com.intellij.platform.ml.impl.model.MLModel
import com.intellij.platform.ml.impl.monitoring.MLTaskGroupListener
import com.intellij.platform.ml.impl.monitoring.MLTaskGroupListener.ApproachToListener.Companion.monitoredBy
import com.intellij.platform.ml.impl.session.AnalysedTierScheme
import com.intellij.platform.ml.impl.session.analysis.AnalysisLogger
import com.intellij.platform.ml.impl.session.analysis.SessionAnalyserProvider
import com.intellij.platform.ml.impl.session.analysis.StructureAnalyser
import com.intellij.platform.ml.impl.session.analysis.createJoinedAnalyser
import com.intellij.util.application
import org.jetbrains.annotations.ApiStatus

/**
 * For the given [this] event group, registers an event, that will be corresponding to a finish of [task]'s session.
 * The event will be logged automatically.
 *
 * You could consider two logging schemes:
 *  - The one, that is putting entire sessions to one event [com.intellij.platform.ml.impl.logs.EntireSessionLoggingStrategy]
 *  - The one, that is splitting ML sessions into multiple events [com.intellij.platform.ml.impl.logs.SessionAsMultipleEventsLoggingStrategy]
 *
 * The attached listener will stop listening at the application's disposal
 *
 * @param eventName Event's name in the event group
 * @param task Task whose finish will be recorded
 * @param loggingStrategy Chosen scheme for the session structure
 * @param analysisMethods All analyzers that will contribute their analytics to the ML logs
 * @param apiPlatform Platform that will be used to build event validators: it should include desired analysis and description features.
 */
@ApiStatus.Internal
fun <M : MLModel<P>, P : Any> EventLogGroup.registerMLTaskLogging(
  eventName: String,
  task: MLTask<P>,
  loggingStrategy: MLSessionLoggingStrategy<P>,
  analysisMethods: AnalysisMethods<M, P> = AnalysisMethods.none(),
  apiPlatform: MLApiPlatform = ReplaceableIJPlatform
) {
  val approachBuilder = findMlTaskApproach(task, apiPlatform)
  val levelsScheme = approachBuilder.buildApproachSessionDeclaration(apiPlatform)

  val sessionAnalyser = analysisMethods.sessionAnalysers.createJoinedAnalyser()
  val structureAnalyser = analysisMethods.structureAnalysers.createJoinedAnalyser()

  val mlSessionLoggerBuilder = loggingStrategy.configureLogger(
    sessionAnalysisDeclaration = sessionAnalyser.declaration,
    sessionStructureAnalysisDeclaration = buildAnalysedLevelsScheme(levelsScheme, structureAnalyser),
    eventLogGroup = this,
    eventPrefix = eventName
  )
  val analysisLogger = AnalysisLogger(sessionAnalyser, structureAnalyser, mlSessionLoggerBuilder)
  return apiPlatform.addTaskListener(
    object : MLTaskGroupListener {
      override val approachListeners: Collection<MLTaskGroupListener.ApproachToListener<*, *>> = listOf(
        approachBuilder.javaClass monitoredBy analysisLogger
      )
    },
    application
  )
}

@ApiStatus.Internal
data class AnalysisMethods<M : MLModel<P>, P : Any>(
  val sessionAnalysers: Collection<SessionAnalyserProvider<M, P>>,
  val structureAnalysers: Collection<StructureAnalyser<M, P>>,
) {
  companion object {
    fun <M : MLModel<P>, P : Any> none() = AnalysisMethods<M, P>(emptyList(), emptyList())
  }
}

private fun <M : MLModel<P>, P : Any> buildAnalysedLevelsScheme(
  levelsScheme: List<DescribedLevelScheme>,
  structureAnalyser: StructureAnalyser<M, P>
): List<AnalysedLevelScheme> {
  return levelsScheme.map { describedLevelScheme ->
    AnalysedLevelScheme(
      main = describedLevelScheme.main.mapValues { (tier, tierDescription) ->
        AnalysedTierScheme(tierDescription.description, structureAnalyser.declaration[tier] ?: emptySet())
      },
      additional = describedLevelScheme.additional
    )
  }
}
