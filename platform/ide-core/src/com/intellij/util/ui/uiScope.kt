// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import com.intellij.openapi.application.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.kernel.withKernel
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
 * Once it starts executing, it will not be restarted if canceled by the component becoming hidden,
 * regardless of whether the block completed normally or at all.
 * But if the component is hidden before the block starts executing, it will be restarted
 * the next time the component is shown again.
 * This behavior covers a common case when a component is added to a showing parent,
 * then immediately removed and added again.
 * It often happens when several components are added to a single parent,
 * and that parent is designed to remove everything and rebuild the entire layout on every child addition.
 * All those removals and additions typically happen in a single EDT event, so the block never even gets a chance to start.
 * For such use cases, this function can be thought of as "launch once when it finally shows."
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

  return launchUnconfined(debugName) {
    var started = false
    showingAsChannel(component) { channel ->
      while (!started) {
        while (!channel.receive()) Unit // await showing
        val uiCoroutine = launchUiCoroutine(component, context) {
          started = true
          block()
        }
        val waitingForHidden = launch {
          while (channel.receive()) Unit // await hidden
        }
        select {
          uiCoroutine.onJoin {
            waitingForHidden.cancel()
          }
          waitingForHidden.onJoin {
            // need to cancel AND join, so the while(!started) check works correctly
            uiCoroutine.cancelAndJoin()
          }
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

  return launchUnconfined(debugName) {
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

/**
 * Launches the given task in the global scope without dispatching.
 */
private fun launchUnconfined(debugName: String, block: suspend CoroutineScope.() -> Unit): Job {
  if (Registry.`is`("ide.ui.coroutine.scopes.unconfined.fix", false)) {
    // The whole point here is to launch the coroutine in-place without dispatching,
    // and to make sure it doesn't dispatch later when called on the EDT from the hierarchy listener.
    // This is why Dispatchers.ui(UiDispatcherKind.RELAX, immediate = true) is used with ModalityState.any().
    // The modality is obvious: using any() ensures that the current modality is ignored when processing hierarchy events.
    // The task itself will then check the modality before executing, but events should be processed immediately.
    // The dispatcher is a bit more tricky: using EDT will dispatch if the WIL is forbidden (when already running under UI),
    // and using UI will dispatch if the WIL is currently locked.
    // But RELAX doesn't care about the WIL and whether it's allowed, so it will not dispatch if immediate = true.
    // Note that using Dispatchers.Unconfined here seems the obvious choice,
    // but it doesn't work when already running an unconfined coroutine (IJPL-196231, CPP-45385).
    @OptIn(DelicateCoroutinesApi::class)
    return GlobalScope.launch(
      context = Dispatchers.ui(UiDispatcherKind.RELAX, immediate = true) + ModalityState.any().asContextElement() + CoroutineName(debugName),
      block = block,
    )
  }
  else {
    @OptIn(DelicateCoroutinesApi::class)
    return GlobalScope.launch(
      context = Dispatchers.Unconfined + CoroutineName(debugName),
      block = block,
    )
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
  return launch(effectiveContext) {
    // withKernel should be kept here, because we need to propagate the context to coroutine launched via GlobalScope
    @Suppress("DEPRECATION")
    withKernel {
      block()
    }
  }
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
