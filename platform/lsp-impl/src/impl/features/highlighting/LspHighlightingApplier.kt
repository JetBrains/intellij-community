// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.features.highlighting

import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl
import com.intellij.codeInsight.daemon.impl.AnnotationSessionImpl
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil
import com.intellij.codeInsight.highlighting.HyperlinkAnnotator
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.ide.IdeBundle
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.annotation.Annotation
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspBundle
import com.intellij.platform.lsp.api.customization.LspDiagnosticsSupport
import com.intellij.platform.lsp.api.customization.LspDocumentLinkDisabled
import com.intellij.platform.lsp.api.customization.LspSemanticTokensSupport
import com.intellij.platform.lsp.impl.LspCoroutineScopeService
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.lsp.util.getRangeInDocument
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Applies LSP highlighting reactively without restarting the daemon.
 *
 * When caches are updated (diagnostics received, semantic tokens refreshed, etc.),
 * [scheduleHighlightingRefresh] launches a coroutine on a **serialized dispatcher** that converts cache data
 * to [HighlightInfo] and applies it via [UpdateHighlightersUtil.setHighlightersToEditor] on the EDT.
 * No [com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.restart] is involved, so other inspections
 * continue undisturbed.
 *
 * A per-file cache generation counter deduplicates rapid updates: if a later [scheduleHighlightingRefresh] has already
 * incremented the cache generation, an earlier coroutine skips the expensive apply.
 * "Cache generation" = a monotonic counter incremented each time any LSP cache (diagnostics, semantic tokens, etc.)
 * is updated for a given file.
 */
@Service(Service.Level.PROJECT)
internal class LspHighlightingApplier(private val project: Project) {

  private val fileToCurrentGeneration = ConcurrentHashMap<VirtualFile, AtomicLong>()
  private val filesWithWolfReportedErrors = HashSet<VirtualFile>()
  private val fileToLastWolfWrittenGen = HashMap<VirtualFile, Long>()

  private val serializedDispatcher = Dispatchers.Default.limitedParallelism(1)

  /**
   * Called when any LSP cache is updated for the given [file].
   * Increments the cache generation counter and launches a coroutine to apply highlights.
   *
   * If multiple refreshes queue up before any of them starts running, only the latest coroutine actually applies.
   */
  fun scheduleHighlightingRefresh(file: VirtualFile) {
    val generationCounter = fileToCurrentGeneration.computeIfAbsent(file) { AtomicLong() }
    val gen = generationCounter.incrementAndGet()
    LspCoroutineScopeService.getInstance(project).cs.launch(serializedDispatcher) {
      if (generationCounter.get() > gen) return@launch
      val highlights = readAction { collectHighlightsForApply(file) } ?: return@launch
      withContext(Dispatchers.EDT) {
        applyHighlightsToEditor(highlights)
      }
      reportErrorsToWolf(file, highlights.highlights, gen)
    }
  }

  private fun collectHighlightsForApply(file: VirtualFile): HighlightsToApply? {
    if (file is VirtualFileWindow) return null
    val psiFile = PsiManager.getInstance(project).findFile(file) ?: return null
    val document = FileDocumentManager.getInstance().getDocument(file) ?: return null
    val groupId = GROUP_ID
    if (groupId == UNREGISTERED_PASS_ID) return null
    val highlights = collectHighlightInfos(psiFile, file, document)
    return HighlightsToApply(document, highlights, groupId)
  }

  /**
   * Applies highlights to the editor from EDT, following the [UpdateHighlightersUtil.setHighlightersToEditor] pattern.
   */
  private fun applyHighlightsToEditor(data: HighlightsToApply) {
    // Uses deprecated `setHighlightersToEditor` overload because no non-deprecated alternative exists
    // for applying highlights outside a [com.intellij.codeHighlighting.TextEditorHighlightingPass].
    @Suppress("DEPRECATION")
    UpdateHighlightersUtil.setHighlightersToEditor(
      project, data.document, 0, data.document.textLength, data.highlights, null, data.groupId
    )
  }

  private class HighlightsToApply(
    val document: Document,
    val highlights: List<HighlightInfo>,
    val groupId: Int,
  )

