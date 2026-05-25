// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.util.ui.update

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.util.cancelOnDispose
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
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
 * Supports three execution modes (all execute sequentially without cancellation):
 * - **runLatest**: Processes only the latest queued item (replaces earlier items with newer ones).
 *   Available for both [ScopeBuilder] (scope-based) and [ComponentBuilder] (component-based) queues.
 * - **runBatched**: Collects and processes all items accumulated during the delay window.
 *   Only available for scope-based queues ([ScopeBuilder]).
 * - **runBatchedDistinct**: Like runBatched, but automatically removes duplicate items.
 *   Only available for scope-based queues ([ScopeBuilder]).
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
 * // Batched items, scope-bound
 * val queue1 = DebouncedUpdates.forScope<Event>(scope, "process-events", 300.milliseconds)
 *   .withContext(Dispatchers.Default)
 *   .restartTimerOnAdd(true)
 *   .runBatched { events -> processEvents(events) }
 *   .cancelOnDispose(disposable)
 *
 * // Latest item, component-bound
 * val queue2 = DebouncedUpdates.forComponent<State>(component, "update-ui", 100.milliseconds)
 *   .runLatest { state -> updateUI(state) }
 *
 * queue1.queue(event)
 * queue2.queue(newState)
 * ```
 *
 * Example usage in Java:
 * ```java
 * // Batched items, scope-bound
 * var queue1 = DebouncedUpdates.forScope(scope, "process-events", 300)
 *   .withContext(Dispatchers.getDefault())
 *   .restartTimerOnAdd(true)
 *   .runBatched(events -> processEvents(events))
 *   .cancelOnDispose(disposable);
 *
 * // Latest item, component-bound
 * var queue2 = DebouncedUpdates.forComponent(component, "update-ui", 100)
 *   .runLatest(state -> updateUI(state));
 *
 * queue1.queue(event);
 * queue2.queue(newState);
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
    return ScopeBuilderImpl.forScope(scope, name, delay)
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
   * Creates a builder for a debounced update queue tied to a [JComponent] visibility lifecycle.
   *
   * The queue accepts items at any time, but the processing action only executes while the component is showing.
   * Items queued while the component is hidden remain in the queue and are processed when the component becomes visible.
   * When the component is hidden, any currently executing action is canceled and will be reprocessed when the component is shown again.
   * Items are never lost due to the component being hidden.
   *
   * Only [ComponentBuilder.runLatest] is supported for component-based queues.
   * Use [forScope] if batching is required.
   *
   * Actions run on `Dispatchers.UI` by default. Use [ComponentBuilder.withContext] to override.
   *
   * @param component The UI component whose visibility controls when items are processed
   * @param name Debug name for the coroutine
   * @param delay The delay as a [Duration]
   * @return A builder to configure and create the queue
   */
  fun <T> forComponent(component: JComponent, name: String, delay: Duration): ComponentBuilder<T> {
    return ComponentBuilderImpl.forComponent(component, name, delay)
  }

  /**
   * Creates a builder for a debounced update queue tied to a [JComponent] visibility lifecycle.
   * Java-friendly overload accepting delay in milliseconds.
   *
   * See the overload with [Duration] parameter for detailed behavior documentation.
   *
   * @param component The UI component whose visibility controls when items are processed
   * @param name Debug name for the coroutine
   * @param delayMillis The delay in milliseconds
   * @return A builder to configure and create the queue
   */
  @JvmStatic
  fun <T> forComponent(component: JComponent, name: String, delayMillis: Int): ComponentBuilder<T> {
    return forComponent(component, name, delayMillis.milliseconds)
  }

  /**
   * Common base interface for debounced update queue builders.
   *
   * See [ComponentBuilder] (returned by [DebouncedUpdates.forComponent]) and
   * [ScopeBuilder] (returned by [DebouncedUpdates.forScope]).
   */
  @ApiStatus.Experimental
  interface Builder<T> {
    /**
     * Sets the coroutine context for executing the action (e.g., Dispatchers.EDT).
     */
    fun withContext(context: CoroutineContext): Builder<T>

    /**
     * Sets whether the timer should restart on each new request.
     * - true: Debouncing behavior (timer resets on each request)
     * - false: Throttling behavior (timer starts once, default)
     */
    fun restartTimerOnAdd(restart: Boolean = false): Builder<T>

    /**
     * Creates a queue that processes only the latest queued item.
     *
     * If multiple items are queued while waiting for the delay, only the most recent one is processed.
     * Actions execute sequentially - if a new item arrives while the previous action is still executing,
     * it waits for the previous action to complete before starting.
     *
     * **Note for component-based queues ([ComponentBuilder]):** The action may be called more than
     * once for the same item. If the component is hidden while the action is executing, the action is
     * cancelled and will be re-invoked when the component becomes visible again, unless a newer item
     * has arrived in the meantime.
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
    fun runLatest(action: suspend (T) -> Unit): UpdateQueue<T>

    /**
     * Creates a queue that processes only the latest queued item.
     * Java-friendly overload accepting a [Consumer].
     *
     * See the overload with suspend function parameter for behavior details.
     *
     * @param action The action to perform for each item
     * @return A queue that can be used to submit items via [UpdateQueue.queue]
     */
    fun runLatest(action: Consumer<T>): UpdateQueue<T>
  }

  /**
   * Builder interface for component-based debounced update queues.
   *
   * Returned by [DebouncedUpdates.forComponent]. Only [runLatest] is supported.
   * Use [DebouncedUpdates.forScope] if batching is required.
   */
  @ApiStatus.Experimental
  interface ComponentBuilder<T> : Builder<T>

  /**
   * Builder interface for scope-based debounced update queues.
   *
   * Returned by [DebouncedUpdates.forScope]. Extends [Builder] with scope-only operations:
   * [withComponentModality], [runBatched], and [runBatchedDistinct].
   */
  @ApiStatus.Experimental
  interface ScopeBuilder<T> : Builder<T> {
    override fun withContext(context: CoroutineContext): ScopeBuilder<T>
    override fun restartTimerOnAdd(restart: Boolean): ScopeBuilder<T>

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
    fun withComponentModality(component: JComponent): ScopeBuilder<T>

    /**
     * Creates a queue that batches all items during the delay window.
     *
     * Collects all items queued within the delay period and processes them together as a batch.
     * Supports both throttle mode (default) and debounce mode via [restartTimerOnAdd].
     *
     * To automatically deduplicate items, use [runBatchedDistinct] instead.
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
    fun runBatched(action: suspend (List<T>) -> Unit): UpdateQueue<T>

    /**
     * Creates a queue that batches all items during the delay window.
     * Java-friendly overload accepting a [Consumer].
     *
     * See the overload with suspend function parameter for behavior details.
     *
     * @param action The action to perform for each batch of items
     * @return A queue that can be used to submit items via [UpdateQueue.queue]
     */
    fun runBatched(action: Consumer<List<T>>): UpdateQueue<T>

    /**
     * Creates a queue that batches all items during the delay window with automatic deduplication.
     *
     * Like [runBatched], but automatically removes duplicate items. The action receives a [Set] of unique items.
     * Useful when multiple identical items may be queued, and you only want to process each unique item once.
     *
     * **Note:** The order of items in the set is not guaranteed. If you need to preserve order, use [runBatched] instead.
     *
     * Example:
     * ```kotlin
     * val queue = DebouncedUpdates.forScope<String>(scope, "batch", 100.milliseconds)
     *   .runBatchedDistinct { ids -> processUniqueIds(ids) }
     *
     * queue.queue("id1")
     * queue.queue("id2")
     * queue.queue("id1")  // Duplicate, will be deduplicated
     * delay(150)  // Batch emitted: ["id1", "id2"] (order not guaranteed)
     * ```
     *
     * @param action The action to perform for each batch of deduplicated items
     * @return A queue that can be used to submit items via [UpdateQueue.queue]
     */
    @JvmSynthetic
    fun runBatchedDistinct(action: suspend (Set<T>) -> Unit): UpdateQueue<T>

    /**
     * Creates a queue that batches all items during the delay window with automatic deduplication.
     * Java-friendly overload accepting a [Consumer].
     *
     * See the overload with suspend function parameter for behavior details.
     *
     * @param action The action to perform for each batch of deduplicated items
     * @return A queue that can be used to submit items via [UpdateQueue.queue]
     */
    fun runBatchedDistinct(action: Consumer<Set<T>>): UpdateQueue<T>
  }

  private class ScopeBuilderImpl<T> private constructor(
    private val scope: CoroutineScope,
    private val name: String,
    private val delay: Duration
  ) : ScopeBuilder<T> {
    companion object {
      fun <T> forScope(scope: CoroutineScope, name: String, delay: Duration): ScopeBuilderImpl<T> {
        return ScopeBuilderImpl(scope, name, delay)
      }
    }

    private var context: CoroutineContext = EmptyCoroutineContext
    private var restartTimerOnAdd: Boolean = false

    override fun withContext(context: CoroutineContext): ScopeBuilderImpl<T> {
      this.context = context
      return this
    }

    override fun withComponentModality(component: JComponent): ScopeBuilderImpl<T> {
      this.context += ModalityState.stateForComponent(component).asContextElement()
      return this
    }

    override fun restartTimerOnAdd(restart: Boolean): ScopeBuilderImpl<T> {
      this.restartTimerOnAdd = restart
      return this
    }

    @JvmSynthetic
    override fun runLatest(action: suspend (T) -> Unit): UpdateQueue<T> {
      return SingleScopeQueue(scope, name, delay, context, restartTimerOnAdd, action)
    }

    override fun runLatest(action: Consumer<T>): UpdateQueue<T> {
      return runLatest { action.accept(it) }
    }

    @JvmSynthetic
    override fun runBatched(action: suspend (List<T>) -> Unit): UpdateQueue<T> {
      return BatchedScopeQueue(scope, name, delay, context, restartTimerOnAdd, action)
    }

    override fun runBatched(action: Consumer<List<T>>): UpdateQueue<T> {
      return runBatched { action.accept(it) }
    }

    @JvmSynthetic
    override fun runBatchedDistinct(action: suspend (Set<T>) -> Unit): UpdateQueue<T> {
      return BatchedDistinctScopeQueue(scope, name, delay, context, restartTimerOnAdd, action)
    }

    override fun runBatchedDistinct(action: Consumer<Set<T>>): UpdateQueue<T> {
      return runBatchedDistinct { action.accept(it) }
    }
  }

  private class ComponentBuilderImpl<T> private constructor(
    private val component: JComponent,
    private val name: String,
    private val delay: Duration
  ) : ComponentBuilder<T> {
    companion object {
      fun <T> forComponent(component: JComponent, name: String, delay: Duration): ComponentBuilderImpl<T> {
        return ComponentBuilderImpl(component, name, delay)
      }
    }

    private var context: CoroutineContext = EmptyCoroutineContext
    private var restartTimerOnAdd: Boolean = false

    override fun withContext(context: CoroutineContext): ComponentBuilderImpl<T> {
      this.context = context
      return this
    }

    override fun restartTimerOnAdd(restart: Boolean): ComponentBuilderImpl<T> {
      this.restartTimerOnAdd = restart
      return this
    }

    @JvmSynthetic
    override fun runLatest(action: suspend (T) -> Unit): UpdateQueue<T> {
      return SingleComponentQueue(component, name, delay, context, restartTimerOnAdd, action)
    }

    override fun runLatest(action: Consumer<T>): UpdateQueue<T> {
      return runLatest { action.accept(it) }
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
   * If the queue is closed (e.g., scope was cancelled, component was removed, or [cancelOnDispose] was triggered),
   * the call is silently ignored and a warning is logged.
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
   * See the overload with [Duration] parameter for detailed behavior documentation.
   *
   * @param timeoutMillis timeout in milliseconds
   * @throws TimeoutException if the timeout is exceeded
   */
  @TestOnly
  @RequiresBackgroundThread
  @Throws(TimeoutException::class)
  fun waitForAllExecuted(timeoutMillis: Long)

  /**
   * Returns `true` when all queued items are processed and no job is currently running.
   *
   * Can be used as an EDT-safe condition with `PlatformTestUtil.waitWithEventsDispatching`:
   * ```kotlin
   * PlatformTestUtil.waitWithEventsDispatching(
   *   "Timed out waiting for queue",
   *   { updateQueue.isAllExecuted },
   *   10
   * )
   * ```
   */
  val isAllExecuted: Boolean

  /**
   * Suspends until all queued items are processed and no job is currently running.
   */
  suspend fun awaitAllExecuted()
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

  // Notifies the processor that a new item was queued, before it is consumed from the main channel.
  protected val notifyChannel: Channel<Unit> = Channel(capacity = channelCapacity)

  protected abstract val job: Job

  // Track the currently running processing job for tests
  protected val processingJob = AtomicReference<Job?>(null)

  // Track whether we're collecting items (between receiving first item and starting processing)
  @Volatile
  protected var isCollecting: Boolean = false

  override fun queue(item: T) {
    if (!job.isActive) {
      logger<DebouncedUpdates>().warn("Ignoring queue() call to cancelled UpdateQueue '$name'")
      return
    }
    val result = channel.trySend(item)
    check(result.isSuccess) { "Failed to send value to channel in UpdateQueue '$name': $result" }

    // Signal after trySend so the item is already in the channel when the receiver wakes up.
    notifyChannel.trySend(Unit)
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
        runBlockingMaybeCancellable {
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

  override val isAllExecuted: Boolean
    get() = !job.isActive || (isEmpty && !isProcessing)

  override suspend fun awaitAllExecuted() {
    while (!isAllExecuted) {
      val currentJob = processingJob.get()
      if (currentJob != null && !currentJob.isCompleted) {
        currentJob.join()
      }
      else {
        delay(10.milliseconds)
      }
    }
  }

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
      // Wait for sync signal that indicates an item was queued
      notifyChannel.receive()

      // Must be set before reading the channel to avoid a window where channel.isEmpty=true and isCollecting=false.
      isCollecting = true

      // Phantom-signal recovery: with channelCapacity=1 + DROP_OLDEST on the data channel, an item that
      // triggered a Unit can be silently overwritten by a concurrent queue() that *also* signals an empty
      // notifyChannel — leaving an extra Unit with no corresponding item. tryReceive lets us detect that
      // here and drop the claim cleanly instead of blocking on channel.receive() with isCollecting latched true.
      val first = channel.tryReceive()
      if (!first.isSuccess) {
        isCollecting = false
        continue
      }
      onReceive(first.getOrThrow())

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
            notifyChannel.receive()
            onReceive(channel.receive())
            lastItemTime = System.nanoTime()
          } ?: break // Timeout - process collected items
        }
      } else {
        // Throttle/sample mode: fixed interval
        delay(delay)

        // Collect all remaining items. Tolerate phantom Units (signals whose corresponding
        // item was silently dropped by a concurrent DROP_OLDEST) — keep draining notifyChannel
        // but skip when no item is available, instead of blocking on channel.receive().
        while (notifyChannel.tryReceive().isSuccess) {
          val next = channel.tryReceive()
          if (next.isSuccess) {
            onReceive(next.getOrThrow())
          }
        }
      }

      // Prepare the data in the current coroutine (ensures happens-before with onReceive)
      val data = onPrepare()

      // Process the data in a separate coroutine
      supervisorScope {
        val job = launch(CoroutineExceptionHandler { _, e -> logger<DebouncedUpdates>().error(e) }) {
          withContext(context) {
            onProcess(data)
          }
        }

        // Must set processingJob before clearing isCollecting to avoid a window where both are false/null.
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
      onPrepare = {
        @Suppress("UNCHECKED_CAST")
        latestItem as T
      },
      onProcess = { item ->
        action(item)
        latestItem = null
      }
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

  /**
   * Process all items as a deduplicated batch (collects all items into a set).
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  protected suspend fun processBatchedDistinct(
    delay: Duration,
    context: CoroutineContext,
    restartTimerOnAdd: Boolean,
    action: suspend (Set<T>) -> Unit
  ) {
    val buffer = HashSet<T>()

    processWithDelay(
      delay = delay,
      context = context,
      restartTimerOnAdd = restartTimerOnAdd,
      onReceive = { buffer.add(it) },
      onPrepare = {
        val batch = buffer.toSet() // Create immutable copy before clearing
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

  // Delivers debounced items to launchOnShow for action execution; DROP_OLDEST keeps the latest
  private val processingChannel = Channel<T>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  // Pending item from canceled action (persists across launchOnShow restarts)
  @Volatile
  private var pendingItem: T? = null

  // Global scope job for channel processing (delay/debouncing)
  @OptIn(DelicateCoroutinesApi::class)
  private val channelProcessingJob: Job = GlobalScope.launch(CoroutineName("$name-channel-processor")) {
    processLatestAndForward(delay, restartTimerOnAdd)
  }

  // Lifetime owner. Hops to EDT before calling launchOnShow, which requires EDT to install a Swing
  // HierarchyListener, so forComponent(...).runLatest(...) can be invoked from any thread.
  @OptIn(DelicateCoroutinesApi::class)
  override val job: Job = GlobalScope.launch(CoroutineName(name)) {
    val actionProcessingJob = withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      component.launchOnShow("$name-action-processor") {
        processFromSecondChannel(context, action)
      }
    }
    try {
      awaitCancellation()
    } finally {
      channelProcessingJob.cancel()
      actionProcessingJob.cancel()
      processingChannel.close()
    }
  }

  /**
   * Process items from the main channel and forward to the processing channel.
   * Runs in global scope, never canceled.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  private suspend fun processLatestAndForward(
    delay: Duration,
    restartTimerOnAdd: Boolean,
  ) {
    var latestItem: T? = null

    processWithDelay(
      delay = delay,
      context = EmptyCoroutineContext,
      restartTimerOnAdd = restartTimerOnAdd,
      onReceive = { latestItem = it },
      onPrepare = {
        @Suppress("UNCHECKED_CAST")
        latestItem as T
      },
      onProcess = { item ->
        processingChannel.send(item)
        latestItem = null
      }
    )
  }

  /**
   * Process items from the second channel and execute the action.
   * Runs inside launchOnShow, so automatically waits for the component to show.
   * Stores the item before processing to ensure it's not lost if action gets canceled.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  private suspend fun processFromSecondChannel(
    context: CoroutineContext,
    action: suspend (T) -> Unit,
  ) {
    // Process pending item only if channel is empty (prioritize newer items)
    if (processingChannel.isEmpty) {
      pendingItem?.let { pending ->
        processItem(pending, context, action)
      }
    }

    // Process new items from the channel
    for (item in processingChannel) {
      processItem(item, context, action)
    }
  }

  private suspend fun processItem(
    item: T,
    context: CoroutineContext,
    action: suspend (T) -> Unit,
  ) {
    pendingItem = item
    try {
      withContext(context) {
        action(item)
      }
      pendingItem = null
    } catch (e: CancellationException) {
      // Component was hidden during action execution
      // Keep pendingItem so we retry when launchOnShow restarts
      throw e
    }
  }
}

/**
 * Batched deduplicating queue bound to a CoroutineScope.
 */
@ApiStatus.Experimental
private class BatchedDistinctScopeQueue<T>(
  scope: CoroutineScope,
  name: String,
  delay: Duration,
  context: CoroutineContext,
  restartTimerOnAdd: Boolean,
  action: suspend (Set<T>) -> Unit
) : BaseUpdateQueue<T>(name, context, channelCapacity = Channel.UNLIMITED) {

  override val job: Job = scope.launch(CoroutineName(name)) {
    processBatchedDistinct(delay, context, restartTimerOnAdd, action)
  }
}
