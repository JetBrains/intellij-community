// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.sun.jdi.ThreadReference

interface LightOrRealThreadInfo {
  val realThread: ThreadReference?

  fun checkSameThread(thread: ThreadReference, suspendContext: SuspendContextImpl): Boolean
}

data class RealThreadInfo(override val realThread: ThreadReference) : LightOrRealThreadInfo {
  override fun checkSameThread(thread: ThreadReference, suspendContext: SuspendContextImpl): Boolean {
    return realThread == thread
  }
}
