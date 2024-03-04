// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.logs.events

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.platform.ml.Environment
import com.intellij.platform.ml.Session
import com.intellij.platform.ml.impl.MLTask
import com.intellij.platform.ml.impl.MLTaskApproach.Companion.findMlTaskApproach
import com.intellij.platform.ml.impl.MLTaskApproachBuilder
import com.intellij.platform.ml.impl.apiPlatform.MLApiPlatform
import com.intellij.platform.ml.impl.apiPlatform.ReplaceableIJPlatform
import com.intellij.platform.ml.impl.logs.EventSessionEventBuilder
import com.intellij.platform.ml.impl.logs.SessionFields
import com.intellij.platform.ml.impl.monitoring.MLApproachInitializationListener
import com.intellij.platform.ml.impl.monitoring.MLApproachListener
import com.intellij.platform.ml.impl.monitoring.MLSessionListener
import com.intellij.platform.ml.impl.monitoring.MLTaskGroupListener
import com.intellij.platform.ml.impl.monitoring.MLTaskGroupListener.ApproachListeners.Companion.monitoredBy
import com.intellij.platform.ml.impl.session.AnalysedRootContainer
import com.intellij.platform.ml.impl.session.DescribedRootContainer
import org.jetbrains.annotations.ApiStatus

/**
 * For the given [this] event group, registers an event, that will be corresponding to a finish of [task]'s session.
 * The event will be logged automatically.
 *
 * @param eventName Event's name in the event group
 * @param task Task whose finish will be recorded
 * @param scheme Chosen scheme for the session structure
 * @param apiPlatform Platform that will be used to build event validators: it should include desired analysis and description features.
 *
 * @return Controller, that could disable event's logging by calling [MLApiPlatform.ExtensionController.removeExtension]
 */
@ApiStatus.Internal
fun <R, P : Any> EventLogGroup.registerEventSessionFinished(
  eventName: String,
  task: MLTask<P>,
  scheme: EventSessionEventBuilder.EventScheme<P>,
  apiPlatform: MLApiPlatform = ReplaceableIJPlatform
): MLApiPlatform.ExtensionController {
  val approachBuilder = findMlTaskApproach(task, apiPlatform)
  val sessionDeclaration = approachBuilder.buildApproachSessionDeclaration(apiPlatform)
  val finishedSessionEventBuilder: EventSessionEventBuilder<P> = scheme.createEventBuilder(sessionDeclaration)
  val sessionFields: SessionFields<P> = finishedSessionEventBuilder.buildSessionFields()
  val eventId = registerVarargEvent(eventName, *sessionFields.getFields())
  val logger = MLSessionFinishedLogger<R, P>(approachBuilder, eventId, finishedSessionEventBuilder, sessionFields)
  return apiPlatform.addTaskListener(logger)
}

private class MLSessionFinishedLogger<R, P : Any>(
  approachBuilder: MLTaskApproachBuilder<P>,
  private val eventId: VarargEventId,
  private val finishedSessionEventBuilder: EventSessionEventBuilder<P>,
  private val sessionFields: SessionFields<P>
) : MLTaskGroupListener {
  override val approachListeners: Collection<MLTaskGroupListener.ApproachListeners<*, *>> = listOf(
    approachBuilder.javaClass monitoredBy InitializationLogger()
  )

  inner class InitializationLogger : MLApproachInitializationListener<R, P> {
    override fun onAttemptedToStartSession(permanentSessionEnvironment: Environment): MLApproachListener<R, P> = ApproachLogger()
  }

  inner class ApproachLogger : MLApproachListener<R, P> {
    override fun onFailedToStartSessionWithException(exception: Throwable) {}

    override fun onFailedToStartSession(failure: Session.StartOutcome.Failure<P>) {}

    override fun onStartedSession(session: Session<P>): MLSessionListener<R, P> = SessionLogger()
  }

  inner class SessionLogger : MLSessionListener<R, P> {
    override fun onSessionDescriptionFinished(sessionTree: DescribedRootContainer<R, P>) {}

    override fun onSessionAnalysisFinished(sessionTree: AnalysedRootContainer<P>) {
      val fusEventData = finishedSessionEventBuilder.buildRecord(sessionTree, sessionFields)
      eventId.log(*fusEventData)
    }
  }
}
