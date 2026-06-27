// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("NAME_SHADOWING")

package fleet.util.async

import fleet.multiplatform.shims.SynchronizedObject
import fleet.multiplatform.shims.synchronized
import fleet.reporting.shared.tracing.spannedScope
import fleet.tracing.SpanInfoBuilder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalForInheritanceCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

/**
 * Something backed by a coroutine tree.
 *
 * [Resource] is just a recipe that will be used at the time of [use]. Each invocation runs its own coroutine.
 * It is unsafe to use [T] outside of [use], unless explicitly stated otherwise by a provider.
 *
 * The interface is not supposed to be implemented directly, use one of builder functions: [resource], [resourceOf]
 *
 * From technical point of view you can think about it as [kotlinx.coroutines.flow.Flow] of a single element.
 *
 * For example:
 * ```
 *interface Logger {
 *  suspend fun log(msg: String)
 *}
 *
 *fun logger(): Resource<Logger> =
 *  resource { cc ->
 *    val channel = Channel<String>();
 *    launch {
 *      channel.consumeEach { msg ->
 *        println(msg);
 *      }
 *    }
 *    cc(object : Logger {
 *      override suspend fun log(msg: String) {
 *        channel.send(msg)
 *      }
 *    }).also {
 *      channel.close()
 *    }
 *  }
 * ```
 * Notice that logger is not canceled, but shuts down by itself, which allows it to flush all buffered messages.
 * */
interface Resource<out T> {
  suspend fun <U> use(body: suspend CoroutineScope.(T) -> U): U
}

/**
 * Main [Resource] constructor.
 * [producer] must invoke consumer with the resource instance.
 * return type [Consumed], can only be constructed from consumer invocation.
 * [Consumed] is used as a proof of invocation to prevent accidental null-punning and consequent leaked coroutines
 * */
fun <T> resource(producer: suspend CoroutineScope.(consumer: Consumer<T>) -> Consumed): Resource<T> =
  object : Resource<T> {
    override suspend fun <U> use(body: suspend CoroutineScope.(T) -> U): U =
      coroutineScope {
        val deferred = CompletableDeferred<T>()
        val shutdown = Job()
        launch(start = CoroutineStart.UNDISPATCHED) {
          // this is a clever (in a bad sense) way to subscribe to cancelation of the outer coroutine scope
          // invokeOnCompletion will not work because it is triggered only on *completion* which includes completion of all children
          val canary = launch(Dispatchers.Unconfined) { awaitCancellation() }
          // cancellation propagation from parent coroutine will cancel sibling coroutines concurrently
          // coroutines spawned by [producer] are cousins of the coroutines spawned by [body] and thus are not ordered
          // we need to control the cancellation manually to guarantee that [producer] is disposed only after [body] has finished
          withContext(NonCancellable) {
            val resourceScope = this@withContext
            // cancel everything if parent is cancelled while the resource is initializing
            val completionHandler = canary.invokeOnCompletion { ex ->
              if (ex != null) {
                resourceScope.cancel("Propagating exception from parent", ex)
              }
            }
            producer { t ->
              // we are publishing the value, have to make sure [body] is completed before we dispose, so we are unsubscribing from parent
              completionHandler.dispose()
              currentCoroutineContext().job.ensureActive()
              check(deferred.complete(t)) { "Double emission" }
              shutdown.join()
              // now that [body] has finised we are enabling cancellation again
              canary.invokeOnCompletion { ex ->
                if (ex != null) {
                  resourceScope.cancel("Propagating exception from parent", ex)
                }
              }
              currentCoroutineContext().job.ensureActive()
              Proof
            }
          }
          canary.cancel()
        }.apply {
          invokeOnCompletion { ex ->
            deferred.completeExceptionally(ex ?: RuntimeException("Resource didn't emit"))
          }
        }
        try {
          coroutineScope { body(deferred.await()) }
        }
        finally {
          shutdown.complete()
        }
      }
  }

/**
 * A proof that a consumer is invoked
 * */
sealed interface Consumed

