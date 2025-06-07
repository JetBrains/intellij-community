// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UiDispatcherKind
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.ui
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ComponentUtil
import com.intellij.util.BitUtil
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.annotations.ApiStatus.Experimental
import java.awt.Component
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException

private val sequence = AtomicLong() // needed to distinguish updates with the same value

/**
 * A simplified version, which does not compute any additional data when the UI component becomes shown.
 */
@Experimental
@Deprecated("Use launchOnShow or launchOnceOnShow")
fun Component.showingScope(
  debugName: String,
  context: CoroutineContext = EmptyCoroutineContext,
  block: suspend CoroutineScope.() -> Unit,
): Job {
  @Suppress("DEPRECATION")
  return showingScope(debugName, context, {}) {
    block()
  }
}

/**
 * This was the first design. It's now deprecated in favor of [launchOnShow]/[launchOnceOnShow].
 *
 * 1. Exceptions from the [block] also cancel the returned Job.
 * For example, to execute some logic on the first time the component is shown, one can throw a CancellationException:
 * ```
 * myLabel.showingScope(myDebugName) {
 *   shownForTheFirstTimeEver(myLabel)
 *   throw CancellationException()
 * ```
 * Instead, [launchOnceOnShow] should be used.
 * In case of [launchOnShow], the [block] will be restarted even if the previous invocation threw an exception.
 *
 * 2. Synchronous [uiData] is not supported, the logic can be moved to the first line of [block].
 *
 * @param debugName name to use as [CoroutineName]
 * @param context additional context of the coroutine.
 * [CoroutineName], [Job], [ContinuationInterceptor][kotlin.coroutines.ContinuationInterceptor] and [ModalityState] are ignored
 * @param uiData a function, which is called in the same EDT event when the component becomes showing
 */
@Experimental
@Deprecated("Use launchOnShow or launchOnceOnShow")
fun <T : Any, C : Component> C.showingScope(
  debugName: String,
  context: CoroutineContext = EmptyCoroutineContext,
  uiData: (component: C) -> T?,
  block: suspend CoroutineScope.(T) -> Unit,
): Job {
  if (Registry.`is`("ide.showing.scope.compatibility.mode")) {
    return compatibilityShowingScope(debugName, context, uiData, block)
  }
  // Removal from the hierarchy triggers `state.value = Pair(0, null)`, which cancels the `uiCoroutine`
  // but keeps the owner coroutine (the one that runs `collect`).
  //
  // Once the component is removed from the hierarchy, the `state` flow stops emitting
  // => its only subscription is never resumed and thus never scheduled
  // => it's never referenced in the dispatcher queues
  // => `state`, the subscription (`collect`), and the hierarchy listener are GC-ed together with the component.
  //
  // Alternatively, one can clean up the whole thing by cancelling the returned Job.
  //
  // Keeping the reference to the returned Job will keep the reference to the component,
  // that's why GlobalScope is used: to avoid keeping the reference to the Job in the children of some parent Job.

  ThreadingAssertions.assertEventDispatchThread()
  val component = this

  fun computeUiData() = if (ComponentUtil.isShowing(component, false)) {
    Pair(sequence.getAndIncrement(), uiData(component))
  }
  else {
    Pair(0, null)
  }

  val state = MutableStateFlow(computeUiData())
  val listener = HierarchyListener { evt ->
    if (BitUtil.isSet(evt.changeFlags, HierarchyEvent.SHOWING_CHANGED.toLong())) {
      state.value = computeUiData()
    }
  }

  // To avoid paying for dispatch and to clean up resources (e.g., remove the listener) faster:
  val immediateDispatcher = Dispatchers.ui(kind = UiDispatcherKind.RELAX, immediate = true) + ModalityState.any().asContextElement()

  @OptIn(DelicateCoroutinesApi::class)
  return GlobalScope.launch(immediateDispatcher + CoroutineName(debugName), start = CoroutineStart.UNDISPATCHED) {
    val owner = this

    component.installHierarchyListener(listener).use {
      var uiCoroutine: Job? = null
      state.collect { (_, value) ->
        // Poor man's collectLatest is needed because original collectLatest
        // does not support changing the context (`ModalityState.stateForComponent(..)`).
        //
        // `join` can resume in a different EDT event.
        // We can either guarantee that the `block` is started in the same EDT event where the component becomes showing,
        // or we can guarantee that a new `block` coroutine is not run before the previous one is complete.
        // Since we already provide a way to compute UI data via `uiData` synchronously,
        // here we choose to guarantee that no two `block` coroutines are active concurrently.
        //
        // When the component is constructed, it's not yet in the hierarchy,
        // => `value` is `null`
        // => `collect` lambda returns without starting the `block.
        // On the first execution, the value becomes non-null, but `uiCoroutine` is still `null`
        // => `uiCoroutine` is launched in the same EDT event.
        // TODO consider launching with `start = UNDISPATCHED` if the current modality matches the component modality
        uiCoroutine?.cancelAndJoin()
        if (value == null) {
          uiCoroutine = null
          return@collect
        }
        // The modality of a component not in the hierarchy is effectively non-modal,
        // so we have to compute modality after the component is added to the hierarchy.
        // Also, the component modality may change when a component is moved between frames.
        val modalityState = ModalityState.stateForComponent(component)
        uiCoroutine = launch(
          context = context.minusKey(CoroutineName).minusKey(Job) + Dispatchers.EDT + modalityState.asContextElement(),
        ) {
          try {
            block(value)
          }
          catch (ce: CancellationException) {
            // First, check if `uiCoroutine` was canceled, e.g., by `cancelAndJoin` above:
            currentCoroutineContext().ensureActive()
            // If `uiCoroutine` is active, the exception must've been thrown manually
            // => cancel the owner to clean up.
            owner.cancel(ce)
            throw ce
          }
        }
      }
    }
  }
}

private fun <C : Component, T : Any> C.compatibilityShowingScope(
  debugName: String,
  context: CoroutineContext,
  uiData: (C) -> T?,
  block: suspend CoroutineScope.(T) -> Unit,
): Job {
  return launchOnShow(debugName, context) {
    // semantic change: uiData is computed when the UI coroutine starts instead of when the component becomes visible
    val data = uiData(this@compatibilityShowingScope)
               ?: return@launchOnShow // null was ignored in showingScope
    try {
      block(data)
    }
    catch (t: Throwable) {
      val ce = if (t is CancellationException) {
        // First, check if `uiCoroutine` was canceled
        currentCoroutineContext().ensureActive()
        // If `uiCoroutine` is active, the exception must've been thrown manually
        // => continue to cancellation of the owner.
        t
      }
      else {
        CancellationException(t)
      }
      @OptIn(ExperimentalCoroutinesApi::class)
      checkNotNull(coroutineContext.job.parent) // `supervisorScope {}` inside `launchOnShow`
        .cancel(ce)
      throw t
    }
  }
}

private fun Component.installHierarchyListener(listener: HierarchyListener): AccessToken {
  ThreadingAssertions.assertEventDispatchThread()
  addHierarchyListener(listener)
  return object : AccessToken() {
    override fun finish() {
      ThreadingAssertions.assertEventDispatchThread()
      removeHierarchyListener(listener)
    }
  }
}
