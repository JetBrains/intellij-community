// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl

import com.intellij.execution.Executor
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface RunContentDescriptorLifecycleListener {
  /**
   * Always called when the descriptor is constructed and before a proper content is assigned to it
   */
  fun beforeContentShown(descriptor: RunContentDescriptor, executor: Executor)

  /**
   * Called *only* when a content was assigned to the descriptor *and* the descriptor was explicitly marked as not hidden
   */
  fun afterContentShown(descriptor: RunContentDescriptor, executor: Executor)
}

@ApiStatus.Internal
@JvmField
@Topic.ProjectLevel
val RUN_CONTENT_DESCRIPTOR_LIFECYCLE_TOPIC: Topic<RunContentDescriptorLifecycleListener> =
  Topic(RunContentDescriptorLifecycleListener::class.java, Topic.BroadcastDirection.NONE)
