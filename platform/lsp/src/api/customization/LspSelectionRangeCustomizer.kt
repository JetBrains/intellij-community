// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api.customization

import com.intellij.openapi.vfs.VirtualFile

/**
 * See LSP specification: [Selection Range](https://microsoft.github.io/language-server-protocol/specification/#textDocument_selectionRange).
 * The corresponding feature names in the IDE are "Extend Selection" and "Shrink Selection".
 */
sealed class LspSelectionRangeCustomizer

open class LspSelectionRangeSupport : LspSelectionRangeCustomizer() {
  /**
   * This function is called when the "Extend Selection" or "Shrink Selection" action is invoked.
   * `True` means that the IDE should send the
   * [textDocument/selectionRange](https://microsoft.github.io/language-server-protocol/specification/#textDocument_selectionRange)
   * request to the server and use the received information for the action.
   */
  open fun shouldAskServerForSelectionRange(file: VirtualFile): Boolean = true
}

object LspSelectionRangeDisabled : LspSelectionRangeCustomizer()
