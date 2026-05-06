// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import java.util.EventListener

@ApiStatus.Internal
interface LspServerManagerListener : EventListener {
  fun serverStateChanged(lspServer: LspServer) {}
  fun fileOpened(lspServer: LspServer, file: VirtualFile) {}
  fun fileEdited(lspServer: LspServer, file: VirtualFile) {}
  fun diagnosticsReceived(lspServer: LspServer, file: VirtualFile) {}
  fun documentLinksReceived(lspServer: LspServer, file: VirtualFile) {}
}