internal data object Proof : Consumed

typealias Consumer<T> = suspend (T) -> Consumed

fun <T> resourceOf(value: T): Resource<T> =
  object : Resource<T> {
    override suspend fun <U> use(body: suspend CoroutineScope.(T) -> U): U =
      coroutineScope { body(value) }
  }

fun <T, U> Resource<T>.map(f: suspend (T) -> U): Resource<U> =
  let { source ->
    object : Resource<U> {
      override suspend fun <R> use(body: suspend CoroutineScope.(U) -> R): R =
        source.use { t -> body(f(t)) }
    }
  }

fun <T> Resource<T>.onContext(context: CoroutineContext): Resource<T> =
  let { source ->
    require(context[Job] == null) { "don't" }
    resource { cc -> withContext(context) { source.use { t -> cc(t) } } }
  }

fun <T, U> Resource<T>.flatMap(f: suspend (T) -> Resource<U>): Resource<U> =
  let { source ->
    object : Resource<U> {
      override suspend fun <R> use(body: suspend CoroutineScope.(U) -> R): R =
        source.use { t ->
          f(t).use { u -> body(u) }
        }
    }
  }

fun <T> Resource<Deferred<T>>.track(displayName: String): Resource<Deferred<T>> =
  let { source ->
    object : Resource<Deferred<T>> {
      override suspend fun <U> use(body: suspend CoroutineScope.(Deferred<T>) -> U): U =
        spannedScope("using $displayName") {
          source.use { t ->
            body(t.track(displayName))
          }
        }
    }
  }

fun <T> Resource<T>.span(name: String, info: SpanInfoBuilder.() -> Unit = {}): Resource<T> =
  let { source ->
    resource { cc ->
      spannedScope(name, info) {
        source.use { t ->
          cc(t)
        }
      }
    }
  }

fun <T> Resource<T>.catch(): Resource<Result<T>> =
  let { source ->
    object : Resource<Result<T>> {
      override suspend fun <U> use(body: suspend CoroutineScope.(Result<T>) -> U): U {
        var bodyFailure: Throwable? = null
        return try {
          source.use { t ->
            try {
              coroutineScope { body(Result.success(t)) }
            }
            catch (ex: Throwable) {
              bodyFailure = ex
              throw ex
            }
          }
        }
        catch (ex: Throwable) {
          when (val bodyFailure = bodyFailure) {
            null -> coroutineScope {
              body(Result.failure(ex))
            }
            else -> throw bodyFailure
          }
        }
      }
    }
  }

fun <T> Resource<T>.async(lazy: Boolean = false): Resource<Deferred<T>> =
  let { source ->
    object : Resource<Deferred<T>> {
      override suspend fun <U> use(body: suspend CoroutineScope.(Deferred<T>) -> U): U =
        coroutineScope {
          val d = CompletableDeferred<T>()
          val shutdown = Job()
          val coroutineStart = if (lazy) CoroutineStart.LAZY else CoroutineStart.DEFAULT
          val job = launch(start = coroutineStart) {
            source.use { t ->
              d.complete(t)
              shutdown.join()
            }
          }.apply {
            invokeOnCompletion { cause ->
              d.completeExceptionally(cause ?: RuntimeException("resource didn't emit"))
            }
          }
          val async = async(Dispatchers.Unconfined, start = coroutineStart) {
            job.start()
            d.await()
          }
          coroutineScope { body(async) }.also {
            async.cancel()
            if (!job.isActive) {
              job.cancel()
            }
            else {
              shutdown.complete()
            }
          }
        }
    }
  }

fun <T> Deferred<T>.track(displayName: String): Deferred<T> =
  let { deferred ->
    @OptIn(InternalForInheritanceCoroutinesApi::class)
    object : Deferred<T> by deferred {
      override suspend fun await(): T =
        spannedScope("awaiting $displayName") {
          deferred.await()
        }
    }
  }

