// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.logger

import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.ObjectEventData
import com.intellij.internal.statistic.eventLog.events.ObjectEventField
import com.intellij.platform.ml.Session
import com.intellij.platform.ml.impl.MLTaskApproach
import com.intellij.platform.ml.impl.apiPlatform.MLApiPlatform
import com.intellij.platform.ml.impl.monitoring.*
import com.intellij.platform.ml.impl.monitoring.MLTaskGroupListener.ApproachListeners.Companion.monitoredBy
import com.intellij.platform.ml.impl.session.analysis.ShallowSessionAnalyser
import com.intellij.platform.ml.impl.session.analysis.ShallowSessionAnalyser.Companion.declarationObjectDescription
import org.jetbrains.annotations.ApiStatus

/**
 * Logs to FUS information about an ML session, that has failed to start.
 *
 * A way to start logging, is to create a [FailedSessionLoggerRegister] and add it via [com.intellij.platform.ml.impl.apiPlatform.ReplaceableIJPlatform.addStartupListener]
 *
 * @param taskApproach The approach that is monitored
 * @param exceptionalAnalysers Analyzers, that are triggered, when [MLTaskApproach.startSession] fails with an unhandled exception
 * @param normalFailureAnalysers Analyzers, that aer triggered, when [MLTaskApproach.startSession] returns a [Session.StartOutcome.Failure]
 */
@ApiStatus.Internal
class MLSessionFailedLogger<M, P : Any>(
  private val taskApproach: MLTaskApproach<P>,
  private val exceptionalAnalysers: Collection<ShallowSessionAnalyser<Throwable>>,
  private val normalFailureAnalysers: Collection<ShallowSessionAnalyser<Session.StartOutcome.Failure<P>>>,
  private val apiPlatform: MLApiPlatform,
) : EventIdRecordingMLEvent(), MLTaskGroupListener {
  override val eventName: String = "${taskApproach.task.name}.failed"

  private val fields: Map<String, ObjectEventField> = (
    exceptionalAnalysers.map { ObjectEventField(it.name, it.declarationObjectDescription) } +
    normalFailureAnalysers.map { ObjectEventField(it.name, it.declarationObjectDescription) })
    .associateBy { it.name }

  override val declaration: Array<EventField<*>> = fields.values.toTypedArray()

  override val approachListeners: Collection<MLTaskGroupListener.ApproachListeners<*, *>>
    get() {
      val eventId = getEventId(apiPlatform)
      return listOf(
        taskApproach.javaClass monitoredBy MLApproachInitializationListener { permanentSessionEnvironment ->
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

/**
 * Registers failed sessions' logging as a separate FUS event.
 *
 * See [MLSessionFailedLogger]
 */
@ApiStatus.Internal
open class FailedSessionLoggerRegister<M, P : Any>(
  private val targetApproachClass: Class<out MLTaskApproach<P>>,
  private val exceptionalAnalysers: Collection<ShallowSessionAnalyser<Throwable>>,
  private val normalFailureAnalysers: Collection<ShallowSessionAnalyser<Session.StartOutcome.Failure<P>>>,
) : MLApiStartupListener {
  override fun onBeforeStarted(apiPlatform: MLApiPlatform): MLApiStartupProcessListener {
    return object : MLApiStartupProcessListener {
      override fun onStartedInitializingFus(initializedApproaches: Collection<InitializerAndApproach<*>>) {
        @Suppress("UNCHECKED_CAST")
        val targetInitializedApproach: MLTaskApproach<P> = requireNotNull(
          initializedApproaches.find { it.approach.javaClass == targetApproachClass }) {
          """
            Could not create logger for failed sessions for $targetApproachClass in platform $apiPlatform, because the corresponding approach was not initialized.
            Initialized approaches: $initializedApproaches
          """.trimIndent()
        }.approach as MLTaskApproach<P>
        val finishedEventLogger = MLSessionFailedLogger<M, P>(targetInitializedApproach, exceptionalAnalysers, normalFailureAnalysers, apiPlatform)
        apiPlatform.addMLEventBeforeFusInitialized(finishedEventLogger)
        apiPlatform.addTaskListener(finishedEventLogger)
      }
    }
  }
}
