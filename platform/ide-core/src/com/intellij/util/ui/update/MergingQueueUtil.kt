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
  require(this.isActive) { "Queue must be active for tracking" }
  val tracker = ApplicationManager.getApplication().service<MergingUpdateQueueTracker>()
  val trackedUpdate = tracker.trackUpdate(update)
  queue(trackedUpdate)
}

@Internal
interface MergingUpdateQueueTracker {
  fun trackUpdate(update: Update): Update
}
