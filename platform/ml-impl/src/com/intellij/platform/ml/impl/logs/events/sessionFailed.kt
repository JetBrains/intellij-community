// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.logs.events

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.ObjectEventData
import com.intellij.internal.statistic.eventLog.events.ObjectEventField
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.platform.ml.Session
import com.intellij.platform.ml.impl.MLTask
import com.intellij.platform.ml.impl.MLTaskApproach
import com.intellij.platform.ml.impl.MLTaskApproach.Companion.findMlTaskApproach
import com.intellij.platform.ml.impl.MLTaskApproachBuilder
import com.intellij.platform.ml.impl.apiPlatform.MLApiPlatform
import com.intellij.platform.ml.impl.apiPlatform.ReplaceableIJPlatform
import com.intellij.platform.ml.impl.monitoring.MLApproachInitializationListener
import com.intellij.platform.ml.impl.monitoring.MLApproachListener
import com.intellij.platform.ml.impl.monitoring.MLTaskGroupListener
import com.intellij.platform.ml.impl.monitoring.MLTaskGroupListener.ApproachListeners.Companion.monitoredBy
import com.intellij.platform.ml.impl.session.analysis.ShallowSessionAnalyser
import com.intellij.platform.ml.impl.session.analysis.ShallowSessionAnalyser.Companion.declarationObjectDescription
import org.jetbrains.annotations.ApiStatus

/**
 * For the given [this] event group, registers an event, that will be corresponding to a failure of [task]'s start attempt.
 * The event will be logged automatically.
 *
 * @param eventName Event's name in the event group
 * @param task Task whose finish will be recorded
 * @param exceptionalAnalysers Analyzers, that are triggered, when [MLTaskApproach.startSession] fails with an unhandled exception
 * @param normalFailureAnalysers Analyzers, that aer triggered, when [MLTaskApproach.startSession] returns a [Session.StartOutcome.Failure]
 * @param apiPlatform Platform that will be used to build event validators: it should include desired analysis and description features.
 *
 * @return Controller, that could disable event's logging by calling [MLApiPlatform.ExtensionController.removeExtension]
 */
@ApiStatus.Internal
fun <R, P : Any> EventLogGroup.registerEventSessionFailed(
  eventName: String,
  task: MLTask<P>,
  exceptionalAnalysers: Collection<ShallowSessionAnalyser<Throwable>>,
  normalFailureAnalysers: Collection<ShallowSessionAnalyser<Session.StartOutcome.Failure<P>>>,
  apiPlatform: MLApiPlatform = ReplaceableIJPlatform
): MLApiPlatform.ExtensionController {
  val approachBuilder = findMlTaskApproach(task, apiPlatform)
  val fields: Map<String, ObjectEventField> = (normalFailureAnalysers + exceptionalAnalysers)
    .map { ObjectEventField(it.name, it.declarationObjectDescription) }
    .associateBy { it.name }
  val eventId = registerVarargEvent(eventName, *fields.values.toTypedArray())
  val logger = MLSessionFailedLogger<R, P>(approachBuilder, exceptionalAnalysers, normalFailureAnalysers, eventId, fields)
  return apiPlatform.addTaskListener(logger)
}

private class MLSessionFailedLogger<M, P : Any>(
  private val taskApproachBuilder: MLTaskApproachBuilder<P>,
  private val exceptionalAnalysers: Collection<ShallowSessionAnalyser<Throwable>>,
  private val normalFailureAnalysers: Collection<ShallowSessionAnalyser<Session.StartOutcome.Failure<P>>>,
  private val eventId: VarargEventId,
  private val fields: Map<String, ObjectEventField>,
) : MLTaskGroupListener {

  override val approachListeners: Collection<MLTaskGroupListener.ApproachListeners<*, *>>
    get() {
      return listOf(
        taskApproachBuilder.javaClass monitoredBy MLApproachInitializationListener { permanentSessionEnvironment ->
          object : MLApproachListener<M, P> {
            override fun onFailedToStartSessionWithException(exception: Throwable) {
              val analysis = exceptionalAnalysers.map {
                val analyserField = fields.getValue(it.name)
                analyserField with ObjectEventData(it.analyse(permanentSessionEnvironment, exception))
              }
              eventId.log(*analysis.toTypedArray())
            }

            override fun onFailedToStartSession(failure: Session.StartOutcome.Failure<P>) {
              val analysis = normalFailureAnalysers.map {
                val analyserField = fields.getValue(it.name)
                analyserField with ObjectEventData(it.analyse(permanentSessionEnvironment, failure))
              }
              eventId.log(*analysis.toTypedArray())
            }

            override fun onStartedSession(session: Session<P>) = null
          }
        }
      )
    }
}
