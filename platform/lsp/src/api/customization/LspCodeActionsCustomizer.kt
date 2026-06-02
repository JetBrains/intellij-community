// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api.customization

import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspServer
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.Diagnostic

sealed class LspCodeActionsCustomizer

/**
 * Handles [CodeAction](https://microsoft.github.io/language-server-protocol/specification#codeAction) objects received from the LSP server.
 */
open class LspCodeActionsSupport : LspCodeActionsCustomizer() {

  /**
   * Whether the IDE should send
   * [textDocument/codeAction](https://microsoft.github.io/language-server-protocol/specification/#textDocument_codeAction)
   * requests to the LSP server to receive quick fixes for a specific [Diagnostic] (see [CodeAction.diagnostics]).
   */
  open val quickFixesSupport: Boolean = true

  /**
   * Creates quick fix for the specific [CodeAction].
   * Implementations may return `null` if they don't want to provide a quick fix for this [codeAction].
   *
   * @param codeAction result of the
   * [textDocument/codeAction](https://microsoft.github.io/language-server-protocol/specification/#textDocument_codeAction) request to the
   * LSP server.
   * The request asked for quick fixes for a specific [Diagnostic] (see [CodeAction.diagnostics]).
   */
  open fun createQuickFix(lspClient: LspClient, codeAction: CodeAction): LspIntentionAction? =
    @Suppress("DEPRECATION")
    createQuickFix(lspClient as LspServer, codeAction)

  @Deprecated(
    "Override or call createQuickFix(lspClient, codeAction) — the LspClient overload",
    ReplaceWith("createQuickFix(lspServer as LspClient, codeAction)", "com.intellij.platform.lsp.api.LspClient"),
  )
  @Suppress("DEPRECATION")
  open fun createQuickFix(lspServer: LspServer, codeAction: CodeAction): LspIntentionAction? =
    LspIntentionAction(lspServer, codeAction)

  /**
   * Whether the IDE should send
   * [textDocument/codeAction](https://microsoft.github.io/language-server-protocol/specification/#textDocument_codeAction)
   * requests to the LSP server to receive code edit suggestions based on the current text selection and caret position,
   * but not related to a specific [Diagnostic].
   *
   * See [Intention actions](https://www.jetbrains.com/help/idea/intention-actions.html).
   */
  open val intentionActionsSupport: Boolean = true

  /**
   * Creates an [Intention action](https://www.jetbrains.com/help/idea/intention-actions.html) for the specific [CodeAction].
   * Implementations may return `null` if they don't want to show an Intention action for this [codeAction].
   *
   * @param codeAction result of the
   * [textDocument/codeAction](https://microsoft.github.io/language-server-protocol/specification/#textDocument_codeAction) request to the
   * LSP server.
   * The request asked for code edit suggestions based on the current text selection and caret position,
   * but not related to a specific [Diagnostic].
   */
  open fun createIntentionAction(lspClient: LspClient, codeAction: CodeAction): LspIntentionAction? =
    @Suppress("DEPRECATION")
    createIntentionAction(lspClient as LspServer, codeAction)

  @Deprecated(
    "Override or call createIntentionAction(lspClient, codeAction) — the LspClient overload",
    ReplaceWith("createIntentionAction(lspServer as LspClient, codeAction)", "com.intellij.platform.lsp.api.LspClient"),
  )
  @Suppress("DEPRECATION")
  open fun createIntentionAction(lspServer: LspServer, codeAction: CodeAction): LspIntentionAction? =
    LspIntentionAction(lspServer, codeAction)
}

object LspCodeActionsDisabled : LspCodeActionsCustomizer()
