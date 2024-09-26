// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SteppingListener {
  companion object {
    @Topic.AppLevel
    @JvmField
    val TOPIC: Topic<SteppingListener> = Topic(SteppingListener::class.java, Topic.BroadcastDirection.NONE)
  }

  fun beforeSteppingStarted(suspendContext: SuspendContextImpl, steppingAction: SteppingAction) { }
}
