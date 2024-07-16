// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.logs

import com.intellij.platform.ml.*
import com.intellij.platform.ml.MLTaskApproach.Companion.findMlTaskApproach
import com.intellij.platform.ml.MLTaskApproach.Companion.startMLSession
import com.intellij.platform.ml.analysis.AnalysisLogger
import com.intellij.platform.ml.analysis.SessionAnalyserProvider
import com.intellij.platform.ml.analysis.StructureAnalyser
import com.intellij.platform.ml.analysis.createJoinedAnalyser
import com.intellij.platform.ml.environment.Environment
import com.intellij.platform.ml.logs.schema.EventField
import com.intellij.platform.ml.logs.schema.EventPair
import com.intellij.platform.ml.monitoring.MLTaskGroupListener
import com.intellij.platform.ml.monitoring.MLTaskGroupListener.ApproachToListener.Companion.monitoredBy
import com.intellij.platform.ml.session.AnalysedRootContainer
import com.intellij.platform.ml.session.AnalysedTierScheme
import com.intellij.platform.ml.session.DescribedTierScheme
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface MLSessionComponentLogger {
  fun log(eventPairs: List<EventPair<*>>)
}

@ApiStatus.Internal
interface MLSessionComponentRegister {
  fun registerComponent(name: String, eventFields: List<EventField<*>>): MLSessionComponentLogger
}

@ApiStatus.Internal
interface MLSessionLoggingStrategy<P : Any> {
  /**
   * Creates a logger to log the described ML session.
   *
   * @param sessionAnalysisDeclaration The additional fields for the ML tasks, taken from the various [com.intellij.platform.ml.analysis.SessionAnalyser].
   * @param sessionStructureAnalysisDeclaration The structure of the session tiers' description features. The features are taken from [com.intellij.platform.ml.TierDescriptor],
   * and the analysis from [com.intellij.platform.ml.analysis.StructureAnalyser].
   * @param componentPrefix A unique prefix for the logger's events' names
   * @param componentRegister TODO
   */
  fun registerLogComponents(sessionAnalysisDeclaration: List<EventField<*>>,
                            sessionStructureAnalysisDeclaration: List<AnalysedLevelScheme>,
                            componentPrefix: String,
                            componentRegister: MLSessionComponentRegister): MLSessionLogger<P>
}

typealias AnalysedLevelScheme = LevelSignature<PerTier<AnalysedTierScheme>, PerTier<DescribedTierScheme>>

@ApiStatus.Internal
interface MLSessionLogger<P : Any> {
  /**
   * Takes all the collected information during the ML session, and logs it.
   *
   * @param apiPlatform The platform, that is used in this session
   * @param permanentSessionEnvironment The tier instances, located on the top ("permanent") level of the ML Task
   * @param permanentCallParameters The call parameters from [com.intellij.platform.ml.MLTaskApproach.Companion.startMLSession] will be passed here
   * @param session The additional session fields, collected by the [com.intellij.platform.ml.analysis.SessionAnalyser]s.
   * @param structure The ML session's structure that contains tiers' descriptions, as well as analysis.
   */
  fun logComponents(apiPlatform: MLApiPlatform, permanentSessionEnvironment: Environment, permanentCallParameters: Environment,
                    session: List<EventPair<*>>, structure: AnalysedRootContainer<P>?)
}


/**
 * For the given [this] event group, registers an event, that will be corresponding to a finish of [task]'s session.
 * The event will be logged automatically.
 *
 * You could consider two logging schemes:
 *  - The one, that is putting entire sessions to one event [com.intellij.platform.ml.logs.EntireSessionLoggingStrategy]
 *  - The one, that is splitting ML sessions into multiple events [com.intellij.platform.ml.logs.SessionAsMultipleEventsLoggingStrategy]
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
fun <M : MLModel<P>, P : Any> MLSessionComponentRegister.registerMLTaskLogging(
  eventName: String,
  task: MLTask<P>,
  loggingStrategy: MLSessionLoggingStrategy<P>,
  analysisMethods: AnalysisMethods<M, P>,
  apiPlatform: MLApiPlatform,
): MLApiPlatform.ExtensionController {
  val approachBuilder = findMlTaskApproach(task, apiPlatform)
  val levelsScheme = approachBuilder.buildApproachSessionDeclaration(apiPlatform)

  val sessionAnalyser = analysisMethods.sessionAnalysers.createJoinedAnalyser(apiPlatform.systemLoggerBuilder)
  val structureAnalyser = analysisMethods.structureAnalysers.createJoinedAnalyser()

  val mlSessionLoggerBuilder = loggingStrategy.registerLogComponents(
    sessionAnalysisDeclaration = sessionAnalyser.declaration,
    sessionStructureAnalysisDeclaration = buildAnalysedLevelsScheme(levelsScheme, structureAnalyser),
    componentPrefix = eventName,
    componentRegister = this
  )
  val analysisLogger = AnalysisLogger(sessionAnalyser, structureAnalyser, mlSessionLoggerBuilder)
  return apiPlatform.addTaskListener(
    object : MLTaskGroupListener {
      override val approachListeners: Collection<MLTaskGroupListener.ApproachToListener<*, *>> = listOf(
        approachBuilder.javaClass monitoredBy analysisLogger
      )
    }
  )
}

@ApiStatus.Internal
class AnalysisMethods<M : MLModel<P>, P : Any>(
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
