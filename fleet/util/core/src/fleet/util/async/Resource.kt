// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("NAME_SHADOWING")

package fleet.util.async

import fleet.tracing.SpanInfoBuilder
import fleet.tracing.spannedScope
import kotlinx.coroutines.*
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
 *    })
 *    channel.close()
 *  }
 * ```
 * Notice that logger is not cancelled, but shuts down by itself, which allows it to flush all buffered messages.
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

internal data object Proof: Consumed

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

fun <T> Resource<T>.async(cancellable: Boolean = false): Resource<Deferred<T>> =
  let { source ->
    object : Resource<Deferred<T>> {
      override suspend fun <U> use(body: suspend CoroutineScope.(Deferred<T>) -> U): U =
        coroutineScope {
          val d = CompletableDeferred<T>()
          val shutdown = Job()
          val job = launch {
            source.use { t ->
              d.complete(t)
              shutdown.join()
            }
          }.apply {
            invokeOnCompletion { cause ->
              d.completeExceptionally(cause ?: RuntimeException("resource didn't emit"))
            }
          }
          coroutineScope { body(d) }.also { if (cancellable) job.cancel() else shutdown.complete() }
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