// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.observation.ActivityTracker
import com.intellij.platform.backend.observation.removeObservedComputation
import com.intellij.platform.backend.observation.traceObservedComputation
import com.intellij.util.ui.update.MergingUpdateQueueTracker
import com.intellij.util.ui.update.Update
import kotlinx.coroutines.delay
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

@ApiStatus.Internal
class MergingUpdateQueueActivityTracker : ActivityTracker {
  override val presentableName: String
    get() = "Queries for MergingUpdateQueue"

  override suspend fun isInProgress(project: Project): Boolean {
    return (ApplicationManager.getApplication().serviceAsync<MergingUpdateQueueTracker>() as MergingUpdateQueueTrackerImpl).hasTrackedUpdates()
  }

  override suspend fun awaitConfiguration(project: Project) {
    while (isInProgress(project)) {
      delay(100.milliseconds)
    }
  }
}

private class MergingUpdateQueueTrackerImpl : MergingUpdateQueueTracker {

  private val counter = AtomicInteger(0)

  fun hasTrackedUpdates(): Boolean {
    return counter.get() > 0 // if true, someone queued a task
  }

  override fun trackUpdate(update: Update): Update {
    return TrackedUpdate(update)
  }

  fun registerEnter(update: TrackedUpdate) {
    counter.incrementAndGet()
    traceObservedComputation(update.id)
  }

  fun registerExit(update: TrackedUpdate) {
    removeObservedComputation(update.id)
    counter.decrementAndGet()
  }
}

private class TrackedUpdate(
  private val original: Update,
) : Update(original) {

  /**
   * The equals and hashcode methods are overridden in the [Update] class.
   * Therefore, we need an additional unique identifier of this update for debug tracing.
   */
  val id = Any()

  // we have to delegate ALL overrideable methods because we don't know which ones are overridden in the original Update
  // also Update is an abstract class, so we cannot use Kotlin Delegation
  override val isDisposed: Boolean get() = original.isDisposed
  override val isExpired: Boolean get() = original.isExpired
  override fun wasProcessed(): Boolean = original.wasProcessed()
  override fun setProcessed(): Unit = original.setProcessed()
  override val executeInWriteAction: Boolean get() = original.executeInWriteAction
  override val isRejected: Boolean get() = original.isRejected
  override fun getEqualityObjects(): Array<Any> = original.equalityObjects

  override fun canEat(update: Update): Boolean {
    val unwrappedUpdate = (update as? TrackedUpdate)?.original ?: update
    return original.canEat(unwrappedUpdate)
  }

  override fun setRejected() {
    (ApplicationManager.getApplication().service<MergingUpdateQueueTracker>() as MergingUpdateQueueTrackerImpl).registerExit(this)
    original.setRejected()
  }

  override fun run() {
    try {
      original.run()
    }
    finally {
      (ApplicationManager.getApplication().service<MergingUpdateQueueTracker>() as MergingUpdateQueueTrackerImpl).registerExit(this)
    }
  }

  init {
    (ApplicationManager.getApplication().service<MergingUpdateQueueTracker>() as MergingUpdateQueueTrackerImpl).registerEnter(this)
  }
}