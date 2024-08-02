// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.observation

import com.intellij.concurrency.IntelliJContextElement
import com.intellij.concurrency.currentThreadContext
import com.intellij.concurrency.installThreadContext
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Allows tracking subsystem activities and getting a "dumb mode" with respect to tracked computations.
 * @see ActivityKey for high-level explanations
 */
@Service(Service.Level.PROJECT)
@Internal
internal class PlatformActivityTrackerService(private val scope: CoroutineScope) {

  companion object {
    suspend fun getInstanceAsync(project: Project): PlatformActivityTrackerService = project.serviceAsync<PlatformActivityTrackerService>()
    fun getInstance(project: Project): PlatformActivityTrackerService = project.service<PlatformActivityTrackerService>()
  }

  private class AssociatedCounter(
    val counter: Int,
    // the job here is an imitation of java.util.concurrent.Phaser
    val job: CompletableJob,
    // the throwable here are for debugging
    val creationTrace: Map<Any, Throwable>,
    ) {
    override fun equals(other: Any?): Boolean {
      return other is AssociatedCounter && this.counter == other.counter && this.job === other.job
    }

    override fun hashCode(): Int {
      return this.counter + this.job.hashCode()
    }
  }

  /**
   * The number of all configuration processes ongoing.
   *
   * Contract: subsystem has no configurations ongoing (i.e. the counter of configuration is 0) <=> there is no corresponding key-value in the map,
   * because we need to avoid memory leaks and problems with dynamic plugin unloading.
   */
  private val concurrentConfigurationCounter: ConcurrentHashMap<ActivityKey, AssociatedCounter> = ConcurrentHashMap()

  private val ongoingConfigurationFlow : MutableStateFlow<Boolean> = MutableStateFlow(false)
  private val flowCounter = AtomicInteger(0)

  internal val configurationFlow : StateFlow<Boolean> = ongoingConfigurationFlow

  /**
   * Installs a tracker for a suspending asynchronous activity of [action].
   * This method is cheap to use: it does not perform any complex computations, and it is essentially equivalent to `withContext`.
   */
  suspend fun <T> trackConfigurationActivity(kind: ActivityKey, action: suspend () -> T): T {
    return withObservationTracker(kind) { observationTracker ->
      withContext(observationTracker) {
        action()
      }
    }
  }

  /**
   * Installs a tracker for a blocking asynchronous activity of [action].
   * This method is cheap to use: it does not add any synchronization or complex computations.
   */
  @RequiresBlockingContext
  fun <T> trackConfigurationActivityBlocking(kind: ActivityKey, action: () -> T): T {
    val currentContext = currentThreadContext()
    return withObservationTracker(kind) { observationTracker ->
      installThreadContext(currentContext + observationTracker, true).use {
        action()
      }
    }
  }


  private inline fun <T> withObservationTracker(kind: ActivityKey, consumer: (ObservationTracker) -> T): T {
    val key = enterConfiguration(kind)
    // new job here to track those and only those computations which are invoked under explicit `trackConfigurationActivity`
    val tracker = Job()
    tracker.invokeOnCompletion {
      leaveConfiguration(kind, key)
    }
    val trackingElement = ObservationTracker(tracker, tracker)
    try {
      return consumer(trackingElement)
    }
    finally {
      scope.launch {
        tracker.children.forEach { it.join() }
      }.invokeOnCompletion {
        tracker.complete()
      }
    }
  }


  internal class ObservationTracker(private val mainJob: Job, val currentJob: CompletableJob) : AbstractCoroutineContextElement(Key), IntelliJContextElement {
    companion object Key : CoroutineContext.Key<ObservationTracker>

    override fun produceChildElement(oldContext: CoroutineContext, isStructured: Boolean): IntelliJContextElement {
      // we would like to know about all child computations, regardless of their relation to the current process
      val newJob = Job(mainJob)
      if (Registry.`is`("ide.activity.tracking.enable.debug", false)) {
        computationMap[newJob] = Throwable()
      }
      return ObservationTracker(mainJob, newJob)
    }

    override fun afterChildCompleted(context: CoroutineContext) {
      computationMap.remove(currentJob)
      currentJob.complete()
    }
  }

  private fun enterConfiguration(kind: ActivityKey) : Any {
    val sentinel = Any()
    while (true) {
      // compare-and-swap, basically
      val insertionResult = concurrentConfigurationCounter.putIfAbsent(kind, AssociatedCounter(1, Job(), mapOf(sentinel to Throwable())))
      if (insertionResult == null) {
        // successfully inserted
        break
      }
      else {
        val incrementedCounter = AssociatedCounter(insertionResult.counter + 1, insertionResult.job, insertionResult.creationTrace + (sentinel to Throwable()))
        if (concurrentConfigurationCounter.replace(kind, insertionResult, incrementedCounter)) {
          break
        }
      }
    }
    val counter = flowCounter.getAndIncrement()
    if (counter == 0) {
      while (!ongoingConfigurationFlow.compareAndSet(false, true)) {
        // The loop should not spin for long.
        // Suppose we have two activities: the first one is fast, and the second one is slow.
        // CAS may fail only if slow activity sets itself before the fast one,
        // but in this case the ending of the fast activity would set `false` back, and fast activity would be able to set `true` again.
        // This can cause a blink, but it is technically correct.
      }
    }
    return sentinel
  }

  private fun leaveConfiguration(kind: ActivityKey, key: Any) {
    while (true) {
      // compare-and-swap, basically
      val currentCounter = concurrentConfigurationCounter[kind]
      if (currentCounter == null) {
        // while the configuration is active, no one has right to remove the key from the map
        thisLogger().error("Configuration tracker is corrupted.")
        return
      }
      val operationSucceeded = if (currentCounter.counter == 1) {
        // attempt to remove key
        val counterRemoved = concurrentConfigurationCounter.remove(kind, currentCounter)
        if (counterRemoved) {
          // no one can subscribe to the job anymore, and it cannot be resurrected
          currentCounter.job.complete()
        }
        counterRemoved
      }
      else {
        // attempt to decrease key
        val newCounter = AssociatedCounter(currentCounter.counter - 1, currentCounter.job, currentCounter.creationTrace - key)
        concurrentConfigurationCounter.replace(kind, currentCounter, newCounter)
      }
      if (operationSucceeded) {
        break
      }
    }
    val counter = flowCounter.decrementAndGet()
    if (counter == 0) {
      while (!ongoingConfigurationFlow.compareAndSet(true, false)) {
        // See the comment in a similar loop of `enterConfiguration`
      }
    }
  }

  fun getAllKeys(): Set<ActivityKey> {
    return concurrentConfigurationCounter.keys
  }

  fun isInProgress(kind: ActivityKey): Boolean {
    // see the contract of [concurrentConfigurationCounter]
    return concurrentConfigurationCounter[kind] != null
  }

  @Suppress("IfThenToSafeAccess")
  suspend fun awaitConfiguration(kind: ActivityKey) {
    val currentCounter = concurrentConfigurationCounter[kind]
    if (currentCounter != null) {
      // currentCounter != null => isInProgress == true => either currentCounter.job corresponds to the current configuration process,
      // or its configuration was completed earlier, and in this case join() will immediately return
      // currentCounter == null => isInProgress == false => immediately return, since no configuration process is here currently
      currentCounter.job.join()
    }
  }
}

private val computationMap : MutableMap<Job, Throwable?> = ConcurrentHashMap()

internal fun dumpCurrentlyObservedComputations(): Set<Throwable> {
  return computationMap.values.mapNotNullTo(HashSet()) { it }
}
