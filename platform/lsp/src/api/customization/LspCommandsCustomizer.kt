// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api.customization

import com.intellij.openapi.application.Application
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspServer
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.ExecuteCommandParams

sealed class LspCommandsCustomizer

/**
 * Handles [Command](https://microsoft.github.io/language-server-protocol/specification#command) objects received from the LSP server.
 */
open class LspCommandsSupport : LspCommandsCustomizer() {
  /**
   * Handles [Command](https://microsoft.github.io/language-server-protocol/specification#command) objects received from the LSP server.
   *
   * The default implementation of this function just sends the
   * [workspace/executeCommand](https://microsoft.github.io/language-server-protocol/#workspace_executeCommand)
   * request to the LSP server.
   * Plugins may override this function to implement some plugin-specific handling of some commands.
   *
   * In the spec v.3.17 there are four responses that may include [Command] object:
   * - [CodeAction](https://microsoft.github.io/language-server-protocol/specification#codeAction),
   * for example, a quick fix, an intention action or a refactoring
   * - [CompletionItem](https://microsoft.github.io/language-server-protocol/specification#completionItem)
   * - [InlayHintLabelPart](https://microsoft.github.io/language-server-protocol/specification#inlayHintLabelPart)
   * - [CodeLens](https://microsoft.github.io/language-server-protocol/specification#codeLens)
   *
   * Note that this function is called in the Event Dispatch Thread.
   * Implementations that perform time-consuming tasks should switch to
   * a background thread, for example, using [Application.executeOnPooledThread]
   */
  @RequiresEdt
  open fun executeCommand(lspClient: LspClient, contextFile: VirtualFile, command: Command): Unit =
    @Suppress("DEPRECATION")
    executeCommand(lspClient as LspServer, contextFile, command)

  @Deprecated(
    "Override or call executeCommand(lspClient, contextFile, command) — the LspClient overload",
    ReplaceWith("executeCommand(server as LspClient, contextFile, command)", "com.intellij.platform.lsp.api.LspClient"),
  )
  @RequiresEdt
  @Suppress("DEPRECATION")
  open fun executeCommand(server: LspServer, contextFile: VirtualFile, command: Command): Unit =
    server.sendNotification { it.workspaceService.executeCommand(ExecuteCommandParams(command.command, command.arguments)) }
}

object LspCommandsDisabled : LspCommandsCustomizer()
