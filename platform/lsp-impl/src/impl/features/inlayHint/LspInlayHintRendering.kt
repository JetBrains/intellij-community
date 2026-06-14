package com.intellij.platform.lsp.impl.features.inlayHint

import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.SequencePresentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClientDescriptor
import com.intellij.platform.lsp.api.customization.LspInlayHintSupport
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.lsp.impl.features.inlayCommon.LspInlayItem
import com.intellij.platform.lsp.util.getOffsetInDocument
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.eclipse.lsp4j.InlayHint
import org.eclipse.lsp4j.InlayHintLabelPart
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.awt.Cursor

private const val MAX_ALLOWED_INLAY_HINT_LENGTH = 100
private const val MIN_ALLOWED_INLAY_HINT_LENGTH = 1

/**
 * Reads the cached inlay hints for [virtualFile] across all clients that have it open, as [LspInlayItem]s.
 *
 * Calling this also triggers a server request when the cache is stale (see [LspInlayHintsCache]).
 */
@RequiresBackgroundThread
@RequiresReadLock
internal fun collectInlayHintItems(project: Project, virtualFile: VirtualFile): List<LspInlayItem> {
  val clients = LspClientManagerImpl.getInstanceImpl(project).getClientsWithThisFileOpen(virtualFile)
  return clients.flatMap { client ->
    val customizer = client.descriptor.lspCustomization.inlayHintCustomizer
    if (customizer is LspInlayHintSupport) {
      val raw = customizer.getMaxInlayHintChars()
      val maxChars = raw.coerceIn(MIN_ALLOWED_INLAY_HINT_LENGTH, MAX_ALLOWED_INLAY_HINT_LENGTH)
      client.getInlayHints(virtualFile)
        .filter { customizer.shouldDisplayInlayHint(virtualFile, it.highlightingInfo) }
        .map { LspInlayHintItem(project, client.descriptor, it.highlightingInfo, it.textRange.startOffset, maxChars) }
    }
    else {
      emptyList()
    }
  }
}

private class LspInlayHintItem(
  private val project: Project,
  private val descriptor: LspClientDescriptor,
  private val inlayHint: InlayHint,
  override val offset: Int,
  private val maxChars: Int,
) : LspInlayItem {

  override val identity: Any = LspInlayHintIdentity(descriptor, inlayHint.label, maxChars)

  override fun buildPresentation(editor: Editor, factory: PresentationFactory): InlayPresentation {
    val presentation = buildInlayPresentation(factory, project, descriptor, inlayHint, maxChars)
    return factory.roundWithBackground(presentation)
  }
}

/**
 * The rendered identity of an inlay hint: everything [buildInlayPresentation] reads, and nothing else.
 *
 * Deliberately excludes the text range / [InlayHint.position]: an edit that only shifts a hint's offset moves the
 * existing inlay automatically, so it must still match (and be reused) rather than be recreated. [Either] and
 * [InlayHintLabelPart] both have value-based `equals`, so two structurally equal labels compare equal.
 */
private data class LspInlayHintIdentity(
  val descriptor: LspClientDescriptor,
  val label: Either<String, List<InlayHintLabelPart>>,
  val maxChars: Int,
)

private fun buildInlayPresentation(
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
