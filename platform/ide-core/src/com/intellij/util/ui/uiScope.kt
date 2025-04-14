// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.ui.ComponentUtil
import com.intellij.util.BitUtil
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.selects.select
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Component
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Launches [block] in a coroutine **once** the [UI component][this] becomes [showing][ComponentUtil.isShowing]
 * and cancels the coroutine when the UI component is hidden.
 * In particular, the component becomes hidden when it's removed from the hierarchy.
 *
 * The [block] is executed with the modality state of the [component][this].
 * This means that the [block] execution might happen in a different EDT event,
 * because it has to wait for the proper modality.
 *
 * The [block] may be executed at most **one time**.
 * It will not be restarted if canceled by the component becoming hidden.
 *
 * @param debugName name to use as [CoroutineName]
 * @param context additional context of the coroutine.
 * [CoroutineName], [Job], [ContinuationInterceptor] and [ModalityState] are ignored
 * @see launchOnShow
 */
@Experimental
fun <C : Component> C.launchOnceOnShow(
  debugName: String,
  context: CoroutineContext = EmptyCoroutineContext,
  block: suspend CoroutineScope.() -> Unit,
): Job {
  ThreadingAssertions.assertEventDispatchThread()
  val component = this

  @OptIn(DelicateCoroutinesApi::class)
  return GlobalScope.launch(Dispatchers.Unconfined + CoroutineName(debugName)) {
    showingAsChannel(component) { channel ->
      while (!channel.receive()) Unit // await showing
      val uiCoroutine = launchUiCoroutine(component, context, block)
      val waitingForHidden = launch {
        while (channel.receive()) Unit // await hidden
      }
      select {
        uiCoroutine.onJoin {
          waitingForHidden.cancel()
        }
        waitingForHidden.onJoin {
          uiCoroutine.cancel()
        }
      }
    }
  }
}

/**
 * Launches [block] in a coroutine **each time** the [UI component][this] becomes [showing][ComponentUtil.isShowing]
 * and cancels the coroutine when the UI component is hidden.
 * In particular, the component becomes hidden when it's removed from the hierarchy.
 *
 * The [block] is executed with the modality state of the [component][this].
 * This means that the [block] execution might happen in a different EDT event,
 * because it has to wait for the proper modality.
 *
 * The [block] may be executed several times, and the next execution of [block] will start after the previous [block] completes.
 * This also means that the next [block] execution might happen in a different EDT event,
 * because it has to [wait for the completion][Job.join] of a previously scheduled [block].
 *
 * Exceptions from the [block] don't cancel the returned Job.
 * If [block] throws an exception, it will be re-launched the next time the component becomes showing.
 *
 * @param debugName name to use as [CoroutineName]
 * @param context additional context of the coroutine.
 * [CoroutineName], [Job], [ContinuationInterceptor] and [ModalityState] are ignored
 * @see launchOnceOnShow
 */
@Experimental
fun <C : Component> C.launchOnShow(
  debugName: String,
  context: CoroutineContext = EmptyCoroutineContext,
  block: suspend CoroutineScope.() -> Unit,
): Job {
  // Removal from the hierarchy triggers `false` through the channel, which cancels the `uiCoroutine`
  // but keeps the owner coroutine (the one that reads from the channel).
  //
  // Once the component is removed from the hierarchy, the channel stops emitting
  // => its only receiver is never resumed and thus never scheduled
  // => it's never referenced in the dispatcher queues
  // => channel, the channel receiver, and the hierarchy listener are GC-ed together with the component.
  //
  // Alternatively, one can clean up the whole thing by cancelling the returned Job.
  //
  // Keeping the reference to the returned Job will keep the reference to the component,
  // that's why GlobalScope is used: to avoid keeping the reference to the Job in the children of some parent Job.

  ThreadingAssertions.assertEventDispatchThread()
  val component = this

  @OptIn(DelicateCoroutinesApi::class)
  return GlobalScope.launch(Dispatchers.Unconfined + CoroutineName(debugName)) {
    showingAsChannel(component) { channel ->
      supervisorScope {
        // Poor man's [distinctUntilChanged] + [collectLatest]
        var uiCoroutine: Job? = null
        for (showing in channel) {
          ThreadingAssertions.assertEventDispatchThread()
          if (showing) {
            if (uiCoroutine == null) {
              uiCoroutine = launchUiCoroutine(component, context, block)
            }
          }
          else {
            if (uiCoroutine != null) {
              uiCoroutine.cancelAndJoin()
              uiCoroutine = null
            }
          }
        }
      }
    }
  }
}

private suspend fun showingAsChannel(component: Component, block: suspend (ReceiveChannel<Boolean>) -> Unit) {
  val channel = Channel<Boolean>(capacity = Int.MAX_VALUE) // don't skip events
  try {
    channel.trySend(isShowing(component))
    val listener = HierarchyListener { evt ->
      if (BitUtil.isSet(evt.changeFlags, HierarchyEvent.SHOWING_CHANGED.toLong()) || showingChanged) {
        channel.trySend(isShowing(component))
      }
    }
    component.installHierarchyListener(listener).use {
      block(channel)
    }
  }
  finally {
    channel.close()
  }
}

// For some reason, HierarchyEvent.SHOWING_CHANGED check does not pass in unit tests.
private var showingChanged: Boolean = false

@Internal
@VisibleForTesting
fun withShowingChanged(block: () -> Unit) {
  showingChanged = true
  try {
    block()
  }
  finally {
    showingChanged = false
  }
}

private fun isShowing(component: Component): Boolean {
  return ComponentUtil.isShowing(component, false)
}

private fun CoroutineScope.launchUiCoroutine(
  component: Component,
  additionalContext: CoroutineContext,
  block: suspend CoroutineScope.() -> Unit,
): Job {
  val componentModality = ModalityState.stateForComponent(component)
  val effectiveContext = additionalContext
    .minusKey(CoroutineName)
    .minusKey(Job)
    .plus(Dispatchers.UI)
    .plus(componentModality.asContextElement())
  return launch(effectiveContext, block = block)
}

private fun Component.installHierarchyListener(listener: HierarchyListener): AccessToken {
  ThreadingAssertions.assertEventDispatchThread()
  addHierarchyListener(listener)
  return object : AccessToken() {
    override fun finish() {
      removeHierarchyListener(listener)
      ThreadingAssertions.assertEventDispatchThread()
    }
  }
}
