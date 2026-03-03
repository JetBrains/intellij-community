// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental
@file:OptIn(FlowPreview::class)

package com.intellij.util.ui.update

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.util.coroutines.flow.debounceBatch
import com.intellij.util.cancelOnDispose
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.util.function.Consumer
import javax.swing.JComponent
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Builder-based API for creating debounced/throttled update queues.
 *
 * Supports two lifetime modes:
 * - **forScope**: Tied to a [CoroutineScope] lifecycle
 * - **forComponent**: Tied to a [JComponent] visibility lifecycle (uses [launchOnShow])
 *
 * Supports two execution modes:
 * - **runLatest**: Processes only the latest item (uses DROP_OLDEST and collectLatest - cancels previous execution if still running)
 * - **runBatched**: Processes all accumulated items together (uses debounceBatch)
 *
 * Example usage in Kotlin:
 * ```kotlin
 * // Latest item, scope-bound
 * val queue1 = DebouncedUpdates.forScope<State>(scope, "update-ui", 300.milliseconds)
 *   .withContext(Dispatchers.EDT)
 *   .restartTimerOnAdd(true)
 *   .runLatest { state -> updateUI(state) }
 *
 * // Batched, component-bound
 * val queue2 = DebouncedUpdates.forComponent<Event>(component, "process-events", 100.milliseconds)
 *   .runBatched { events -> processEvents(events) }
 *
 * queue1.queue(newState)
 * queue2.queue(event)
 * ```
 *
 * Example usage in Java:
 * ```java
 * // Latest item, scope-bound
 * var queue1 = DebouncedUpdates.forScope(scope, "update-ui", 300)
 *   .withContext(Dispatchers.getEDT())
 *   .restartTimerOnAdd(true)
 *   .runLatestConsumer(state -> updateUI(state));
 *
 * // Batched, component-bound
 * var queue2 = DebouncedUpdates.forComponent(component, "process-events", 100)
 *   .runBatchedConsumer(events -> processEvents(events));
 *
 * queue1.queue(newState);
 * queue2.queue(event);
 * ```
 */
@ApiStatus.Experimental
object DebouncedUpdates {

  /**
   * Creates a builder for a debounced update queue tied to a [CoroutineScope].
   *
   * @param scope The coroutine scope for lifetime management
   * @param name Debug name for the coroutine
   * @param delay The delay as a [Duration]
   * @return A builder to configure and create the queue
   */
  fun <T> forScope(scope: CoroutineScope, name: String, delay: Duration): ScopeBuilder<T> {
    return ScopeBuilder(scope, name, delay)
  }

  /**
   * Creates a builder for a debounced update queue tied to a [CoroutineScope].
   * Java-friendly overload accepting delay in milliseconds.
   *
   * @param scope The coroutine scope for lifetime management
   * @param name Debug name for the coroutine
   * @param delayMillis The delay in milliseconds
   * @return A builder to configure and create the queue
   */
  @JvmStatic
  fun <T> forScope(scope: CoroutineScope, name: String, delayMillis: Int): ScopeBuilder<T> {
    return forScope(scope, name, delayMillis.milliseconds)
  }

  /**
   * Creates a builder for a debounced update queue tied to a [JComponent] lifecycle.
   * The queue starts when the component is shown and stops when hidden.
   *
   * @param component The UI component whose lifecycle controls the queue
   * @param name Debug name for the coroutine
   * @param delay The delay as a [Duration]
   * @return A builder to configure and create the queue
   */
  fun <T> forComponent(component: JComponent, name: String, delay: Duration): ComponentBuilder<T> {
    return ComponentBuilder(component, name, delay)
  }

  /**
   * Creates a builder for a debounced update queue tied to a [JComponent] lifecycle.
   * Java-friendly overload accepting delay in milliseconds.
   *
   * @param component The UI component whose lifecycle controls the queue
   * @param name Debug name for the coroutine
   * @param delayMillis The delay in milliseconds
   * @return A builder to configure and create the queue
   */
  @JvmStatic
  fun <T> forComponent(component: JComponent, name: String, delayMillis: Int): ComponentBuilder<T> {
    return forComponent(component, name, delayMillis.milliseconds)
  }

