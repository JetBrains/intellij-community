// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.features.intention

import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.customization.LspCodeActionInternalService
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.hasCodeActionResolveProvider
import org.eclipse.lsp4j.CodeAction

internal class LspCodeActionInternalServiceImpl : LspCodeActionInternalService() {
  override fun resolveCodeAction(lspClient: LspClient, codeAction: CodeAction): CodeAction? {
    if (!(lspClient as LspClientImpl).hasCodeActionResolveProvider()) return null
    return lspClient.sendRequestSync { it.textDocumentService.resolveCodeAction(codeAction) }
  }
}