fun <T> Resource<T>.useOn(coroutineScope: CoroutineScope): Deferred<T> {
  val deferred = CompletableDeferred<T>()
  coroutineScope.launch(start = CoroutineStart.ATOMIC) {
    use { t ->
      deferred.complete(t)
      awaitCancellation()
    }
  }.invokeOnCompletion { x ->
    deferred.completeExceptionally(x ?: RuntimeException("Resource did not start"))
  }
  return deferred
}

internal sealed interface StopMode {
  data object DoNotStop : StopMode
  data class Stop(val stopTimeout: kotlin.time.Duration, val graceful: Boolean) : StopMode
}

sealed class SharingMode(
  internal val runImmediately: Boolean,
  internal val stopWithoutConsumersMode: StopMode,
) {
  /**
   * The resource starts immediately and remains active until the scope is canceled.
   */
  data object Eager : SharingMode(
    runImmediately = true,
    stopWithoutConsumersMode = StopMode.DoNotStop,
  )

  /**
   * The resource starts when a consumer appears and remains active until the scope is canceled.
   */
  data object Lazy : SharingMode(
    runImmediately = false,
    stopWithoutConsumersMode = StopMode.DoNotStop,
  )

  /**
   * The resource starts when a consumer appears and is gracefully shut down when the last consumer leaves.
   * This cycle may repeat multiple times.
   */
  data class WhileUsed(
    val graceful: Boolean = true,
    /*
     * configures a delay between the disappearance of the last subscriber and the stopping of the sharing coroutine. It defaults to zero (stop immediately).
     */
    val stopTimeout: kotlin.time.Duration = kotlin.time.Duration.ZERO,
  ) : SharingMode(
    runImmediately = false,
    stopWithoutConsumersMode = StopMode.Stop(stopTimeout, graceful),
  )
}

/**
 * A [Resource] that is shared between multiple consumers (see [shareIn]).
 *
 * In addition to the regular [Resource] lifecycle, it can be [stop]ped explicitly.
 */
interface SharedResource<out T> : Resource<T> {
  /**
   * Initiates the shutdown of the shared resource immediately, regardless of any configured stop timeout.
   *
   * After this call the resource is permanently dead: every current consumer is interrupted with a
   * [ResourceStoppedException], and every subsequent [use] fails with the same exception.
   */
  fun stop()
}

class ResourceStoppedException : RuntimeException("Shared resource has been stopped")

/**
 * Runs [Resource] on the given [coroutineScope].
 *
 * The returned [Resource] can be consumed multiple times, the [T] will be shared between them.
 * If the source throws an exception at any point, it will be propagated to the consumers, even if [coroutineScope] is a supervised one.
 *
 * See [SharingMode] for details.
 */
fun <T> Resource<T>.shareIn(coroutineScope: CoroutineScope, sharing: SharingMode = SharingMode.Eager): SharedResource<T> =
  let { source ->
    val initialState: SharedResourceState<T> = when {
      sharing.runImmediately -> SharedResourceState.Running(1, runSharedResource(source, coroutineScope))
      else -> SharedResourceState.NotRunning()
    }
    val stateStore = object : StateStore<T>, StateStore.StateRef<T> {
      private val lock = SynchronizedObject()
      private var state = initialState

      override val source: Resource<T> = source

      override var value: SharedResourceState<T>
        get() = state
        set(value) {
          state = value
        }

      override fun <U> update(f: (StateStore.StateRef<T>) -> U): U =
        synchronized(lock) { f(this) }
    }
    sharedResource(
      coroutineScope = coroutineScope,
      sharing = sharing,
      store = stateStore,
    )
  }

private interface StateStore<T> {
  interface StateRef<T> {
    var value: SharedResourceState<T>
    val source: Resource<T>
  }

  fun <U> update(f: (StateRef<T>) -> U): U
}

