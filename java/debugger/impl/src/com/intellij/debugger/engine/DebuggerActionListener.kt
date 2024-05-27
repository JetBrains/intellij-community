// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface DebuggerActionListener {

  fun onRunToCursor(context: SuspendContextImpl?)

  companion object {
    @JvmField
    @Topic.ProjectLevel
    val TOPIC = Topic(DebuggerActionListener::class.java)
  }
}
