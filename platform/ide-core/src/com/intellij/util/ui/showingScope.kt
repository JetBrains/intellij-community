// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
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

private val sequence = AtomicLong() // needed to distinguish updates with the same value

/**
 * A simplified version, which does not compute any additional data when the UI component becomes shown.
 */
@Experimental
fun Component.showingScope(
  debugName: String,
  context: CoroutineContext = EmptyCoroutineContext,
  block: suspend CoroutineScope.() -> Unit,
): Job {
  return showingScope(debugName, context, {}) {
    block()
  }
}

/**
 * Launches [block] in a coroutine, which is bound to the [UI Component][this] [visibility][com.intellij.ui.ComponentUtil.isShowing].
 *
 * [uiData] is used to synchronously compute data **in the same** EDT event where an [HierarchyListener][hierarchy event] was fired.
 * If [uiData] returns a non-null value, [block] with the computed value is run in a new coroutine.
 * Once the component is removed from the hierarchy, the coroutine is canceled.
 * The [block] may be executed several times if the component is repeatedly added/removed from the hierarchy.
 *
 * Cancelling the returned Job brings back the state before calling this function.
 * Exceptions from the [block] also cancel the returned Job, effectively cleaning up the whole thing.
 * For example, to execute some logic on the first time the component is shown, one can throw a CancellationException:
 * ```
 * myLabel.uiScope {
 *   shownForTheFirstTimeEver(myLabel)
 *   throw CancellationException()
 * }
 * ```
 *
 * @param debugName name to use as [CoroutineName]
 * @param context additional context of the coroutine.
 * [CoroutineName], [Job], [ContinuationInterceptor][kotlin.coroutines.ContinuationInterceptor] and [ModalityState] are ignored
 * @param uiData a function, which is called in the same EDT event when the component becomes showing
 */
@Experimental
fun <T : Any> Component.showingScope(
  debugName: String,
  context: CoroutineContext = EmptyCoroutineContext,
  uiData: (component: Component) -> T?,
  block: suspend CoroutineScope.(T) -> Unit,
): Job {
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
  val immediateDispatcher = (Dispatchers.EDT as MainCoroutineDispatcher).immediate + ModalityState.any().asContextElement()

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
