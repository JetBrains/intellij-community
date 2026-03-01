// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

/**
 * This listener is always invoked in write action synchronously with the change of dumb mode status.
 * The thread of invocation is undefined.
 */
@ApiStatus.Experimental
interface DumbModeListenerBackgroundable {
  companion object {

    @ApiStatus.Experimental
    @Topic.ProjectLevel
    @JvmField
    val TOPIC: Topic<DumbModeListenerBackgroundable> =
      Topic("dumb mode backgroundable", DumbModeListenerBackgroundable::class.java, Topic.BroadcastDirection.NONE)
  }

  @RequiresWriteLock
  fun enteredDumbMode() {
  }

  @RequiresWriteLock
  fun exitDumbMode() {
  }
}