private fun <T> sharedResource(
  coroutineScope: CoroutineScope,
  sharing: SharingMode,
  store: StateStore<T>,
): SharedResource<T> {
  // whether an explicit stop lets the resource shut itself down instead of cancelling its coroutine
  val graceful = when (val mode = sharing.stopWithoutConsumersMode) {
    is StopMode.Stop -> mode.graceful
    StopMode.DoNotStop -> true
  }
  val resource = resource<T> { cc ->
    while (true) {
      val r: Pair<SharedResourceState.Running<T>?, Job?> = store.update { state ->
        when (val s = state.value) {
          is SharedResourceState.NotRunning<T> -> {
            SharedResourceState.Running(1, runSharedResource(state.source, coroutineScope)).also { state.value = it } to null
          }
          is SharedResourceState.Running<T> -> {
            s.copy(refCount = s.refCount + 1).also { state.value = it } to null
          }
          is SharedResourceState.Stopping<T> -> {
            when {
              s.finalStop -> throw ResourceStoppedException()
              s.job.isCompleted -> {
                SharedResourceState.Running(1, runSharedResource(state.source, coroutineScope)).also { state.value = it } to null
              }
              else -> {
                null to s.job
              }
            }
          }
          is SharedResourceState.StoppingAfterDelay<T> -> {
            s.timeoutCoroutine.cancel()
            SharedResourceState.Running(1, s.running).also { state.value = it } to null
          }
          is SharedResourceState.Stopped<T> -> {
            throw ResourceStoppedException()
          }
        }
      }
      val (running, obstacle) = r
      when {
        obstacle != null -> obstacle.join()
        running != null -> {
          // break the loop
          return@resource try {
            running.runnning.use(cc)
          }
          finally {
            store.update { state ->
              when (val s = state.value) {
                // an explicit, final stop tore the resource down while we were still using it: nothing left to release
                is SharedResourceState.Stopped<*> -> Unit
                is SharedResourceState.Stopping<*> -> {
                  check(s.finalStop) { "we are not yet done with the resource, yet it is not running" }
                }
                is SharedResourceState.NotRunning<*>, is SharedResourceState.StoppingAfterDelay<*> -> {
                  error("we are not yet done with the resource, yet it is not running")
                }
                is SharedResourceState.Running<T> -> {
                  val next = if (s.refCount == 1) {
                    when (val mode = sharing.stopWithoutConsumersMode) {
                      is StopMode.Stop -> {
                        if (mode.stopTimeout == Duration.ZERO) {
                          s.runnning.shutDown(mode.graceful)
                          SharedResourceState.Stopping<T>(
                            job = s.runnning.job,
                            finalStop = false,
                          )
                        }
                        else {
                          val marker = Any()
                          SharedResourceState.StoppingAfterDelay(
                            running = s.runnning,
                            marker = marker,
                            timeoutCoroutine = coroutineScope.launch(start = CoroutineStart.ATOMIC) {
                              delay(mode.stopTimeout)
                              store.update { state ->
                                val s = state.value
                                if (s is SharedResourceState.StoppingAfterDelay<*> && s.marker == marker) {
                                  s.running.shutDown(mode.graceful)
                                  val stopping = SharedResourceState.Stopping<T>(
                                    job = s.running.job,
                                    finalStop = false,
                                  )
                                  state.value = stopping
                                  scheduleEvictionAfterStop(store, stopping)
                                }
                              }
                            }
                          )
                        }
                      }
                      StopMode.DoNotStop -> {
                        s.copy(refCount = 0)
                      }
                    }
                  }
                  else {
                    s.copy(refCount = s.refCount - 1)
                  }
                  state.value = next
                  // the state must be assigned before scheduling, so the (possibly synchronous) completion handler observes it
                  if (next is SharedResourceState.Stopping<*>) {
                    scheduleEvictionAfterStop(store, next)
                  }
                }
              }
            }
          }
        }
        else -> error("unreachable")
      }
    }
    error("unreachable")
  }
  return object : SharedResource<T> {
    override suspend fun <U> use(body: suspend CoroutineScope.(T) -> U): U = resource.use(body)

    override fun stop() {
      stopShared(store, graceful, ResourceStoppedException())
    }
  }
}

/**
 * Initiates the final shutdown of a shared resource, immediately and regardless of any remaining stop timeout.
 */
