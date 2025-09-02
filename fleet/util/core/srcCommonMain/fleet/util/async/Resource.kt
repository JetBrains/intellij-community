// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("NAME_SHADOWING")

package fleet.util.async

import fleet.multiplatform.shims.synchronized
import fleet.reporting.shared.tracing.spannedScope
import fleet.tracing.SpanInfoBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import kotlin.coroutines.CoroutineContext

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
        // isolate consumers from coroutine context potentially polluted by the resource
        launch(start = CoroutineStart.UNDISPATCHED) {
          producer { t ->
            check(deferred.complete(t)) { "Double emission" }
            shutdown.join()
            Proof
          }
        }.invokeOnCompletion { cause ->
          deferred.completeExceptionally(cause ?: RuntimeException("Resource didn't emit"))
        }
        coroutineScope { body(deferred.await()) }.also { shutdown.complete() }
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
    object : Deferred<T> by deferred {
      override suspend fun await(): T =
        spannedScope("awaiting $displayName") {
          deferred.await()
        }
    }
  }

@OptIn(ExperimentalCoroutinesApi::class)
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

enum class StopMode {
  NOPE, STOP, CANCEL
}

sealed class SharingMode(
  val runImmediately: Boolean,
  val stopWithoutConsumersMode: StopMode,
) {
  /**
   * The resource starts immediately and remains active until the scope is canceled.
   */
  data object Eager : SharingMode(runImmediately = true, stopWithoutConsumersMode = StopMode.NOPE)

  /**
   * The resource starts when a consumer appears and remains active until the scope is canceled.
   */
  data object Lazy : SharingMode(runImmediately = false, stopWithoutConsumersMode = StopMode.NOPE)

  /**
   * The resource starts when a consumer appears and is gracefully shut down when the last consumer leaves.
   * This cycle may repeat multiple times.
   */
  data class WhileUsed(val graceful: Boolean = true) : SharingMode(runImmediately = false,
                                                                   stopWithoutConsumersMode = if (graceful) StopMode.STOP else StopMode.CANCEL)
}

/**
 * Runs [Resource] on the given [coroutineScope].
 *
 * The returned [Resource] can be consumed multiple times, the [T] will be shared between them.
 * If the source throws an exception at any point, it will be propagated to the consumers, even if [coroutineScope] is a supervised one.
 *
 * See [SharingMode] for details.
 */
fun <T> Resource<T>.shareIn(coroutineScope: CoroutineScope, sharing: SharingMode = SharingMode.Eager): Resource<T> =
  let { source ->
    val lock = Any()
    var state: SharedResourceState<T> = when {
      sharing.runImmediately -> SharedResourceState.Running(1, runSharedResource(source, coroutineScope))
      else -> SharedResourceState.NotRunning()
    }
    resource { cc ->
      while (coroutineContext.isActive) {
        val r: Pair<SharedResourceState.Running<T>?, Job?> = synchronized(lock) {
          when (val s = state) {
            is SharedResourceState.NotRunning<T> -> {
              SharedResourceState.Running(1, runSharedResource(source, coroutineScope)).also { state = it } to null
            }
            is SharedResourceState.Running<T> -> {
              s.copy(refCount = s.refCount + 1).also { state = it } to null
            }
            is SharedResourceState.Stopping<T> -> {
              if (s.job.isCompleted) {
                SharedResourceState.Running(1, runSharedResource(source, coroutineScope)).also { state = it } to null
              }
              else {
                null to s.job
              }
            }
          }
        }
        val (running, obstacle) = r
        when {
          obstacle != null -> obstacle.join()
          running != null -> {
            return@resource try {
              running.runnning.use(cc)
            }
            finally {
              synchronized(lock) {
                when (val s = state) {
                  is SharedResourceState.Stopping<*>, is SharedResourceState.NotRunning<*> -> error("we are not yet done with the resource, yet it is not running")
                  is SharedResourceState.Running<T> -> {
                    state = if (s.refCount == 1) {
                      when (sharing.stopWithoutConsumersMode) {
                        StopMode.STOP -> {
                          s.runnning.termination.complete()
                          SharedResourceState.Stopping(s.runnning.job)
                        }
                        StopMode.CANCEL -> {
                          s.runnning.job.cancel()
                          s.runnning.termination.complete()
                          SharedResourceState.Stopping(s.runnning.job)
                        }
                        StopMode.NOPE -> {
                          s.copy(refCount = 0)
                        }
                      }
                    }
                    else {
                      s.copy(refCount = s.refCount - 1)
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
  }

private sealed interface SharedResourceState<T> {
  class NotRunning<T> : SharedResourceState<T>
  data class Running<T>(val refCount: Int, val runnning: HotResource<T>) : SharedResourceState<T>
  data class Stopping<T>(val job: Job) : SharedResourceState<T>
}

private class HotResource<T>(
  val termination: CompletableJob,
  val job: Job,
  val failure: Deferred<Nothing>,
  val value: Deferred<T>,
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
}

@OptIn(ExperimentalCoroutinesApi::class)
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
