// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.ide

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.impl.RawSwingDispatcher
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.blockingContext
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.debounce
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Service(Service.Level.APP)
class IdleTracker(private val coroutineScope: CoroutineScope) {
  private val listenerToRequest = Collections.synchronizedMap(LinkedHashMap<Runnable, CoroutineScope>())

  // must be `replay = 1`, because on a first subscription,
  // the subscriber should start countdown (`debounce()` or `delay()` as part of `collect`)
  private val _events = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_LATEST)

  companion object {
    @JvmStatic
    fun getInstance(): IdleTracker = service<IdleTracker>()
  }

  val events: SharedFlow<Unit> = _events.asSharedFlow()

  init {
    coroutineScope.launch(RawSwingDispatcher) {
      IdeEventQueue.getInstance().setIdleTracker { check(_events.tryEmit(Unit)) }
    }
  }

  /**
   * Only for existing Java clients.
   * Returns handle to remove the listener.
   */
  @Internal
  @OptIn(FlowPreview::class)
  fun addIdleListener(delayInMs: Int, listener: Runnable): AccessToken {
    val delay = delayInMs.milliseconds
    checkDelay(delay, listener)

    val listenerScope = coroutineScope.childScope()
    listenerScope.launch(CoroutineName("Idle listener: ${listener.javaClass.name}")) {
      events
        .debounce(delay)
        .collect {
          withContext(Dispatchers.EDT) {
            blockingContext {
              listener.run()
            }
          }
        }
    }
    return object : AccessToken() {
      override fun finish() {
        listenerScope.cancel()
      }
    }
  }

  private fun checkDelay(delay: Duration, listener: Any) {
    if (delay == Duration.ZERO || delay.inWholeHours >= 24) {
      logger<IdleTracker>().error(PluginException.createByClass(IllegalArgumentException("This delay value is unsupported: $delay"),
                                                                listener::class.java))
    }
  }

  /**
   * Use coroutines and [events].
   */
  @OptIn(FlowPreview::class)
  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use coroutines and [events]. " +
              "Or at least method that returns close handler: `addIdleListener(delayInMs, listener): AccessToken`")
  fun addIdleListener(runnable: Runnable, timeoutMillis: Int) {
    val delay = timeoutMillis.toDuration(DurationUnit.MILLISECONDS)
    checkDelay(delay, runnable)

    synchronized(listenerToRequest) {
      val listenerScope = coroutineScope.childScope()
      listenerToRequest.put(runnable, listenerScope)
      listenerScope.launch(CoroutineName("Idle listener: ${runnable.javaClass.name}")) {
        events
          .debounce(delay)
          .collect {
            withContext(Dispatchers.EDT) {
              runnable.run()
            }
          }
      }
    }
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use coroutines and [events]. " +
              "Or at least method that returns close handler: `addIdleListener(delayInMs, listener): AccessToken`")
  fun removeIdleListener(runnable: Runnable) {
    synchronized(listenerToRequest) {
      val coroutineScope = listenerToRequest.remove(runnable)
      if (coroutineScope == null) {
        logger<IdleTracker>().error("unknown runnable: $runnable")
      }
      else {
        coroutineScope.cancel()
      }
    }
  }

  /**
   * Notify the event queue that IDE shouldn't be considered idle at this moment.
   */
  @Internal
  fun restartIdleTimer() {
    check(_events.tryEmit(Unit))
  }
}