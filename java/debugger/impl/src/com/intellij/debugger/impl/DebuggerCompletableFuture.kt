// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl

import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * This implementation of [CompletableFuture] ensures that it cannot be awaited
 * on [com.intellij.debugger.engine.DebuggerManagerThreadImpl], as it
 * will lead to deadlock.
 */
internal class DebuggerCompletableFuture<T> : CompletableFuture<T>() {
  override fun <U> newIncompleteFuture() = DebuggerCompletableFuture<U>()

  override fun get(): T? {
    assertNotDebuggerThreadOrCompleted()
    return super.get()
  }

  override fun get(timeout: Long, unit: TimeUnit): T? {
    assertNotDebuggerThreadOrCompleted()
    return super.get(timeout, unit)
  }

  override fun join(): T? {
    assertNotDebuggerThreadOrCompleted()
    return super.join()
  }

  private fun assertNotDebuggerThreadOrCompleted() {
    if (isDone) return
    if (DebuggerManagerThreadImpl.isManagerThread()) {
      throw IllegalStateException("Should not be called from the debugger thread")
    }
  }
}
