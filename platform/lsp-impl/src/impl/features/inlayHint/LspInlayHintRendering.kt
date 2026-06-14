package com.intellij.platform.lsp.impl.features.inlayHint

import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.SequencePresentation
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClientDescriptor
import com.intellij.platform.lsp.api.customization.LspInlayHintSupport
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.lsp.impl.features.highlightingCommon.LspCachedHighlighting
import com.intellij.platform.lsp.util.getOffsetInDocument
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.eclipse.lsp4j.InlayHint
import org.eclipse.lsp4j.InlayHintLabelPart
import org.eclipse.lsp4j.Range
import java.awt.Cursor

private const val MAX_ALLOWED_INLAY_HINT_LENGTH = 100
private const val MIN_ALLOWED_INLAY_HINT_LENGTH = 1

/**
 * Shared rendering/data-collection for LSP inlay hints, used by both [LspInlayHintsProvider] (as a request trigger)
 * and [LspInlayHintsApplier] (which paints them out-of-band onto the editor [com.intellij.openapi.editor.InlayModel]).
 */
internal data class LspInlayHintData(
  val descriptor: LspClientDescriptor,
  val cached: LspCachedHighlighting<InlayHint>,
  val maxChars: Int,
)

/**
 * Reads the cached inlay hints for [virtualFile] across all clients that have it open.
 *
 * Calling this also triggers a server request when the cache is stale (see [LspInlayHintsCache]).
 */
@RequiresBackgroundThread
@RequiresReadLock
internal fun collectInlayHintData(project: Project, virtualFile: VirtualFile): List<LspInlayHintData> {
  val clients = LspClientManagerImpl.getInstanceImpl(project).getClientsWithThisFileOpen(virtualFile)
  return clients.flatMap { client ->
    val customizer = client.descriptor.lspCustomization.inlayHintCustomizer
    if (customizer is LspInlayHintSupport) {
      val raw = customizer.getMaxInlayHintChars()
      val maxChars = raw.coerceIn(MIN_ALLOWED_INLAY_HINT_LENGTH, MAX_ALLOWED_INLAY_HINT_LENGTH)
      client.getInlayHints(virtualFile)
        .filter { customizer.shouldDisplayInlayHint(virtualFile, it.highlightingInfo) }
        .map { LspInlayHintData(client.descriptor, it, maxChars) }
    }
    else {
      emptyList()
    }
  }
}

internal fun buildInlayPresentation(
  factory: PresentationFactory,
  project: Project,
  descriptor: LspClientDescriptor,
  inlayHint: InlayHint,
  maxChars: Int,
): InlayPresentation {
  val label = inlayHint.label
  val ellipsis = "..."
  // If the label consists of parts, compose an interactive presentation per part with truncation by total length
  val parts: List<InlayHintLabelPart>? = label.right
  if (parts != null) {
    val cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    val subPresentations = mutableListOf<InlayPresentation>()
    var currentInlayHintLength = 0

    for (part in parts) {
      val value = part.value
      currentInlayHintLength += value.length
      if (currentInlayHintLength > maxChars) {
        // Do not add this part; append ellipsis to indicate truncation and stop
        subPresentations += factory.smallText(ellipsis)
        break
      }

      var inlayPresentation: InlayPresentation = factory.smallText(value)

      // Make clickable if location is present
      val location = part.location
      if (location != null) {
        val clickable = factory.reference(inlayPresentation) {
          navigateTo(descriptor, project, location.uri, location.range)
        }
        inlayPresentation = factory.withCursorOnHoverWhenControlDown(clickable, cursor)
      }

      subPresentations += inlayPresentation
    }

    return SequencePresentation(subPresentations)
  }

  // If the label consists of a single text part, truncate it by total length
  val hintText = label.left ?: ""
  val shownText = if (hintText.length > maxChars) hintText.take(maxChars) + ellipsis else hintText
  return factory.smallText(shownText)
}

private fun navigateTo(descriptor: LspClientDescriptor, project: Project, targetUri: String, targetRange: Range?) {
  val targetFile = descriptor.findFileByUri(targetUri) ?: return

  val editor = FileEditorManager.getInstance(project).openTextEditor(
    OpenFileDescriptor(project, targetFile), true
  ) ?: return

  if (targetRange != null) {
    val document = editor.document
    val offset = getOffsetInDocument(document, targetRange.start) ?: return
    editor.caretModel.moveToOffset(offset)
  }
}
