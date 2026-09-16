// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.features.lineMarkers

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.codeInsight.daemon.NavigateAction
import com.intellij.icons.AllIcons
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.lsp.api.LspBundle
import com.intellij.platform.lsp.api.customization.LspInheritanceMarker
import com.intellij.platform.lsp.api.customization.LspInheritanceMarkerKind
import com.intellij.platform.lsp.api.customization.LspInheritanceMarkersSupport
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.lsp.impl.LspCoroutineScopeService
import com.intellij.platform.lsp.impl.documentMapping
import com.intellij.platform.lsp.util.navigateOrShowPopup
import com.intellij.psi.PsiElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.Location
import java.awt.event.MouseEvent
import javax.swing.Icon

/**
 * Shows the override/implement gutter icons for LSP-backed files.
 *
 * The provider only reads [LspInheritanceMarkersCache] snapshots.
 * The cache pulls the data from the server asynchronously and restarts the daemon when a response arrives.
 */
internal class LspInheritanceLineMarkerProvider : LineMarkerProviderDescriptor(), DumbAware {
  override fun getName(): String = LspBundle.message("lineMarkers.LspInheritanceLineMarkerProvider.name")

  override fun getIcon(): Icon = AllIcons.Gutter.ImplementedMethod

  override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

  override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
    val psiFile = elements.firstOrNull()?.containingFile ?: return
    val virtualFile = psiFile.virtualFile ?: return
    if (virtualFile is VirtualFileWindow || !virtualFile.isInLocalFileSystem) return

    // computed lazily: most files have no LSP client with this feature
    var elementSet: Set<PsiElement>? = null

    for (client in LspClientManagerImpl.getInstanceImpl(psiFile.project).getClientsWithThisFileOpen(virtualFile)) {
      if (client.descriptor.lspCustomization.inheritanceMarkersCustomizer !is LspInheritanceMarkersSupport) continue
      for (cached in client.getInheritanceMarkers(virtualFile)) {
        ProgressManager.checkCanceled()
        val marker = cached.highlightingInfo

        // A snapshot range can lag behind the document for a moment, and LineMarkerInfo rejects an out-of-file range.
        val anchorRange = cached.textRange.intersection(psiFile.textRange) ?: continue
        val anchor = psiFile.findElementAt(anchorRange.startOffset) ?: continue
        val set = elementSet ?: elements.toHashSet().also { elementSet = it }
        // The contract of collectSlowLineMarkers: create markers only for the passed elements.
        if (anchor !in set) continue

        result.add(createLineMarkerInfo(client, virtualFile, anchor, anchorRange, marker))
      }
    }
  }

  private fun createLineMarkerInfo(
    lspClient: LspClientImpl,
    file: VirtualFile,
    anchor: PsiElement,
    anchorRange: TextRange,
    marker: LspInheritanceMarker,
  ): LineMarkerInfo<PsiElement> {
    val icon = when (marker.kind) {
      LspInheritanceMarkerKind.IMPLEMENTED_METHOD, LspInheritanceMarkerKind.IMPLEMENTED_CLASS -> AllIcons.Gutter.ImplementedMethod
      LspInheritanceMarkerKind.OVERRIDDEN_METHOD, LspInheritanceMarkerKind.SUBCLASSED_CLASS -> AllIcons.Gutter.OverridenMethod
    }
    val tooltip = tooltip(marker)
    val info = LineMarkerInfo(
      anchor,
      anchorRange,
      icon,
      { tooltip },
      LspInheritanceMarkerNavigationHandler(lspClient, file, anchorRange.startOffset, marker),
      GutterIconRenderer.Alignment.RIGHT,
      { tooltip },
    )
    return NavigateAction.setNavigateAction(info, tooltip, IdeActions.ACTION_GOTO_IMPLEMENTATION)
  }

  private fun tooltip(marker: LspInheritanceMarker): @NlsContexts.Tooltip String = when (marker.kind) {
    LspInheritanceMarkerKind.IMPLEMENTED_METHOD, LspInheritanceMarkerKind.IMPLEMENTED_CLASS ->
      LspBundle.message("lineMarkers.tooltip.has.implementations")
    LspInheritanceMarkerKind.OVERRIDDEN_METHOD -> LspBundle.message("lineMarkers.tooltip.is.overridden")
    LspInheritanceMarkerKind.SUBCLASSED_CLASS -> LspBundle.message("lineMarkers.tooltip.has.subtypes")
  }
}

/**
 * Re-requests the targets on click, so the popup shows the current state,
 * and falls back to the targets cached at marker creation.
 */
internal class LspInheritanceMarkerNavigationHandler(
  private val lspClient: LspClientImpl,
  private val file: VirtualFile,
  private val anchorStartOffset: Int,
  private val marker: LspInheritanceMarker,
) : GutterIconNavigationHandler<PsiElement> {

  override fun navigate(e: MouseEvent?, elt: PsiElement?) {
    val project = lspClient.project
    val title = popupTitle(marker)
    LspCoroutineScopeService.getInstance(project).cs.launch {
      val locations = withBackgroundProgress(project, title) {
        freshTargets() ?: marker.targets
      }
      withContext(Dispatchers.EDT) {
        navigateOrShowPopup(lspClient, locations, title, e)
      }
    }
  }

  private suspend fun freshTargets(): List<Location>? {
    val docPos = readAction {
      val document = FileDocumentManager.getInstance().getDocument(file) ?: return@readAction null
      lspClient.documentMapping.getDocumentPosition(file, document, anchorStartOffset)
    } ?: return null

    val targets = when (marker.kind) {
      LspInheritanceMarkerKind.IMPLEMENTED_METHOD, LspInheritanceMarkerKind.OVERRIDDEN_METHOD ->
        lspClient.requestImplementationTargets(docPos.document, docPos.position)
      LspInheritanceMarkerKind.IMPLEMENTED_CLASS, LspInheritanceMarkerKind.SUBCLASSED_CLASS ->
        lspClient.requestSubtypeTargets(docPos.document, docPos.position)
    } ?: return null

    val hostAnchorStart = docPos.document.toHostPosition(docPos.position)
    return lspClient.mapTargetsToHost(docPos.document, hostAnchorStart, targets).takeIf { it.isNotEmpty() }
  }

  private fun popupTitle(marker: LspInheritanceMarker): @NlsContexts.PopupTitle String = when (marker.kind) {
    LspInheritanceMarkerKind.IMPLEMENTED_METHOD, LspInheritanceMarkerKind.IMPLEMENTED_CLASS ->
      LspBundle.message("lineMarkers.popup.title.implementations", marker.symbolName)
    LspInheritanceMarkerKind.OVERRIDDEN_METHOD -> LspBundle.message("lineMarkers.popup.title.overrides", marker.symbolName)
    LspInheritanceMarkerKind.SUBCLASSED_CLASS -> LspBundle.message("lineMarkers.popup.title.subtypes", marker.symbolName)
  }
}