  /**
   * Java-friendly helper to build a coroutine context.
   * Combines additional context with modality state from a component.
   *
   * @param additionalContext Additional coroutine context (e.g., Dispatchers.EDT)
   * @param modalityStateComponent Component to derive modality state from (optional)
   * @return Combined coroutine context
   */
  @JvmStatic
  private fun buildContext(
    additionalContext: CoroutineContext?,
    modalityStateComponent: JComponent?
  ): CoroutineContext {
    return (additionalContext ?: EmptyCoroutineContext) +
           (modalityStateComponent?.let { ModalityState.stateForComponent(it).asContextElement() } ?: EmptyCoroutineContext)
  }

  /**
   * Builder for scope-bound debounced update queues.
   */
  @ApiStatus.Experimental
  class ScopeBuilder<T> internal constructor(
    private val scope: CoroutineScope,
    private val name: String,
    private val delay: Duration
  ) {
    private var context: CoroutineContext = EmptyCoroutineContext
    private var restartTimerOnAdd: Boolean = false

    /**
     * Sets the coroutine context for executing the action (e.g., Dispatchers.EDT).
     */
    fun withContext(context: CoroutineContext): ScopeBuilder<T> {
      this.context = context
      return this
    }

    /**
     * Java-friendly method to build context from additional context and modality state component.
     *
     * @param additionalContext Optional coroutine context (e.g., Dispatchers.EDT)
     * @param modalityStateComponent Optional component for modality state
     */
    @JvmName("withContextForModality")
    @JvmOverloads
    fun withContext(
      additionalContext: CoroutineContext?,
      modalityStateComponent: JComponent? = null
    ): ScopeBuilder<T> {
      this.context = buildContext(additionalContext, modalityStateComponent)
      return this
    }

    /**
     * Sets whether the timer should restart on each new request.
     * - true: Debouncing behavior (timer resets on each request)
     * - false: Throttling behavior (timer starts once)
     */
    fun restartTimerOnAdd(restart: Boolean): ScopeBuilder<T> {
      this.restartTimerOnAdd = restart
      return this
    }

    /**
     * Creates a queue that processes only the latest item (DROP_OLDEST strategy).
     *
     * Uses `collectLatest` internally: if a new item arrives while the previous action is still executing,
     * the previous execution will be cancelled before processing the new item.
     *
     * @param action The action to perform for each item
     * @return A queue that can be used to submit items via [UpdateQueue.queue]
     */
    fun runLatest(action: suspend (T) -> Unit): UpdateQueue<T> {
      return SingleScopeQueue(scope, name, delay, context, restartTimerOnAdd, action)
    }

    /**
     * Creates a queue that processes only the latest item (DROP_OLDEST strategy).
     * Java-friendly overload accepting a [Consumer].
     *
     * Uses `collectLatest` internally: if a new item arrives while the previous action is still executing,
     * the previous execution will be cancelled before processing the new item.
     *
     * @param action The action to perform for each item
     * @return A queue that can be used to submit items via [UpdateQueue.queue]
     */
    @JvmName("runLatestConsumer")
    fun runLatest(action: Consumer<T>): UpdateQueue<T> {
      return runLatest { action.accept(it) }
    }

    /**
     * Creates a queue that batches all items during the delay window.
     *
     * @param action The action to perform for each batch of items
     * @return A queue that can be used to submit items via [UpdateQueue.queue]
     */
    fun runBatched(action: suspend (List<T>) -> Unit): UpdateQueue<T> {
      return BatchedScopeQueue(scope, name, delay, context, action)
    }

    /**
     * Creates a queue that batches all items during the delay window.
     * Java-friendly overload accepting a [Consumer].
     *
     * @param action The action to perform for each batch of items
     * @return A queue that can be used to submit items via [UpdateQueue.queue]
     */
    @JvmName("runBatchedConsumer")
    fun runBatched(action: Consumer<List<T>>): UpdateQueue<T> {
      return runBatched { action.accept(it) }
    }
  }