private fun <T> stopShared(store: StateStore<T>, graceful: Boolean, cause: ResourceStoppedException) {
  store.update { state ->
    when (val current = state.value) {
      is SharedResourceState.NotRunning<T> -> state.value = SharedResourceState.Stopped()
      is SharedResourceState.Stopped<T> -> Unit
      is SharedResourceState.Running<T> -> {
        initiateStop(store, state, current.runnning, graceful, cause)
      }
      is SharedResourceState.StoppingAfterDelay<T> -> {
        current.timeoutCoroutine.cancel()
        initiateStop(store, state, current.running, graceful, cause)
      }
      is SharedResourceState.Stopping<T> -> {
        if (!current.finalStop) {
          // a regular (restartable) shutdown is already in progress; promote it to a final one over the same job.
          val stopping = SharedResourceState.Stopping<T>(current.job, finalStop = true)
          state.value = stopping
          // publishing a fresh state turns the previously registered eviction handler into a no-op (=== fails).
          scheduleEvictionAfterStop(store, stopping)
        }
      }
    }
  }
}

private fun <T> initiateStop(
  store: StateStore<T>,
  state: StateStore.StateRef<T>,
  running: HotResource<T>,
  graceful: Boolean,
  cause: ResourceStoppedException,
) {
  val stopping = SharedResourceState.Stopping<T>(running.job, finalStop = true)
  // publish the final state before touching the resource: the eviction handler (and a consumer that resumes
  // synchronously once we interrupt it below) must observe Stopping rather than the now-stale Running state
  state.value = stopping
  scheduleEvictionAfterStop(store, stopping)
  running.stop(graceful, cause)
}

/**
 * Once the [stopping] resource's job has fully terminated, transitions the state away from it,
 * but only if it is still the very same [stopping] instance (a returning consumer may have restarted it in the meantime).
 *
 * A regular stop is evicted back to [SharedResourceState.NotRunning] so it can be restarted later; a
 * [final][SharedResourceState.Stopping.finalStop] stop is evicted to the terminal [SharedResourceState.Stopped].
 *
 * The completion handler may run synchronously if the job is already complete; callers must publish [stopping]
 * into the store before invoking this so the handler observes it. The store lock is reentrant, so a synchronous run is safe.
 */
private fun <T> scheduleEvictionAfterStop(store: StateStore<T>, stopping: SharedResourceState.Stopping<*>) {
  stopping.job.invokeOnCompletion {
    store.update { state ->
      if (state.value === stopping) {
        state.value = if (stopping.finalStop) SharedResourceState.Stopped() else SharedResourceState.NotRunning()
      }
    }
  }
}

private sealed interface SharedResourceState<T> {
  class NotRunning<T> : SharedResourceState<T>
  data class Running<T>(val refCount: Int, val runnning: HotResource<T>) : SharedResourceState<T>
  data class Stopping<T>(val job: Job, val finalStop: Boolean) : SharedResourceState<T>
  data class StoppingAfterDelay<T>(
    val running: HotResource<T>,
    val timeoutCoroutine: Job,
    val marker: Any,
  ) : SharedResourceState<T>

  /**
   * Terminal state reached via [SharedResource.stop]. The resource will never run again, and any current or new
   * consumer fails with [ResourceStoppedException].
   */
  class Stopped<T> : SharedResourceState<T>
}

private class HotResource<T>(
  val termination: CompletableJob,
  val job: Job,
  val failure: CompletableDeferred<Nothing>,
  val value: CompletableDeferred<T>,
) {
  @OptIn(ExperimentalCoroutinesApi::class)
  suspend fun use(cc: Consumer<T>): Consumed {
    return coroutineScope {
      if (failure.isCompleted) {
        failure.getCompletionExceptionOrNull()?.let { throw it }
      }
      val body = async(start = CoroutineStart.UNDISPATCHED) {
        cc(value.await())
      }
      select {
        failure.onAwait { it }
        job.onJoin { error("outlived shared resource. using it out of scope?") }
        body.onAwait { it }
      }
    }
  }

  /**
   * Shuts the resource down: when [graceful] it is asked to stop by itself , otherwise its coroutine
   * is cancelled. This is the same teardown a regular stop performs once the last consumer leaves.
   */
  fun shutDown(graceful: Boolean) {
    if (!graceful) {
      job.cancel()
    }
    termination.complete()
  }

  /**
   * A [shutDown] that additionally interrupts every live consumer with [cause]. Used for an explicit, final stop.
   */
  fun stop(graceful: Boolean, cause: Throwable) {
    // complete the failure first so a live consumer's select observes it rather than the coroutine completion
    failure.completeExceptionally(cause)
    value.completeExceptionally(cause)
    shutDown(graceful)
  }
}

