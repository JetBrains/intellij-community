// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api

import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import org.eclipse.lsp4j.InitializeResult

/**
 * Plugins can register their [LspServerListener] by overriding [LspClientDescriptor.lspServerListener].
 */
interface LspServerListener {
  /**
   * Once the IDE receives a response from the LSP server to the
   * [initialize](https://microsoft.github.io/language-server-protocol/specification/#initialize) request,
   * it sends the [initialized](https://microsoft.github.io/language-server-protocol/specification/#initialized)
   * notification to the server and calls this function.
   */
  @RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
  @RequiresReadLockAbsence
  fun serverInitialized(params: InitializeResult) {
  }

  /**
   * Normal shutdown examples:
   * - the project is being closed
   * - the 'Restart' button has been clicked in the Language Services status bar widget
   *
   * Unexpected shutdown examples:
   * - the response to the [initialize](https://microsoft.github.io/language-server-protocol/specification/#initialize)
   * request has not arrived
   * - the LSP server process has terminated
   *
   * When the process of a running server ends or its connection closes, the IDE can restart the server automatically.
   * It does so only if [LspClientDescriptor.maxUnexpectedTerminationRestarts] is greater than 0.
   * Then the IDE calls [serverInitialized] again after [serverStopped].
   */
  @RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
  @RequiresReadLockAbsence
  fun serverStopped(shutdownNormally: Boolean) {
  }
}