  /**
   * Builder for component-bound debounced update queues.
   */
  @ApiStatus.Experimental
  class ComponentBuilder<T> internal constructor(
    private val component: JComponent,
    private val name: String,
    private val delay: Duration
  ) {
    private var context: CoroutineContext = EmptyCoroutineContext
    private var restartTimerOnAdd: Boolean = false

    /**
     * Sets the coroutine context for executing the action (e.g., Dispatchers.EDT).
     */
    fun withContext(context: CoroutineContext): ComponentBuilder<T> {
      this.context = context
      return this
    }

    /**
     * Java-friendly method to build context from additional context and modality state component.
     *
     * @param additionalContext Optional coroutine context (e.g., Dispatchers.EDT)
     * @param modalityStateComponent Optional component for modality state
     */
    @JvmName("withContextForModality")
    @JvmOverloads
    fun withContext(
      additionalContext: CoroutineContext?,
      modalityStateComponent: JComponent? = null
    ): ComponentBuilder<T> {
      this.context = buildContext(additionalContext, modalityStateComponent)
      return this
    }

    /**
     * Sets whether the timer should restart on each new request.
     * - true: Debouncing behavior (timer resets on each request)
     * - false: Throttling behavior (timer starts once)
     */
    fun restartTimerOnAdd(restart: Boolean): ComponentBuilder<T> {
      this.restartTimerOnAdd = restart
      return this
    }

    /**
     * Creates a queue that processes only the latest item (DROP_OLDEST strategy).
     *
     * Uses `collectLatest` internally: if a new item arrives while the previous action is still executing,
     * the previous execution will be cancelled before processing the new item.
     *
     * @param action The action to perform for each item
     * @return A queue that can be used to submit items via [UpdateQueue.queue]
     */
    fun runLatest(action: suspend (T) -> Unit): UpdateQueue<T> {
      return SingleComponentQueue(component, name, delay, context, restartTimerOnAdd, action)
    }

    /**
     * Creates a queue that processes only the latest item (DROP_OLDEST strategy).
     * Java-friendly overload accepting a [Consumer].
     *
     * Uses `collectLatest` internally: if a new item arrives while the previous action is still executing,
     * the previous execution will be cancelled before processing the new item.
     *
     * @param action The action to perform for each item
     * @return A queue that can be used to submit items via [UpdateQueue.queue]
     */
    @JvmName("runLatestConsumer")
    fun runLatest(action: Consumer<T>): UpdateQueue<T> {
      return runLatest { action.accept(it) }
    }

    /**
     * Creates a queue that batches all items during the delay window.
     *
     * @param action The action to perform for each batch of items
     * @return A queue that can be used to submit items via [UpdateQueue.queue]
     */
    fun runBatched(action: suspend (List<T>) -> Unit): UpdateQueue<T> {
      return BatchedComponentQueue(component, name, delay, context, action)
    }

    /**
     * Creates a queue that batches all items during the delay window.
     * Java-friendly overload accepting a [Consumer].
     *
     * @param action The action to perform for each batch of items
     * @return A queue that can be used to submit items via [UpdateQueue.queue]
     */
    @JvmName("runBatchedConsumer")
    fun runBatched(action: Consumer<List<T>>): UpdateQueue<T> {
      return runBatched { action.accept(it) }
    }
  }
}

/**
 * Common interface for update queues.
 */
@ApiStatus.Experimental
interface UpdateQueue<T> {
  /**
   * Queues an item for processing.
   */
  fun queue(item: T)

  /**
   * Cancels the queue when the given [Disposable] is disposed.
   */
  fun cancelOnDispose(disposable: Disposable): UpdateQueue<T>
}

/**
 * Helper function to process flow with exception handling.
 */
private suspend fun <T> Channel<T>.processLatest(
  name: String,
  delay: Duration,
  context: CoroutineContext,
  restartTimerOnAdd: Boolean,
  action: suspend (T) -> Unit
) {
  val flow = if (restartTimerOnAdd) {
    receiveAsFlow().debounce(delay)
  } else {
    receiveAsFlow().sample(delay)
  }

  flow.collectLatest { item ->
    try {
      withContext(context) {
        action(item)
      }
    } catch (e: CancellationException) {
      throw e // Propagate cancellation
    } catch (e: Throwable) {
      logger<DebouncedUpdates>().error("Exception in DebouncedUpdates '$name'", e)
    }
  }
}

