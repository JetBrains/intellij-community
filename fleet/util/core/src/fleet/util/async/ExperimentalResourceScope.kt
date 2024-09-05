// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.async

import fleet.tracing.spannedScope
import kotlinx.coroutines.*
import java.lang.RuntimeException
import java.util.concurrent.ConcurrentHashMap

@DslMarker
annotation class ResourceDsl

@ResourceDsl
sealed interface ExperimentalResourceScope : CoroutineScope {
  suspend fun <T> Shared<T>.await(): T

  /**
   * Launces a coroutine that will collect resource returned by [body].
   * Coroutine will be launched on [this], it will inherit coroutine context and become a child of this scope (which might prevent it from shutting down).
   * [ShareScope] allows one to establish dependencies on other shared resources so that we know in which order to shut them down.
   * */
  fun <T> CoroutineScope.share(debugName: String? = null, body: ShareScope.() -> ExperimentalResource<T>): Shared<T>

  /**
   * Simplistic shortcut to register a job for cancellation on shutdown of this [ExperimentalResourceScope].
   * Cancellation will be triggered **after** all coroutines launched with [share].
   * */
  fun <T : Job> T.cancelOnExit(): T
  fun shutdown()
}

// at the moment of shutdown we will complete this deferred with a set of jobs to wait for
private typealias ShutdownSignal = CompletableDeferred<Set<Job>>

private class ResourceScopeImpl(scope: CoroutineScope) : ExperimentalResourceScope, CoroutineScope by scope {
  private val shareds = ConcurrentHashMap.newKeySet<Shared<*>>()
  private val cancelOnExit = ConcurrentHashMap.newKeySet<Job>()

  override suspend fun <T> Shared<T>.await(): T =
    let { shared ->
      spannedScope("awaiting ${shared.debugName}") {
        shared.deferred.await()
      }
    }

  override fun <T> CoroutineScope.share(debugName: String?, body: ShareScope.() -> ExperimentalResource<T>): Shared<T> =
    let { scope ->
      val dependencies = hashSetOf<CompletableDeferred<Set<Job>>>()
      val resource = object : ShareScope {
        override fun <T> Shared<T>.require(): Deferred<T> =
          let { dependency ->
            dependency.deferred.also {
              // if dependency is launced outside of current resource scope, we should not bother with dependency tracking
              if (dependency.resourceScope == this@ResourceScopeImpl) {
                dependencies.add(dependency.shutdownSignal)
              }
            }
          }
      }.run(body)
      val deferred = CompletableDeferred<T>()
      val shutdownSignal = CompletableDeferred<Set<Job>>()
      val job = scope.launch {
        resource.collect { t ->
          deferred.complete(t)
          for (dep in shutdownSignal.await()) {
            dep.join()
          }
        }
      }.apply {
        invokeOnCompletion { cause ->
          if (!deferred.isCompleted) {
            deferred.completeExceptionally(cause ?: RuntimeException("unreachable"))
          }
        }
      }

      val shared = Shared(
        resourceScope = this@ResourceScopeImpl,
        deferred = deferred,
        shutdownSignal = shutdownSignal,
        job = job,
        dependencies = dependencies,
        debugName = debugName,
      )
      shareds.add(shared)
      return shared
    }

  override fun <T : Job> T.cancelOnExit(): T =
    also { job ->
      cancelOnExit.add(job)
      job.invokeOnCompletion { cancelOnExit.remove(job) }
    }

  override fun shutdown() {
    val shutdownOrder = buildMap<CompletableDeferred<Set<Job>>, MutableSet<Job>> {
      shareds.forEach { shared ->
        // schedule shutdown even if shared has no dependencies
        getOrPut(shared.shutdownSignal) { mutableSetOf() }
        // each dependency must wait for completion of our job
        shared.dependencies.forEach { dep ->
          getOrPut(dep) { mutableSetOf() }.add(shared.job)
        }
      }
    }
    shutdownOrder.forEach { (shutdownSignal, jobsToWaitFor) ->
      shutdownSignal.complete(jobsToWaitFor)
    }
    for (job in cancelOnExit) {
      job.cancel()
    }
  }
}

/**
 * EXPERIMENTAL NOT SAFE TO USE
 * */
suspend fun <T> resourceScope(body: suspend ExperimentalResourceScope.() -> T): T =
  coroutineScope {
    ResourceScopeImpl(this).run { body() }
  }

class Shared<T> internal constructor(
  internal val resourceScope: ExperimentalResourceScope,
  internal val deferred: Deferred<T>,
  // will be completed with dependency set at the moment of shutdown
  internal val shutdownSignal: ShutdownSignal,
  internal val job: Job,
  // shareds that have to wait for us
  internal val dependencies: HashSet<ShutdownSignal>,
  internal val debugName: String?,
)

@ResourceDsl
interface ShareScope {
  fun <T> Shared<T>.require(): Deferred<T>
}

fun <T> ShareScope.require(dependency: Shared<T>): Deferred<T> = dependency.require()


class ExperimentalResource<T> {
  /**
   * Marker interface to prevent resource-related receivers from leaking into resource body.
   * */
  @ResourceDsl
  interface Scope : CoroutineScope

  private val producer: suspend (Consumer<T>) -> Unit

  constructor(value: T) {
    producer = { consumer -> consumer(value) }
  }

  /**
   * [body] **must** call given [Consumer] exactly once, failure to do so will result in exception.
   * [Consumer] will suspend for the time the resource is in use. Once it returns it is time to perform shutdown.
   * [body] is responsible for completion of all spawned jobs, they will not be cancelled automatically.
   * */
  constructor(body: suspend Scope.(Consumer<T>) -> Unit) {
    producer = { consumer: Consumer<T> ->
      var emitted = false
      coroutineScope {
        object : Scope, CoroutineScope by this {}
          .run {
            body { t ->
              require(!emitted) { "double emission" }
              emitted = true
              consumer(t)
            }
          }
      }
    }
  }

  companion object {
    private val NONE = Any()
  }

  suspend fun <U> use(body: suspend CoroutineScope.(Deferred<T>) -> U): U =
    coroutineScope {
      val deferredResource = CompletableDeferred<T>()
      val shutdown = Job()
      // TODO should it be cancellable? optionally cancellable?
      launch(start = CoroutineStart.UNDISPATCHED) {
        producer { t ->
          deferredResource.complete(t)
          shutdown.join()
        }
      }.invokeOnCompletion {
        deferredResource.completeExceptionally(RuntimeException("Resource didn't emit"))
      }
      coroutineScope { body(deferredResource) }.also { shutdown.complete() }
    }

  fun <T> CoroutineScope.launch(): Deferred<T> = TODO("cancellable? Handle with stop?")
}

suspend fun <T, U> ExperimentalResource<T>.collect(body: suspend CoroutineScope.(T) -> U): U = use { d -> body(d.await()) }
