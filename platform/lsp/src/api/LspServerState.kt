// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api

/**
 * @see LspServer.state
 */
enum class LspServerState {
  /**
   * The [LspServer] has the [Initializing] state initially.
   * The state changes to [Running] when the IDE receives a response to the
   * [initialize](https://microsoft.github.io/language-server-protocol/specification/#initialize) request.
   */
  Initializing,

  /**
   * The [LspServer] has the [Running] state when it is ready to handle requests and notifications from the IDE.
   * Technically, it means that the IDE has already received a response to the very first
   * [initialize](https://microsoft.github.io/language-server-protocol/specification/#initialize) request.
   */
  Running,

  ShutdownNormally,

  ShutdownUnexpectedly,
}