/**
 * Helper function to process batched flow with exception handling.
 */
private suspend fun <T> Channel<T>.processBatched(
  name: String,
  delay: Duration,
  context: CoroutineContext,
  action: suspend (List<T>) -> Unit
) {
  receiveAsFlow()
    .debounceBatch(delay)
    .collect { batch ->
      try {
        withContext(context) {
          action(batch)
        }
      } catch (e: CancellationException) {
        throw e // Propagate cancellation
      } catch (e: Throwable) {
        logger<DebouncedUpdates>().error("Exception in DebouncedUpdates '$name'", e)
      }
    }
}

/**
 * Base class for update queue implementations.
 */
@ApiStatus.Experimental
private abstract class BaseUpdateQueue<T>(
  protected val name: String
) : UpdateQueue<T> {

  protected abstract val channel: Channel<T>
  protected abstract val job: Job

  override fun queue(item: T) {
    require(job.isActive) { "Cannot queue to cancelled DebouncedUpdates '$name'" }
    val result = channel.trySend(item)
    check(result.isSuccess) { "Failed to send value to channel in DebouncedUpdates '$name': $result" }
  }

  override fun cancelOnDispose(disposable: Disposable): UpdateQueue<T> {
    job.cancelOnDispose(disposable)
    return this
  }
}

/**
 * Single-item queue bound to a CoroutineScope.
 */
@ApiStatus.Experimental
private class SingleScopeQueue<T>(
  scope: CoroutineScope,
  name: String,
  delay: Duration,
  context: CoroutineContext,
  restartTimerOnAdd: Boolean,
  action: suspend (T) -> Unit
) : BaseUpdateQueue<T>(name) {

  init {
    require(context[Job] == null) { "context must not contain a Job" }
  }

  override val channel = Channel<T>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  override val job: Job = scope.launch(CoroutineName(name)) {
    channel.processLatest(name, delay, context, restartTimerOnAdd, action)
  }
}

/**
 * Batched queue bound to a CoroutineScope.
 */
@ApiStatus.Experimental
private class BatchedScopeQueue<T>(
  scope: CoroutineScope,
  name: String,
  delay: Duration,
  context: CoroutineContext,
  action: suspend (List<T>) -> Unit
) : BaseUpdateQueue<T>(name) {

  init {
    require(context[Job] == null) { "context must not contain a Job" }
  }

  override val channel = Channel<T>(capacity = Channel.UNLIMITED)
  override val job: Job = scope.launch(CoroutineName(name)) {
    channel.processBatched(name, delay, context, action)
  }
}

/**
 * Single-item queue bound to a JComponent lifecycle.
 */
@ApiStatus.Experimental
private class SingleComponentQueue<T>(
  component: JComponent,
  name: String,
  delay: Duration,
  context: CoroutineContext,
  restartTimerOnAdd: Boolean,
  action: suspend (T) -> Unit
) : BaseUpdateQueue<T>(name) {

  init {
    require(context[Job] == null) { "context must not contain a Job" }
  }

  override val channel = Channel<T>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  override val job: Job = component.launchOnShow(name) {
    channel.processLatest(name, delay, context, restartTimerOnAdd, action)
  }
}

/**
 * Batched queue bound to a JComponent lifecycle.
 */
@ApiStatus.Experimental
private class BatchedComponentQueue<T>(
  component: JComponent,
  name: String,
  delay: Duration,
  context: CoroutineContext,
  action: suspend (List<T>) -> Unit
) : BaseUpdateQueue<T>(name) {

  init {
    require(context[Job] == null) { "context must not contain a Job" }
  }

  override val channel = Channel<T>(capacity = Channel.UNLIMITED)
  override val job: Job = component.launchOnShow(name) {
    channel.processBatched(name, delay, context, action)
  }
}
