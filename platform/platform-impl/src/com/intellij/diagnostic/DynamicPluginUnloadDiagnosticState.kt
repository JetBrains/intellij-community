// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import java.util.concurrent.atomic.AtomicLong

internal object DynamicPluginUnloadDiagnosticState {
  private val firstUnloadAttemptTimestamp = AtomicLong(0)

  fun markUnloadAttempted() {
    firstUnloadAttemptTimestamp.compareAndSet(0, System.currentTimeMillis())
  }

  fun wasUnloadAttemptedBefore(timestamp: Long): Boolean {
    val unloadTimestamp = firstUnloadAttemptTimestamp.get()
    return unloadTimestamp != 0L && unloadTimestamp <= timestamp
  }
}

internal class DynamicPluginUnloadDiagnosticListener : DynamicPluginListener {
  override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
    DynamicPluginUnloadDiagnosticState.markUnloadAttempted()
  }
}
