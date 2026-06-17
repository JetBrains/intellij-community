// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api.customization

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspServer
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.eclipse.lsp4j.CodeActionKind

/**
 * Customizes the 'Optimize Imports' feature behavior.
 *
 * Implementations may override this class to:
 * - return their specific subclass of [LspOptimizeImportsSupport] that fine-tunes the behavior
 * - return [LspOptimizeImportsDisabled] to disable the LSP feature
 *
 * When enabled, the IDE will use the
 * [textDocument/codeAction](https://microsoft.github.io/language-server-protocol/specification/#textDocument_codeAction)
 * request with the `"source.organizeImports"` code action kind to optimize imports in the file.
 */
sealed class LspOptimizeImportsCustomizer

open class LspOptimizeImportsSupport : LspOptimizeImportsCustomizer() {
  /**
   * Determines whether the LSP server should exclusively handle import optimization for the given file.
   *
   * This function is called when the "Optimize Imports" action is invoked, which might be explicit
   * (e.g., on a shortcut) or implicit (e.g., on save or before commit if the corresponding option is enabled).
   *
   * If this function returns `true`, the IDE will send a
   * [textDocument/codeAction](https://microsoft.github.io/language-server-protocol/specification/#textDocument_codeAction)
   * request with the `"source.organizeImports"` code action kind to the server and apply the received result.
   * The IDE's internal import optimizer (if it exists) won't be used.
   *
   * If this function returns `false`, the IDE will use its internal import optimizer (if it exists) for this file.
   *
   * The default implementation returns `true` only when:
   * - The IDE cannot optimize imports in this file itself, AND
   * - The server advertises support for the code action kind `"source.organizeImports"`
   *
   * Implementation may look like this:
   *
   *    override fun shouldOptimizeImportsInThisFileExclusivelyByServer(
   *      lspClient: LspClient,
   *      file: VirtualFile,
   *      ideCanOptimizeImportsInThisFileItself: Boolean,
   *    ): Boolean = file.extension == "foo"
   *
   * @param ideCanOptimizeImportsInThisFileItself `true` if the IDE has its own [com.intellij.lang.ImportOptimizer]
   *        that supports the given file
   */
  @RequiresReadLock
  open fun shouldOptimizeImportsInThisFileExclusivelyByServer(
    lspClient: LspClient,
    file: VirtualFile,
    ideCanOptimizeImportsInThisFileItself: Boolean,
  ): Boolean =
    @Suppress("DEPRECATION")
    shouldOptimizeImportsInThisFileExclusivelyByServer(lspClient as LspServer, file, ideCanOptimizeImportsInThisFileItself)

  @Deprecated(
    "Override or call shouldOptimizeImportsInThisFileExclusivelyByServer(lspClient, file, ideCanOptimizeImportsInThisFileItself) — the LspClient overload",
    ReplaceWith("shouldOptimizeImportsInThisFileExclusivelyByServer(lspServer as LspClient, file, ideCanOptimizeImportsInThisFileItself)"),
  )
  @RequiresReadLock
  @Suppress("DEPRECATION")
  open fun shouldOptimizeImportsInThisFileExclusivelyByServer(
    lspServer: LspServer,
    file: VirtualFile,
    ideCanOptimizeImportsInThisFileItself: Boolean,
  ): Boolean {
    if (ideCanOptimizeImportsInThisFileItself) return false
    val codeActionKinds = lspServer.initializeResult?.capabilities?.codeActionProvider?.right?.codeActionKinds ?: return false
    return codeActionKinds.contains(CodeActionKind.SourceOrganizeImports)
  }
}

object LspOptimizeImportsDisabled : LspOptimizeImportsCustomizer()
