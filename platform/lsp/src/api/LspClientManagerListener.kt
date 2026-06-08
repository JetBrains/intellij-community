// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import java.util.EventListener

@ApiStatus.Internal
interface LspClientManagerListener : EventListener {
  fun serverStateChanged(lspClient: LspClient) {}
  fun fileOpened(lspClient: LspClient, file: VirtualFile) {}
  fun fileEdited(lspClient: LspClient, file: VirtualFile) {}
  fun diagnosticsReceived(lspClient: LspClient, file: VirtualFile) {}
  fun documentLinksReceived(lspClient: LspClient, file: VirtualFile) {}
}
