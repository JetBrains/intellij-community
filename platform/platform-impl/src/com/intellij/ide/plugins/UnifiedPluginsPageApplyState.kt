// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

internal class UnifiedPluginsPageApplyState {
  private val lock = Any()
  private var pendingOperations = 0
  private var disposed = false
  private var restartFlowOwnsSession = false
  private var resetOwnsSession = false
  private var closeRequested = false

  fun operationStarted() {
    synchronized(lock) {
      check(!closeRequested) { "Plugin session close was already requested" }
      pendingOperations++
    }
  }

  fun operationFinished(restartRequired: Boolean): Boolean {
    synchronized(lock) {
      check(pendingOperations > 0) { "No plugin apply operation is pending" }
      pendingOperations--
      restartFlowOwnsSession = restartFlowOwnsSession || restartRequired
      return requestCloseIfReady()
    }
  }

  fun resetWithSessionRemovalStarted(): Boolean {
    synchronized(lock) {
      check(!closeRequested) { "Plugin session close was already requested" }
      if (resetOwnsSession) return false
      resetOwnsSession = true
      return true
    }
  }

  fun resetWithSessionRemovalFailed(): Boolean {
    synchronized(lock) {
      if (!resetOwnsSession) return false
      resetOwnsSession = false
      return requestCloseIfReady()
    }
  }

  fun dispose(): Boolean {
    synchronized(lock) {
      disposed = true
      return requestCloseIfReady()
    }
  }

  private fun requestCloseIfReady(): Boolean {
    if (!disposed || pendingOperations > 0 || restartFlowOwnsSession || resetOwnsSession || closeRequested) return false
    closeRequested = true
    return true
  }
}
