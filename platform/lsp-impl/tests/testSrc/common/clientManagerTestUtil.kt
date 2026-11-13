// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.common

import com.intellij.openapi.util.Disposer
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManagerListener
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import kotlinx.coroutines.CompletableDeferred

/** Runs [block] with [listener] registered on this manager, and removes the listener afterwards. */
internal suspend fun <T> LspClientManagerImpl.withListener(listener: LspClientManagerListener, block: suspend () -> T): T {
  val disposable = Disposer.newDisposable("LspClientManagerTestUtil")
  try {
    addListener(listener, disposable, false)
    return block()
  }
  finally {
    Disposer.dispose(disposable)
  }
}

/** Runs [start] and returns the first client that reaches the Running state afterwards. */
internal suspend fun awaitRunningClient(manager: LspClientManagerImpl, start: () -> Unit): LspClientImpl {
  val running = CompletableDeferred<LspClient>()
  return manager.withListener(object : LspClientManagerListener {
    override fun serverStateChanged(lspClient: LspClient) {
      if (lspClient.state == LspServerState.Running) running.complete(lspClient)
    }
  }) {
    start()
    running.await() as LspClientImpl
  }
}

/** Runs [stop] and waits for the first `clientRemoved` event afterwards for a client that matches [removed]. */
internal suspend fun stopAndWait(manager: LspClientManagerImpl, removed: (LspClient) -> Boolean = { true }, stop: () -> Unit) {
  val done = CompletableDeferred<Unit>()
  manager.withListener(object : LspClientManagerListener {
    override fun clientRemoved(lspClient: LspClient) {
      if (removed(lspClient)) done.complete(Unit)
    }
  }) {
    stop()
    done.await()
  }
}
