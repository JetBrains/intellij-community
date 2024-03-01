// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.logger

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.platform.ml.impl.apiPlatform.MLApiPlatform
import com.intellij.platform.ml.impl.apiPlatform.MLApiPlatform.Companion.ensureApproachesInitialized
import com.intellij.platform.ml.impl.apiPlatform.ReplaceableIJPlatform
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface MLEvent {
  val eventName: String

  val declaration: Array<EventField<*>>

  fun onEventGroupInitialized(eventId: VarargEventId)
}

@ApiStatus.Internal
abstract class EventIdRecordingMLEvent : MLEvent {
  private var providedEventId: VarargEventId? = null

  protected fun getEventId(apiPlatform: MLApiPlatform): VarargEventId {
    MLEventsLogger.Manager.ensureInitialized(okIfInitializing = false, apiPlatform)
    return requireNotNull(providedEventId)
  }

  final override fun onEventGroupInitialized(eventId: VarargEventId) {
    providedEventId = eventId
  }
}

/**
 * It logs ML sessions with tiers' descriptions and the analysis.
 * Each session is logged after it is finished, and all analyzers [com.intellij.platform.ml.impl.session.analysis.SessionAnalyser]
 * have yielded the analysis.
 *
 * As the FUS logs' validators are initialized once during the application's start,
 * before giving the [getGroup] for the FUS to use, we must first walk through all declared
 * [com.intellij.platform.ml.impl.MLTaskApproach] in the platform and register them in the FUS scheme
 * (in case they require FUS logging).
 */
@ApiStatus.Internal
class MLEventsLogger : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = Initializer.GROUP.also { Manager.ensureInitialized(okIfInitializing = false) }

  object Manager {
    private val defaultPlatform = ReplaceableIJPlatform

    internal fun ensureInitialized(okIfInitializing: Boolean, apiPlatform: MLApiPlatform = defaultPlatform) {
      when (val state = Initializer.state) {
        is State.FailedToInitialize -> throw Exception("ML Event Log already has failed to initialize", state.exception)
        State.Initializing -> if (okIfInitializing) return else throw IllegalStateException("Initialization recursion")
        State.NonInitialized -> Initializer.initializeGroup(apiPlatform)
        is State.Initialized -> {
          val currentApiPlatformState = apiPlatform.staticState
          require(currentApiPlatformState == state.context.staticState) {
            """
            FUS ML Logger was initialized from ${state.context.apiPlatform} with presumably immutable state ${state.context.staticState},
            but it is used from ${apiPlatform} with state ${currentApiPlatformState}, which differs from the initial state.
            Hence, something that was expected to be logged will not be.
            """.trimIndent()
          }
        }
      }
    }
  }

  internal data class InitializationContext(
    val apiPlatform: MLApiPlatform,
    val staticState: MLApiPlatform.StaticState
  )

  internal sealed interface State {
    data object NonInitialized : State

    data object Initializing : State

    class FailedToInitialize(val exception: Throwable) : State

    class Initialized(val context: InitializationContext) : State
  }

  internal object Initializer {
    val GROUP = EventLogGroup("ml", 3)

    var state: State = State.NonInitialized

    fun initializeGroup(apiPlatform: MLApiPlatform) {
      require(state == State.NonInitialized)
      state = State.Initializing
      try {
        apiPlatform.ensureApproachesInitialized()
        val apiPlatformState = apiPlatform.staticState

        apiPlatform.addDefaultEventsAndListeners()

        apiPlatform.events.forEach { event ->
          val eventId = GROUP.registerVarargEvent(event.eventName, *event.declaration)
          event.onEventGroupInitialized(eventId)
        }
        state = State.Initialized(InitializationContext(apiPlatform, apiPlatformState))
      }
      catch (e: Throwable) {
        state = State.FailedToInitialize(e)
      }
      (state as? State.FailedToInitialize)?.let {
        throw Exception("Failed to initialize FUS ML Logger", it.exception)
      }
    }

    private fun MLApiPlatform.addDefaultEventsAndListeners() {
      val startupLogger = MLApiPlatformStartupLogger()
      addEvent(startupLogger)
      addStartupListener(startupLogger)
    }
  }
}
