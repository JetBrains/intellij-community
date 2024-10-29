// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("MergingQueueUtil")

package com.intellij.util.ui.update

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Queues an [Update] and additionally notifies the platform about its completion
 *
 * This behavior is necessary when [MergingUpdateQueue] is used for the control-flow reason,
 * and the platform may be interested in undelivered updates.
 * An example is project configuration process, where configuration starts via a [MergingUpdateQueue],
 * hence the platform needs to know about all undelivered updates to proper track the configuration of a project.
 */
fun MergingUpdateQueue.queueTracked(update: Update) {
  ApplicationManager.getApplication().service<MergingUpdateQueueTracker>().registerEnter()
  queue(TrackedUpdate(update))
}

@Internal
interface MergingUpdateQueueTracker {
  fun registerEnter()
  fun registerExit()
}

private class TrackedUpdate(
  private val original: Update,
) : Update(original) {

  // we have to delegate ALL overrideable methods because we don't know which ones are overridden in the original Update
  // also Update is an abstract class, so we cannot use Kotlin Delegation

  override val isDisposed: Boolean
    get() = original.isDisposed
  override val isExpired: Boolean
    get() = original.isExpired
  override fun wasProcessed(): Boolean = original.wasProcessed()
  override fun setProcessed() = original.setProcessed()

  override val executeInWriteAction: Boolean
    get() = original.executeInWriteAction

  override val isRejected: Boolean
    get() = original.isRejected

  override fun getEqualityObjects(): Array<Any> = original.equalityObjects

  override fun canEat(update: Update): Boolean {
    val unwrappedUpdate = (update as? TrackedUpdate)?.original ?: update
    return original.canEat(unwrappedUpdate)
  }

  override fun setRejected() {
    ApplicationManager.getApplication().service<MergingUpdateQueueTracker>().registerExit()
    original.setRejected()
  }

  override fun run() {
    try {
      original.run()
    } finally {
      ApplicationManager.getApplication().service<MergingUpdateQueueTracker>().registerExit()
    }
  }
}