  fun collectHighlightInfos(psiFile: PsiFile, file: VirtualFile, document: Document): List<HighlightInfo> {
    val clients = LspClientManagerImpl.getInstanceImpl(project).getClientsWithThisFileOpen(file)
    if (clients.isEmpty()) return emptyList()

    val result = mutableListOf<HighlightInfo>()
    for (client in clients) {
      collectDiagnosticHighlightInfos(client, psiFile, file, document, result)
      collectSemanticTokenHighlightInfos(client, file, result)
      collectDocumentLinkHighlightInfos(client, file, result)
    }
    return result
  }

  private fun collectDiagnosticHighlightInfos(
    client: LspClientImpl,
    psiFile: PsiFile,
    file: VirtualFile,
    document: Document,
    result: MutableList<HighlightInfo>,
  ) {
    val diagnosticsSupport = client.descriptor.lspCustomization.diagnosticsCustomizer as? LspDiagnosticsSupport ?: return
    val diagnosticsAndQuickFixes = client.getDiagnosticsAndQuickFixes(file)
    if (diagnosticsAndQuickFixes.isEmpty()) return

    val session = AnnotationSessionImpl.create(psiFile)

    // Uses deprecated AnnotationHolderImpl because no non-deprecated alternative exists
    // for converting LSP diagnostics to HighlightInfo outside an Annotator.
    @Suppress("DEPRECATION")
    val holder = AnnotationHolderImpl(session, false)
    holder.runAnnotatorWithContext(psiFile)

    for (item in diagnosticsAndQuickFixes) {
      val textRange = getRangeInDocument(document, item.diagnostic.range) ?: continue
      holder.clear()
      diagnosticsSupport.createAnnotation(holder, item.diagnostic, textRange, item.quickFixes)
      for (annotation in holder) {
        result.add(convertAnnotationToHighlightInfo(annotation))
      }
    }
  }

