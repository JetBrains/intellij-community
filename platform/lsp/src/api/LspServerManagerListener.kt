// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Deprecated(
  "Renamed to LspClientManagerListener",
  ReplaceWith("LspClientManagerListener", "com.intellij.platform.lsp.api.LspClientManagerListener"),
)
@Suppress("DEPRECATION")
interface LspServerManagerListener : LspClientManagerListener {
  fun serverStateChanged(lspServer: LspServer) {}
  fun fileOpened(lspServer: LspServer, file: VirtualFile) {}
  fun fileEdited(lspServer: LspServer, file: VirtualFile) {}
  fun diagnosticsReceived(lspServer: LspServer, file: VirtualFile) {}
  fun documentLinksReceived(lspServer: LspServer, file: VirtualFile) {}

  override fun serverStateChanged(lspClient: LspClient): Unit = serverStateChanged(lspClient as LspServer)
  override fun fileOpened(lspClient: LspClient, file: VirtualFile): Unit = fileOpened(lspClient as LspServer, file)
  override fun fileEdited(lspClient: LspClient, file: VirtualFile): Unit = fileEdited(lspClient as LspServer, file)
  override fun diagnosticsReceived(lspClient: LspClient, file: VirtualFile): Unit = diagnosticsReceived(lspClient as LspServer, file)
  override fun documentLinksReceived(lspClient: LspClient, file: VirtualFile): Unit = documentLinksReceived(lspClient as LspServer, file)
}
