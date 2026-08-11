// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api

/**
 * The channel for the communication between the IDE and the LSP server.
 * @see LspClientDescriptor.lspCommunicationChannel
 */
sealed class LspCommunicationChannel {

  data object StdIO : LspCommunicationChannel()

  /**
   * @param startProcess `true` means that the IDE should start the LSP server by calling [LspClientDescriptor.startServerProcess]
   * and then to connect to the started server via a socket connection using the specified [port];
   * `false` means that the LSP server is already running,
   * so the IDE should only connect to it via a socket connection
   *
   */
  data class Socket(val port: Int, val startProcess: Boolean = true) : LspCommunicationChannel()
}
