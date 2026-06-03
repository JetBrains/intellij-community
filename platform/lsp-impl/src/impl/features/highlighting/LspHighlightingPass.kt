// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.features.highlighting

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.impl.BackgroundUpdateHighlightersUtil
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspDiagnosticsSupport
import com.intellij.platform.lsp.api.customization.LspDocumentLinkDisabled
import com.intellij.platform.lsp.api.customization.LspSemanticTokensSupport
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

/**
 * A highlighting pass that applies LSP highlighting (diagnostics, semantic tokens, document links) to the editor.
 *
 * This pass has two roles:
 * 1. **Trigger pull-based caches**: Calling `getHighlightings()` / `getSemanticTokens()` / `getDocumentLinkInfos()`
 *    on pull-based caches checks `psiModCount` staleness and triggers `scheduleHighlightingsUpdate()` when stale,
 *    which sends pull requests to the server. Without the pass, pull diagnostics would never refresh after edits.
 * 2. **Apply highlights**: Converts cache data to [HighlightInfo] and applies to the editor.
 *    This handles edge cases like the initial file open before the server responds. This may duplicate
 *    work already done by the reactive path ([LspHighlightingApplier]), but ensures correctness after document edits.
 */
internal class LspHighlightingPass(
  project: Project,
  document: Document,
  private val psiFile: PsiFile,
  private val file: VirtualFile,
  private val editor: Editor?,
) : TextEditorHighlightingPass(project, document, false), DumbAware {

  private var highlightInfos: List<HighlightInfo> = emptyList()

  override fun doCollectInformation(progress: ProgressIndicator) {
    if (file is VirtualFileWindow) return

    // GROUP_ID is assigned by the platform during pass registration; skip if not yet initialized.
    // The platform will re-run the pass once registration completes.
    val groupId = LspHighlightingApplier.GROUP_ID
    if (groupId == LspHighlightingApplier.UNREGISTERED_PASS_ID) return

    val clients = LspClientManagerImpl.getInstanceImpl(myProject).getClientsWithThisFileOpen(file) // clients may be empty

    // Always trigger pull-based caches (semantic tokens, pull diagnostics, document links).
    // This is critical: getHighlightings()/getSemanticTokens()/getDocumentLinkInfos() check psiModCount
    // and schedule server requests when stale.
    for (client in clients) {
      val diagnosticsCustomizer = client.descriptor.lspCustomization.diagnosticsCustomizer
      if (diagnosticsCustomizer is LspDiagnosticsSupport) {
        client.getDiagnosticsAndQuickFixes(file) // triggers pull diagnostics cache
      }

      val semanticTokensCustomizer = client.descriptor.lspCustomization.semanticTokensCustomizer
      if (semanticTokensCustomizer is LspSemanticTokensSupport) {
        client.getSemanticTokens(file) // triggers semantic tokens cache
      }

      val documentLinkCustomizer = client.descriptor.lspCustomization.documentLinkCustomizer
      if (documentLinkCustomizer !is LspDocumentLinkDisabled) {
        client.getDocumentLinkInfos(file) // triggers document link cache
      }
    }

    val applier = LspHighlightingApplier.getInstance(myProject)

    // Snapshot the cache generation *before* reading the cache, so that if a reactive refresh
    // writes Wolf with a newer snapshot in between, our reportErrorsToWolf below is dropped as stale.
    val observedGen = applier.currentGeneration(file)
    val highlights = applier.collectHighlightInfos(psiFile, file, myDocument)
    highlightInfos = highlights

    BackgroundUpdateHighlightersUtil.setHighlightersToEditor(myProject, psiFile, myDocument, 0, myDocument.textLength, highlights, groupId)
    applier.reportErrorsToWolf(file, highlights, observedGen)
  }

  override fun doApplyInformationToEditor() {
    // Highlights are applied in doCollectInformation via BackgroundUpdateHighlightersUtil.
    // Save PSI modification stamp so the factory can skip the next pass if PSI hasn't changed.
    editor?.putUserData(PSI_MODIFICATION_STAMP, PsiManager.getInstance(myProject).modificationTracker.modificationCount)
  }

  /**
   * Called by [com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl.runMainPasses] to collect results for batch analysis
   */
  override fun getInfos(): List<HighlightInfo> = highlightInfos
}
