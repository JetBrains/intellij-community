// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.util.ui.update

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.util.cancelOnDispose
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import javax.swing.JComponent
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Builder-based API for creating debounced/throttled update queues.
 *
 * Supports two lifetime modes:
 * - **forScope**: Tied to a [CoroutineScope] lifecycle
 * - **forComponent**: Tied to a [JComponent] visibility lifecycle
 *
 * Supports two execution modes:
 * - **runLatest**: Processes only the latest queued item (replaces earlier items with newer ones).
 *   Actions execute sequentially without cancellation.
 * - **runBatched**: Collects and processes all items accumulated during the delay window.
 *   Actions execute sequentially without cancellation.
 *
 * Supports two timing modes:
 * - **Throttle mode** (default, `restartTimerOnAdd = false`): Timer starts on first item, waits for delay
 *   collecting other items, then processes. Subsequent items don't restart the timer.
 * - **Debounce mode** (`restartTimerOnAdd = true`): Timer resets on each new item. Only processes after
 *   a period of inactivity (no new items for the full delay duration).
 *
 * Queues can be cancelled early using [UpdateQueue.cancelOnDispose] to tie them to a [Disposable] lifecycle.
 *
 * Example usage in Kotlin:
 * ```kotlin
 * // Latest item, scope-bound
 * val queue1 = DebouncedUpdates.forScope<State>(scope, "update-ui", 300.milliseconds)
 *   .withContext(Dispatchers.EDT)
 *   .restartTimerOnAdd(true)
 *   .runLatest { state -> updateUI(state) }
 *   .cancelOnDispose(disposable)
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
 *   .runLatestConsumer(state -> updateUI(state))
 *   .cancelOnDispose(disposable);
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
  fun <T> forScope(scope: CoroutineScope, name: String, delay: Duration): Builder<T> {
    return Builder.forScope(scope, name, delay)
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
  fun <T> forScope(scope: CoroutineScope, name: String, delayMillis: Int): Builder<T> {
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
  fun <T> forComponent(component: JComponent, name: String, delay: Duration): Builder<T> {
    return Builder.forComponent(component, name, delay)
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
  fun <T> forComponent(component: JComponent, name: String, delayMillis: Int): Builder<T> {
    return forComponent(component, name, delayMillis.milliseconds)
  }

  /**
   * Sealed hierarchy for builder owner types
   */
  private sealed interface BuilderOwner {
    companion object {
      fun scope(scope: CoroutineScope): BuilderOwner = ScopeOwner(scope)
      fun component(component: JComponent): BuilderOwner = ComponentOwner(component)
    }
  }

  private data class ScopeOwner(val scope: CoroutineScope) : BuilderOwner

  private data class ComponentOwner(val component: JComponent) : BuilderOwner

  /**
   * Unified builder for debounced update queues.
   */
  @ApiStatus.Experimental
  class Builder<T> private constructor(
    private val owner: BuilderOwner,
    private val name: String,
    private val delay: Duration
  ) {
    companion object {
      internal fun <T> forScope(scope: CoroutineScope, name: String, delay: Duration): Builder<T> {
        return Builder(BuilderOwner.scope(scope), name, delay)
      }

      internal fun <T> forComponent(component: JComponent, name: String, delay: Duration): Builder<T> {
        return Builder(BuilderOwner.component(component), name, delay)
      }
    }

    private var context: CoroutineContext = EmptyCoroutineContext
    private var restartTimerOnAdd: Boolean = false

    /**
     * Sets the coroutine context for executing the action (e.g., Dispatchers.EDT).
     */
    fun withContext(context: CoroutineContext): Builder<T> {
      this.context = context
      return this
    }

    /**
     * Adds the modality state derived from the given component to the current context.
     * The modality state is combined with any previously set context.
     *
     * Call `withContext()` first to set the dispatcher (e.g., Dispatchers.EDT), then call this method
     * to add the component's modality state.
     *
     * Example:
     * ```kotlin
     * DebouncedUpdates.forScope<State>(scope, "update", 300.milliseconds)
     *   .withContext(Dispatchers.EDT)
     *   .withComponentModality(myComponent)
     *   .runLatest { ... }
     * ```
     *
     * @param component The component to derive modality state from
     */
    fun withComponentModality(component: JComponent): Builder<T> {
      this.context += ModalityState.stateForComponent(component).asContextElement()
      return this
    }

    /**
     * Sets whether the timer should restart on each new request.
     * - true: Debouncing behavior (timer resets on each request)
     * - false: Throttling behavior (timer starts once, default)
     */
    fun restartTimerOnAdd(restart: Boolean = false): Builder<T> {
      this.restartTimerOnAdd = restart
      return this
    }

    /**
     * Creates a queue that processes only the latest queued item.
     *
     * If multiple items are queued while waiting for the delay, only the most recent one is processed.
     * Actions execute sequentially - if a new item arrives while the previous action is still executing,
     * it waits for the previous action to complete before starting.
     *
     * Example:
     * ```kotlin
     * val queue = DebouncedUpdates.forScope<State>(scope, "update", 100.milliseconds)
     *   .runLatest { state -> updateUI(state) }
     *
     * queue.queue(state1)
     * queue.queue(state2)  // state1 is dropped
     * queue.queue(state3)  // state2 is dropped
     * // After delay: only state3 is processed
     * ```
     *
     * @param action The action to perform for each item
     * @return A queue that can be used to submit items via [UpdateQueue.queue]
     */
    @JvmSynthetic
    fun runLatest(action: suspend (T) -> Unit): UpdateQueue<T> {
      return when (owner) {
        is ScopeOwner -> SingleScopeQueue(owner.scope, name, delay, context, restartTimerOnAdd, action)
        is ComponentOwner -> SingleComponentQueue(owner.component, name, delay, context, restartTimerOnAdd, action)
      }
    }

    /**
     * Creates a queue that processes only the latest queued item.
     * Java-friendly overload accepting a [Consumer].
     *
     * See [runLatest] for behavior details.
     *
     * @param action The action to perform for each item
     * @return A queue that can be used to submit items via [UpdateQueue.queue]
     */
    fun runLatest(action: Consumer<T>): UpdateQueue<T> {
      return runLatest { action.accept(it) }
    }

    /**
     * Creates a queue that batches all items during the delay window.
     *
     * Collects all items queued within the delay period and processes them together as a batch.
     * Supports both throttle mode (default) and debounce mode via [restartTimerOnAdd].
     *
     * Throttle mode example:
     * ```kotlin
     * val queue = DebouncedUpdates.forScope<Int>(scope, "batch", 100.milliseconds)
     *   .runBatched { items -> processBatch(items) }
     *
     * delay(40)
     * queue.queue(1)
     * delay(40)
     * queue.queue(2)
     * delay(120)  // First batch emitted: [1, 2]
     * queue.queue(3)
     * delay(120)  // Second batch emitted: [3]
     * ```
     *
     * Debounce mode example:
     * ```kotlin
     * val queue = DebouncedUpdates.forScope<Int>(scope, "batch", 100.milliseconds)
     *   .restartTimerOnAdd(true)
     *   .runBatched { items -> processBatch(items) }
     *
     * queue.queue(1)
     * delay(50)
     * queue.queue(2)  // Timer resets
     * delay(50)
     * queue.queue(3)  // Timer resets
     * delay(150)  // First batch emitted: [1, 2, 3]
     * queue.queue(4)
     * delay(50)
     * queue.queue(5)  // Timer resets
     * delay(150)  // Second batch emitted: [4, 5]
     * ```
     *
     * @param action The action to perform for each batch of items
     * @return A queue that can be used to submit items via [UpdateQueue.queue]
     */
    @JvmSynthetic
    fun runBatched(action: suspend (List<T>) -> Unit): UpdateQueue<T> {
      return when (owner) {
        is ScopeOwner -> BatchedScopeQueue(owner.scope, name, delay, context, restartTimerOnAdd, action)
        is ComponentOwner -> BatchedComponentQueue(owner.component, name, delay, context, restartTimerOnAdd, action)
      }
    }

    /**
     * Creates a queue that batches all items during the delay window.
     * Java-friendly overload accepting a [Consumer].
     *
     * See [runBatched] for behavior details.
     *
     * @param action The action to perform for each batch of items
     * @return A queue that can be used to submit items via [UpdateQueue.queue]
     */
    fun runBatched(action: Consumer<List<T>>): UpdateQueue<T> {
      return runBatched { action.accept(it) }
    }
  }
}

/**
 * Common interface for update queues.
 */
@ApiStatus.Experimental
sealed interface UpdateQueue<T> {
  /**
   * Queues an item for processing.
   *
   * @throws IllegalArgumentException if the queue is closed (e.g., scope was cancelled, component was removed, or [cancelOnDispose] was triggered)
   */
  fun queue(item: T)

  /**
   * Cancels the queue when the given [Disposable] is disposed.
   */
  fun cancelOnDispose(disposable: Disposable): UpdateQueue<T>

  /**
   * Waits for all queued items to be processed.
   * This is a blocking call intended for testing.
   *
   * **WARNING:** This method uses `runBlockingCancellable` which is forbidden on EDT.
   * When calling from EDT, use `PlatformTestUtil.waitWithEventsDispatching` with [isAllExecuted] condition.
   *
   * @param timeout timeout duration
   * @throws TimeoutException if the timeout is exceeded
   */
  @TestOnly
  @RequiresBackgroundThread
  @Throws(TimeoutException::class)
  fun waitForAllExecuted(timeout: Duration)

  /**
   * Java-friendly overload: Waits for all queued items to be processed.
   *
   * @param timeoutMillis timeout in milliseconds
   * @throws TimeoutException if the timeout is exceeded
   */
  @TestOnly
  @RequiresBackgroundThread
  @Throws(TimeoutException::class)
  fun waitForAllExecuted(timeoutMillis: Long)

  /**
   * EDT-safe condition for use with `PlatformTestUtil.waitWithEventsDispatching`.
   *
   * Returns true when all queued items are processed and no job is currently running.
   *
   * Example usage from EDT:
   * ```kotlin
   * PlatformTestUtil.waitWithEventsDispatching(
   *   "Timed out waiting for queue",
   *   { updateQueue.isAllExecuted },
   *   10
   * )
   * ```
   *
   * This is the EDT-safe alternative to [waitForAllExecuted] which uses `runBlockingCancellable`
   * and is forbidden on EDT.
   */
  @get:TestOnly
  val isAllExecuted: Boolean
}

/**
 * Base class for update queue implementations.
 */
@ApiStatus.Experimental
private abstract class BaseUpdateQueue<T>(
  protected val name: String,
  context: CoroutineContext,
  channelCapacity: Int
) : UpdateQueue<T> {

  init {
    require(context[Job] == null) { "context must not contain a Job" }
  }

  protected val channel: Channel<T> = Channel(capacity = channelCapacity, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  protected abstract val job: Job
  
  // Track the currently running processing job for tests
  protected val processingJob = AtomicReference<Job?>(null)
  
  // Track whether we're collecting items - set after receive(), cleared before starting processing job
  @Volatile
  protected var isCollecting: Boolean = false

  override fun queue(item: T) {
    require(job.isActive) { "Cannot queue to cancelled UpdateQueue '$name'" }
    val result = channel.trySend(item)
    check(result.isSuccess) { "Failed to send value to channel in UpdateQueue '$name': $result" }
  }

  override fun cancelOnDispose(disposable: Disposable): UpdateQueue<T> {
    job.cancelOnDispose(disposable)
    return this
  }
  
  /**
   * Returns true if there are no pending items in the channel AND we're not collecting.
   * Returns false if channel has items OR we just received the first item and are collecting.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  private val isEmpty: Boolean
    get() = channel.isEmpty && !isCollecting
  
  /**
   * Returns true if a job is currently processing items.
   */
  private val isProcessing: Boolean
    get() {
      val job = processingJob.get()
      return job != null && !job.isCompleted
    }
  
  /**
   * Waits for all queued items to be processed.
   * This is a blocking call intended for testing.
   * 
   * **WARNING:** This method uses `runBlockingCancellable` which is forbidden on EDT.
   * When calling from EDT, use `PlatformTestUtil.waitWithEventsDispatching` with [isAllExecuted] condition.
   * 
   * @param timeout timeout duration
   * @throws TimeoutException if the timeout is exceeded
   */
  @TestOnly
  @RequiresBackgroundThread
  @Throws(TimeoutException::class)
  override fun waitForAllExecuted(timeout: Duration) {
    val deadline = System.currentTimeMillis() + timeout.inWholeMilliseconds
    
    // Loop until both channel is empty AND no processing job is running
    // This handles nested queuing (items queued during batch processing)
    while (!isAllExecuted) {
      if (System.currentTimeMillis() > deadline) {
        throw TimeoutException("Timed out waiting for DebouncedUpdates '$name'")
      }
      
      val currentJob = processingJob.get()
      if (currentJob != null && !currentJob.isCompleted) {
        runBlockingCancellable {
          withTimeout((deadline - System.currentTimeMillis()).milliseconds) {
            currentJob.join()
          }
        }
      } else {
        // Avoid busy-waiting when item is queued but processing hasn't started yet
        Thread.sleep(10)
      }
    }
  }
  
  /**
   * Java-friendly overload: Waits for all queued items to be processed.
   * 
   * @param timeoutMillis timeout in milliseconds
   * @throws TimeoutException if the timeout is exceeded
   */
  @TestOnly
  @RequiresBackgroundThread
  @Throws(TimeoutException::class)
  override fun waitForAllExecuted(timeoutMillis: Long) {
    waitForAllExecuted(timeoutMillis.milliseconds)
  }
  
  /**
   * EDT-safe condition for use with `PlatformTestUtil.waitWithEventsDispatching`.
   * 
   * Returns true when all queued items are processed and no job is currently running.
   * 
   * Example usage from EDT:
   * ```kotlin
   * PlatformTestUtil.waitWithEventsDispatching(
   *   "Timed out waiting for queue",
   *   { updateQueue.isAllExecuted },
   *   10
   * )
   * ```
   * 
   * This is the EDT-safe alternative to [waitForAllExecuted] which uses `runBlockingCancellable`
   * and is forbidden on EDT.
   */
  override val isAllExecuted: Boolean
    get() = isEmpty && !isProcessing
  
  /**
   * Core function for processing channel items with manual channel processing.
   * 
   * @param delay The delay duration
   * @param context The coroutine context for executing actions
   * @param restartTimerOnAdd If true, uses debounce mode (timer resets on each new item).
   *                          If false, uses throttle mode (timer starts on the first item, collects items during delay).
   * @param onReceive Called when a new item is received. Should either add to the buffer or replace the current item.
   * @param onPrepare Called to prepare the batch after delay expires. Runs in the collector coroutine to ensure happens-before.
   * @param onProcess Called to process the prepared batch. Runs in a separate coroutine with the specified context.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  protected suspend fun <R> processWithDelay(
    delay: Duration,
    context: CoroutineContext,
    restartTimerOnAdd: Boolean,
    onReceive: (T) -> Unit,
    onPrepare: () -> R,
    onProcess: suspend (R) -> Unit
  ) {
    while (true) {
      // Wait for first item and add it
      onReceive(channel.receive())
      
      // Mark as collecting - we received an item and are collecting the batch
      isCollecting = true

      if (restartTimerOnAdd) {
        // Debounce mode: restart timer on each new item
        var lastItemTime = System.nanoTime()
        
        while (true) {
          val remainingDelay = delay.inWholeNanoseconds - (System.nanoTime() - lastItemTime)
          if (remainingDelay <= 0) {
            // Timer expired, process collected items
            break
          }
          
          // Wait for remaining delay or new item, whichever comes first
          withTimeoutOrNull(remainingDelay.nanoseconds) {
            onReceive(channel.receive())
            lastItemTime = System.nanoTime()
          } ?: break // Timeout - process collected items
        }
      } else {
        // Throttle/sample mode: fixed interval
        delay(delay)
        
        // Collect all remaining items
        var extraItem = channel.tryReceive().getOrNull()
        while (extraItem != null) {
          onReceive(extraItem)
          extraItem = channel.tryReceive().getOrNull()
        }
      }

      // Prepare the data in the current coroutine (ensures happens-before with onReceive)
      val data = onPrepare()

      // Process the data in a separate coroutine
      coroutineScope {
        val job = launch {
          try {
            withContext(context) {
              onProcess(data)
            }
          } catch (e: CancellationException) {
            throw e
          } catch (e: Throwable) {
            logger<DebouncedUpdates>().error("Exception in DebouncedUpdates '$name'", e)
          }
        }
        
        // Track the processing job and clear collecting flag
        // IMPORTANT: Must set processingJob before clearing isCollecting to avoid race condition
        // where both would be false/null even though processing is about to start
        processingJob.set(job)
        isCollecting = false
        
        try {
          job.join()
        } finally {
          processingJob.set(null)
        }
      }
    }
  }
  
  /**
   * Process latest item only (replaces previous item with new one).
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  protected suspend fun processLatest(
    delay: Duration,
    context: CoroutineContext,
    restartTimerOnAdd: Boolean,
    action: suspend (T) -> Unit
  ) {
    var latestItem: T? = null
    
    processWithDelay(
      delay = delay,
      context = context,
      restartTimerOnAdd = restartTimerOnAdd,
      onReceive = { latestItem = it },
      onPrepare = { latestItem!! },
      onProcess = { item -> action(item) }
    )
  }
  
  /**
   * Process all items as a batch (collects all items into a list).
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  protected suspend fun processBatched(
    delay: Duration,
    context: CoroutineContext,
    restartTimerOnAdd: Boolean,
    action: suspend (List<T>) -> Unit
  ) {
    val buffer = mutableListOf<T>()
    
    processWithDelay(
      delay = delay,
      context = context,
      restartTimerOnAdd = restartTimerOnAdd,
      onReceive = { buffer.add(it) },
      onPrepare = {
        val batch = buffer.toList()
        buffer.clear()
        batch
      },
      onProcess = { batch -> action(batch) }
    )
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
) : BaseUpdateQueue<T>(name, context, channelCapacity = 1) {

  override val job: Job = scope.launch(CoroutineName(name)) {
    processLatest(delay, context, restartTimerOnAdd, action)
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
  restartTimerOnAdd: Boolean,
  action: suspend (List<T>) -> Unit
) : BaseUpdateQueue<T>(name, context, channelCapacity = Channel.UNLIMITED) {

  override val job: Job = scope.launch(CoroutineName(name)) {
    processBatched(delay, context, restartTimerOnAdd, action)
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
) : BaseUpdateQueue<T>(name, context, channelCapacity = 1) {

  override val job: Job = component.launchOnShow(name) {
    processLatest(delay, context, restartTimerOnAdd, action)
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
  restartTimerOnAdd: Boolean,
  action: suspend (List<T>) -> Unit
) : BaseUpdateQueue<T>(name, context, channelCapacity = Channel.UNLIMITED) {

  override val job: Job = component.launchOnShow(name) {
    processBatched(delay, context, restartTimerOnAdd, action)
  }
}
