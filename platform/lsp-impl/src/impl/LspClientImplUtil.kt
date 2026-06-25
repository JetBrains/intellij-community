package com.intellij.platform.lsp.impl

import com.intellij.platform.lsp.api.LspClient
import org.jetbrains.annotations.ApiStatus

@get:ApiStatus.Internal
val LspClient.isNotebookSupportedByServer: Boolean
  get() = (this as LspClientImpl).serverCapabilities?.notebookDocumentSync != null