private fun <T> runSharedResource(source: Resource<T>, coroutineScope: CoroutineScope): HotResource<T> =
  run {
    val value = CompletableDeferred<T>()
    val termination = Job()
    val failure = CompletableDeferred<Nothing>()
    val job = coroutineScope.launch(start = CoroutineStart.ATOMIC) {
      try {
        source.use { t ->
          value.complete(t)
          termination.join()
        }
      }
      catch (ex: Throwable) {
        failure.completeExceptionally(ex)
        value.completeExceptionally(ex)
        throw ex
      }
    }
    HotResource(
      termination = termination,
      job = job,
      failure = failure,
      value = value,
    )
  }


class ResourceCache<K, R> internal constructor(
  private val scope: CoroutineScope,
  private val graceful: Boolean,
  private val stopTimeout: Duration,
  private val factory: (K) -> Resource<R>,
) {
  private val lock = SynchronizedObject()
  private val map = HashMap<K, SharedResourceState<R>>()

  /**
   * Keys currently retained in the internal map.
   * Exposed for diagnostics and tests: an entry must not linger once its resource is no longer used by anyone.
   */
  fun retainedKeys(): Set<K> = synchronized(lock) { map.keys.toHashSet() }

  /**
   * Stops every resource currently retained by this cache, immediately and regardless of any remaining stop timeout.
   * The configured [graceful] flag is respected (see [SharedResource.stop]).
   */
  internal fun stopAll() {
    synchronized(lock) {
      val cause = ResourceStoppedException()
      for (key in map.keys.toHashSet()) {
        stopShared(keyStore(key), graceful, cause)
      }
    }
  }

  fun get(key: K): Resource<R> {
    val sharing = SharingMode.WhileUsed(
      graceful = graceful,
      stopTimeout = stopTimeout,
    )
    // every client gets their own instance, but they share the same per-key state
    return sharedResource(scope, sharing, keyStore(key))
  }

  /**
   * A [StateStore] view over [map] for a single [key]. All views share [lock], so operations on the same key are
   * serialized, while a [SharedResourceState.NotRunning] value evicts the entry entirely.
   */
  private fun keyStore(key: K): StateStore<R> =
    object : StateStore<R>, StateStore.StateRef<R> {
      override val source: Resource<R> get() = factory(key)
      override var value: SharedResourceState<R>
        get() = map[key] ?: SharedResourceState.NotRunning()
        set(value) {
          if (value is SharedResourceState.NotRunning) {
            map.remove(key)
          }
          else {
            map[key] = value
          }
        }

      override fun <U> update(f: (StateStore.StateRef<R>) -> U): U =
        synchronized(lock) { f(this) }
    }
}

fun <K, R> resourceCache(
  graceful: Boolean = true,
  stopTimeout: Duration = Duration.ZERO,
  f: (K) -> Resource<R>,
): Resource<ResourceCache<K, R>> =
  resource { cc ->
    supervisorScope {
      val scope = this
      val cache = ResourceCache(
        scope = scope,
        graceful = graceful,
        stopTimeout = stopTimeout,
        factory = f,
      )
      try {
        cc(cache)
      }
      finally {
        // the cache is being disposed: resources are no longer used, but some might still linger because of the stopTimeout.
        // shut all of them down explicitly, regardless of the remaining stopTimeout.
        cache.stopAll()
      }
    }
  }
