// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api.customization

import com.intellij.platform.lsp.api.LspServerDescriptor

/**
 * Delegates to previously overridden properties on [LspServerDescriptor]. Clients can continue to use the old API by using this class.
 */
@Suppress("DEPRECATION")
internal class DeprecatedLspCustomization(private val descriptor: LspServerDescriptor) : LspCustomization() {
  override val goToDefinitionCustomizer: LspGoToDefinitionCustomizer
    get() = if (descriptor.lspGoToDefinitionSupport) defaultLspCustomization.goToDefinitionCustomizer else LspGoToDefinitionDisabled
  override val goToTypeDefinitionCustomizer: LspGoToTypeDefinitionCustomizer
    get() = if (descriptor.lspGoToTypeDefinitionSupport) defaultLspCustomization.goToTypeDefinitionCustomizer else LspGoToTypeDefinitionDisabled
  override val hoverCustomizer: LspHoverCustomizer
    get() = if (descriptor.lspHoverSupport) defaultLspCustomization.hoverCustomizer else LspHoverDisabled
  override val completionCustomizer: LspCompletionCustomizer
    get() = descriptor.lspCompletionSupport ?: LspCompletionDisabled
  override val semanticTokensCustomizer: LspSemanticTokensCustomizer
    get() = descriptor.lspSemanticTokensSupport ?: LspSemanticTokensDisabled
  override val diagnosticsCustomizer: LspDiagnosticsCustomizer
    get() = descriptor.lspDiagnosticsSupport ?: LspDiagnosticsDisabled
  override val codeActionsCustomizer: LspCodeActionsCustomizer
    get() = descriptor.lspCodeActionsSupport ?: LspCodeActionsDisabled
  override val commandsCustomizer: LspCommandsCustomizer
    get() = descriptor.lspCommandsSupport ?: LspCommandsDisabled
  override val formattingCustomizer: LspFormattingCustomizer
    get() = descriptor.lspFormattingSupport ?: LspFormattingDisabled
  override val findReferencesCustomizer: LspFindReferencesCustomizer
    get() = descriptor.lspFindReferencesSupport ?: LspFindReferencesDisabled
  override val documentColorCustomizer: LspDocumentColorCustomizer
    get() = descriptor.lspDocumentColorSupport ?: LspDocumentColorDisabled
  override val documentLinkCustomizer: LspDocumentLinkCustomizer
    get() = descriptor.lspDocumentLinkSupport ?: LspDocumentLinkDisabled
}

internal val defaultLspCustomization = LspCustomization()