  private fun collectSemanticTokenHighlightInfos(
    client: LspClientImpl,
    file: VirtualFile,
    result: MutableList<HighlightInfo>,
  ) {
    val semanticTokensSupport = client.descriptor.lspCustomization.semanticTokensCustomizer
                                  as? LspSemanticTokensSupport ?: return
    val tokens = client.getSemanticTokens(file)
    for (token in tokens) {
      val textAttributesKey = semanticTokensSupport.getTextAttributesKey(
        token.highlightingInfo.tokenType, token.highlightingInfo.tokenModifiers
      ) ?: continue
      val info = HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION)
        .range(token.textRange)
        .textAttributes(textAttributesKey)
        .severity(HighlightSeverity.TEXT_ATTRIBUTES)
        .createUnconditionally()
      result.add(info)
    }
  }

  private fun collectDocumentLinkHighlightInfos(
    client: LspClientImpl,
    file: VirtualFile,
    result: MutableList<HighlightInfo>,
  ) {
    val customizer = client.descriptor.lspCustomization.documentLinkCustomizer
    if (customizer is LspDocumentLinkDisabled) return
    val documentLinks = client.getDocumentLinkInfos(file)
    for (link in documentLinks) {
      val tooltip = getDocumentLinkTooltip(link.highlightingInfo)
      val shortcutsText = HyperlinkAnnotator.getGoToDeclarationShortcutsText()
      val tooltipWithShortcuts = if (shortcutsText.isNotEmpty()) "$tooltip ($shortcutsText)" else tooltip

      val info = HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION)
        .range(link.textRange)
        .descriptionAndTooltip(tooltipWithShortcuts)
        .textAttributes(CodeInsightColors.INACTIVE_HYPERLINK_ATTRIBUTES)
        .severity(HighlightSeverity.TEXT_ATTRIBUTES)
        .createUnconditionally()
      result.add(info)
    }
  }

  /**
   * Snapshot of the current cache generation for [file]. Pass this back to [reportErrorsToWolf] together with
   * highlights collected from the cache so that a stale snapshot can't overwrite a newer Wolf write.
   */
  fun currentGeneration(file: VirtualFile): Long {
    return fileToCurrentGeneration[file]?.get() ?: 0L
  }

  /**
   * @param observedGen the cache generation at the time [highlights] was collected. Reports with a strictly older
   *   generation than the last committed one are dropped to prevent stale snapshots from racing newer ones.
   */
  fun reportErrorsToWolf(file: VirtualFile, highlights: List<HighlightInfo>, observedGen: Long) {
    val hasErrors = highlights.any { it.severity == HighlightSeverity.ERROR }
    val wolf = WolfTheProblemSolver.getInstance(project)
    synchronized(filesWithWolfReportedErrors) {
      val last = fileToLastWolfWrittenGen[file] ?: -1L
      if (observedGen < last) return
      fileToLastWolfWrittenGen[file] = observedGen
      if (hasErrors) {
        filesWithWolfReportedErrors.add(file)
        wolf.reportProblemsFromExternalSource(file, LSP_EXTERNAL_SOURCE)
      }
      else if (filesWithWolfReportedErrors.remove(file)) {
        wolf.clearProblemsFromExternalSource(file, LSP_EXTERNAL_SOURCE)
      }
    }
  }

  companion object {
    private const val LSP_EXTERNAL_SOURCE: String = "LSP"
    const val UNREGISTERED_PASS_ID: Int = -1

    /**
     * Assigned by the platform via [com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar.registerTextEditorHighlightingPass].
     * Volatile because it's written from the registration thread and read from highlighting coroutines.
     */
    @Volatile
    var GROUP_ID: Int = UNREGISTERED_PASS_ID

    fun getInstance(project: Project): LspHighlightingApplier = project.service()

    fun convertAnnotationToHighlightInfo(annotation: Annotation): HighlightInfo {
      val type = toHighlightInfoType(annotation.highlightType, annotation.severity)
      val builder = HighlightInfo.newHighlightInfo(type)
        .range(annotation.startOffset, annotation.endOffset)
        .severity(annotation.severity)

      annotation.message?.let { builder.description(it) }
      annotation.tooltip?.let { builder.escapedToolTip(it) }
      annotation.enforcedTextAttributes
        ?.let { builder.textAttributes(it) } ?: builder.textAttributes(annotation.textAttributes)
      if (annotation.isAfterEndOfLine) builder.endOfLine()
      if (annotation.isFileLevelAnnotation) builder.fileLevelAnnotation()
      annotation.gutterIconRenderer?.let { builder.gutterIconRenderer(it) }

      for (fix in annotation.quickFixes.orEmpty()) {
        builder.registerFix(fix.quickFix, null, null, fix.textRange, fix.key)
      }

      return builder.createUnconditionally()
    }

    private fun toHighlightInfoType(problemHighlightType: ProblemHighlightType, severity: HighlightSeverity): HighlightInfoType {
      if (problemHighlightType == ProblemHighlightType.LIKE_UNUSED_SYMBOL) return HighlightInfoType.UNUSED_SYMBOL
      if (problemHighlightType == ProblemHighlightType.LIKE_UNKNOWN_SYMBOL) return HighlightInfoType.WRONG_REF
      if (problemHighlightType == ProblemHighlightType.LIKE_DEPRECATED) return HighlightInfoType.DEPRECATED
      if (problemHighlightType == ProblemHighlightType.LIKE_MARKED_FOR_REMOVAL) return HighlightInfoType.MARKED_FOR_REMOVAL
      if (problemHighlightType == ProblemHighlightType.POSSIBLE_PROBLEM) return HighlightInfoType.POSSIBLE_PROBLEM
      return HighlightInfo.convertSeverity(severity)
    }

    private fun getDocumentLinkTooltip(documentLink: LspDocumentLink): @NlsSafe String {
      if (documentLink.tooltip != null) return documentLink.tooltip

      val uri = documentLink.targetUri
      @Suppress("HttpUrlsUsage")
      if (uri != null && (uri.startsWith("http://") || uri.startsWith("https://"))) {
        return IdeBundle.message("open.url.in.browser.tooltip")
      }

      return LspBundle.message("follow.link.tooltip")
    }
  }
}
