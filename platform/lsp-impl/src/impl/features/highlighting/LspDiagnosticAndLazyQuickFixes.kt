package com.intellij.platform.lsp.impl.features.highlighting

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspCodeActionsSupport
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.features.quickFix.LspQuickFixSet
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class DiagnosticAndQuickFixes(
  val diagnostic: Diagnostic,
  val quickFixes: List<IntentionAction>,
) {
  @ApiStatus.Internal
  companion object {
    fun build(
      client: LspClientImpl,
      virtualFile: VirtualFile,
      diagnostic: Diagnostic,
      documentId: TextDocumentIdentifier,
    ): DiagnosticAndQuickFixes {
      return DiagnosticAndQuickFixes(diagnostic, LspDiagnosticAndLazyQuickFixes(diagnostic, documentId)
        .getQuickFixes(client, virtualFile)
      )
    }
  }
}

internal fun copyDiagnosticWithRange(diagnostic: Diagnostic, range: Range): Diagnostic = Diagnostic().apply {
  this.range = range
  this.severity = diagnostic.severity
  this.code = diagnostic.code
  this.codeDescription = diagnostic.codeDescription
  this.source = diagnostic.source
  this.message = diagnostic.message
  this.tags = diagnostic.tags
  this.relatedInformation = diagnostic.relatedInformation
  this.data = diagnostic.data
}

internal class LspDiagnosticAndLazyQuickFixes(val diagnostic: Diagnostic, val documentId: TextDocumentIdentifier? = null) {
  private var quickFixes: List<IntentionAction>? = null

  fun getQuickFixes(lspClient: LspClientImpl, file: VirtualFile): List<IntentionAction> {
    quickFixes?.let { return it }

    val codeActionsCustomizer = lspClient.descriptor.lspCustomization.codeActionsCustomizer
    val clientSupportsQuickFixes = codeActionsCustomizer is LspCodeActionsSupport && codeActionsCustomizer.quickFixesSupport
    if (!clientSupportsQuickFixes) return emptyList()
    val serverSupportsQuickFixes = lspClient.supportsCodeActions { kinds -> kinds.any { kind -> kind.startsWith(CodeActionKind.QuickFix) } }
    if (!serverSupportsQuickFixes) return emptyList()

    val quickFixSet = LspQuickFixSet(lspClient, file, diagnostic, documentId)
    val result = quickFixSet.quickFixes
    quickFixes = result
    return result
  }
}
