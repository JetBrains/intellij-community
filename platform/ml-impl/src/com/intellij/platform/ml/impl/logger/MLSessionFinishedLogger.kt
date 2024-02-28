// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.logger

import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.platform.ml.Environment
import com.intellij.platform.ml.Session
import com.intellij.platform.ml.impl.MLTaskApproach
import com.intellij.platform.ml.impl.apiPlatform.MLApiPlatform
import com.intellij.platform.ml.impl.apiPlatform.MLApiStartupListenerProvider
import com.intellij.platform.ml.impl.monitoring.*
import com.intellij.platform.ml.impl.monitoring.MLTaskGroupListener.ApproachListeners.Companion.monitoredBy
import com.intellij.platform.ml.impl.session.AnalysedRootContainer
import com.intellij.platform.ml.impl.session.DescribedRootContainer
import org.jetbrains.annotations.ApiStatus

/**
 * Logs to FUS information about a finished ML session, tier instances' descriptions, analysis.
 *
 * A way to start logging, is to create a [FinishedSessionLoggerRegister] and add it with [com.intellij.platform.ml.impl.apiPlatform.ReplaceableIJPlatform.addStartupListener].
 * If you are using a regression model, see [InplaceFeaturesScheme.FusScheme.Companion.DOUBLE].
 *
 * @param approach The approach whose sessions are monitored
 * @param configuration The particular scheme that is used to serialize sessions
 */
@ApiStatus.Internal
class MLSessionFinishedLogger<R, P : Any>(
  approach: MLTaskApproach<P>,
  configuration: FusSessionEventBuilder.FusScheme<P>,
  private val apiPlatform: MLApiPlatform,
) : EventIdRecordingMLEvent(), MLTaskGroupListener {
  private val loggingScheme: FusSessionEventBuilder<P> = configuration.createEventBuilder(approach.approachDeclaration)
  private val fusDeclaration: SessionFields<P> = loggingScheme.buildFusDeclaration()
  override val declaration: Array<EventField<*>> = fusDeclaration.getFields()

  override val eventName: String = "${approach.task.name}.finished"

  override val approachListeners: Collection<MLTaskGroupListener.ApproachListeners<*, *>> = listOf(
    approach.javaClass monitoredBy InitializationLogger()
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
      val eventId = getEventId(apiPlatform)
      val fusEventData = loggingScheme.buildRecord(sessionTree, fusDeclaration)
      eventId.log(*fusEventData)
    }
  }
}

/**
 * Registers successful ML sessions' logging as a separate FUS event.
 *
 * See [MLSessionFinishedLogger]
 */
@ApiStatus.Internal
open class FinishedSessionLoggerRegister<M, P : Any>(
  private val targetApproachClass: Class<out MLTaskApproach<P>>,
  private val fusScheme: FusSessionEventBuilder.FusScheme<P>,
) : MLApiStartupListener, MLApiStartupListenerProvider.ThisProvider {
  override fun onBeforeStarted(apiPlatform: MLApiPlatform): MLApiStartupProcessListener {
    return object : MLApiStartupProcessListener {
      override fun onStartedInitializingFus(initializedApproaches: Collection<InitializerAndApproach<*>>) {
        @Suppress("UNCHECKED_CAST")
        val targetInitializedApproach: MLTaskApproach<P> = requireNotNull(initializedApproaches.find { it.approach.javaClass == targetApproachClass }) {
          """
            Could not create logger for finished sessions of $targetApproachClass in platform $apiPlatform, because the corresponding approach was not initialized.
            Initialized approaches: $initializedApproaches
          """.trimIndent()
        }.approach as MLTaskApproach<P>
        val finishedEventLogger = MLSessionFinishedLogger<M, P>(targetInitializedApproach, fusScheme, apiPlatform)
        apiPlatform.addMLEventBeforeFusInitialized(finishedEventLogger)
        apiPlatform.addTaskListener(finishedEventLogger)
      }
    }
  }
}
