package com.intellij.platform.lsp.impl.highlightingCommon

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.impl.LspServerImpl
import com.intellij.platform.lsp.impl.codeLens.LspCodeLensCache
import com.intellij.platform.lsp.impl.folding.LspFoldingRangeCache
import com.intellij.platform.lsp.impl.highlighting.DiagnosticAndQuickFixes
import com.intellij.platform.lsp.impl.highlighting.LspDocumentLinkCache
import com.intellij.platform.lsp.impl.highlighting.LspPublishDiagnosticsCache
import com.intellij.platform.lsp.impl.highlighting.LspPullDiagnosticsCache
import com.intellij.platform.lsp.impl.highlighting.LspSemanticTokensCache
import com.intellij.platform.lsp.impl.highlighting.copyDiagnosticWithRange
import com.intellij.platform.lsp.impl.inlayHint.LspInlayHintsCache
import com.intellij.platform.lsp.impl.inlayHintColor.LspDocumentColorCache
import com.intellij.platform.lsp.util.getLsp4jRange
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.eclipse.lsp4j.Diagnostic

internal class LspHighlightingCacheRegistry(private val lspServer: LspServerImpl) {
  private val allCaches = mutableListOf<LspHighlightingCache<*>>()

  internal val publishDiagnosticsCache = register(LspPublishDiagnosticsCache(lspServer))
  internal val pullDiagnosticsCache = register(LspPullDiagnosticsCache(lspServer))
  internal val semanticTokensCache = register(LspSemanticTokensCache(lspServer))
  internal val documentLinkCache = register(LspDocumentLinkCache(lspServer))

  internal val inlayHintsCache = register(LspInlayHintsCache(lspServer))
  internal val documentColorCache = register(LspDocumentColorCache(lspServer))
  internal val foldingRangeCache = register(LspFoldingRangeCache(lspServer))
  internal val codeLensCache = register(LspCodeLensCache(lspServer))

  private fun <T : LspHighlightingCache<*>> register(cache: T): T {
    allCaches.add(cache)
    return cache
  }

  internal fun fileEdited(file: VirtualFile, e: DocumentEvent) {
    allCaches.forEach { it.fileEdited(file, e) }
  }

  internal fun clearCache() {
    allCaches.forEach { it.clearCache() }
  }

  /**
   * The quick fixes in the returned list are special wrappers that may or may not possess a real
   * [LspIntentionAction][com.intellij.platform.lsp.api.customization.LspIntentionAction] object at this moment,
   * but clients shouldn't care about that. For curious: see [LspQuickFixSet][com.intellij.platform.lsp.impl.quickFix.LspQuickFixSet].
   */
  @RequiresBackgroundThread
  internal fun getDiagnosticsAndQuickFixes(file: VirtualFile): List<DiagnosticAndQuickFixes> {
    val pushedCachedHighlightings = publishDiagnosticsCache.getHighlightings(file)
    val pushedDiagnostics = pushedCachedHighlightings.map { it.highlightingInfo.diagnostic }

    val document = FileDocumentManager.getInstance().getDocument(file)
                   ?: return emptyList()

    val pushedDiagnosticsAndQuickFixes = pushedCachedHighlightings.map {
      val updatedDiagnostic = copyDiagnosticWithRange(it.highlightingInfo.diagnostic,
                                                      getLsp4jRange(document, it.textRange.startOffset, it.textRange.length))
      DiagnosticAndQuickFixes(updatedDiagnostic, it.highlightingInfo.getQuickFixes(lspServer, file))
    }

    val pulledDiagnosticsAndQuickFixes = getPulledDiagnosticsAndQuickFixes(file, document, pushedDiagnostics)

    return pushedDiagnosticsAndQuickFixes + pulledDiagnosticsAndQuickFixes
  }

  private fun getPulledDiagnosticsAndQuickFixes(
    file: VirtualFile,
    document: Document,
    alreadyKnownDiagnostics: List<Diagnostic>,
  ): List<DiagnosticAndQuickFixes> {
    val pulledDiagnosticsAndLazyQuickFixes = pullDiagnosticsCache.getHighlightings(file)
    if (pulledDiagnosticsAndLazyQuickFixes.isEmpty()) return emptyList()

    return pulledDiagnosticsAndLazyQuickFixes.mapNotNull {
      val diagnostic = it.highlightingInfo.diagnostic
      if (alreadyKnownDiagnostics.contains(diagnostic)) return@mapNotNull null

      val updatedDiagnostic = copyDiagnosticWithRange(diagnostic,
                                                      getLsp4jRange(document, it.textRange.startOffset, it.textRange.length))
      DiagnosticAndQuickFixes(updatedDiagnostic, it.highlightingInfo.getQuickFixes(lspServer, file))
    }
  }
}