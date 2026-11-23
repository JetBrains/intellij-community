// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api

import com.intellij.openapi.vfs.VirtualFile
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.jetbrains.annotations.ApiStatus
import java.util.EventListener

@ApiStatus.Internal
interface LspClientManagerListener : EventListener {
  fun clientAdded(lspClient: LspClient) {}
  fun clientRemoved(lspClient: LspClient) {}
  fun serverStateChanged(lspClient: LspClient) {}
  fun fileOpened(lspClient: LspClient, file: VirtualFile) {}
  fun fileEdited(lspClient: LspClient, file: VirtualFile) {}

  /** The client has sent `textDocument/didClose` for [file]. */
  fun fileClosed(lspClient: LspClient, file: VirtualFile) {}

  /**
   * The raw `textDocument/publishDiagnostics` notification, also when its URI resolves to no file or no loaded document.
   * Runs on the lsp4j listener thread: only hand [params] off, do no VFS or document work here.
   */
  fun diagnosticsPublished(lspClient: LspClient, params: PublishDiagnosticsParams) {}

  fun diagnosticsReceived(lspClient: LspClient, file: VirtualFile) {}
  fun documentLinksReceived(lspClient: LspClient, file: VirtualFile) {}
}
