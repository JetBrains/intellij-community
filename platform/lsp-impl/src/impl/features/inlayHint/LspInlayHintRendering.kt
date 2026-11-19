package com.intellij.platform.lsp.impl.features.inlayHint

import com.intellij.codeInsight.hints.InlayHintsSettings
import com.intellij.codeInsight.hints.InlayPresentationFactory.HoverListener
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.SequencePresentation
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorOpenRequest
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient.Companion.DEFAULT_REQUEST_TIMEOUT_MS
import com.intellij.platform.lsp.api.LspClientDescriptor
import com.intellij.platform.lsp.api.customization.LspInlayHintSupport
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.lsp.impl.LspCoroutineScopeService
import com.intellij.platform.lsp.impl.features.documentation.createLspDocumentationData
import com.intellij.platform.lsp.impl.features.inlayCommon.LspInlayItem
import com.intellij.platform.lsp.impl.supportsInlayHintResolve
import com.intellij.platform.lsp.util.getOffsetInDocument
import com.intellij.ui.LightweightHint
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.xml.util.XmlStringUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.eclipse.lsp4j.InlayHint
import org.eclipse.lsp4j.InlayHintLabelPart
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.awt.Cursor
import java.awt.Point
import java.awt.event.MouseEvent
import kotlin.time.Duration.Companion.milliseconds

private const val MAX_ALLOWED_INLAY_HINT_LENGTH = 100
private const val MIN_ALLOWED_INLAY_HINT_LENGTH = 1

/**
 * Reads the cached inlay hints for [virtualFile] across all clients that have it open, as [LspInlayItem]s.
 *
 * Calling this also triggers a server request when the cache is stale (see [LspInlayHintsCache]).
 */
@RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
@RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
internal fun collectInlayHintItems(project: Project, virtualFile: VirtualFile): List<LspInlayItem> {
  // The LSP client paints the hints out of band, so the global inlay hints switch does not reach them.
  // Read the switch here so the LSP hints follow it, like the daemon hints do.
  if (!InlayHintsSettings.instance().hintsEnabledGlobally()) return emptyList()

  val clients = LspClientManagerImpl.getInstanceImpl(project).getClientsWithThisFileOpen(virtualFile)
  return clients.flatMap { client ->
    val customizer = client.descriptor.lspCustomization.inlayHintCustomizer
    if (customizer is LspInlayHintSupport) {
      val raw = customizer.getMaxInlayHintChars()
      val maxChars = raw.coerceIn(MIN_ALLOWED_INLAY_HINT_LENGTH, MAX_ALLOWED_INLAY_HINT_LENGTH)
      val supportsResolve = client.supportsInlayHintResolve(virtualFile)
      client.highlightingCacheRegistry.inlayHintsCache.getHighlightings(virtualFile)
        .filter { customizer.shouldDisplayInlayHint(virtualFile, it.highlightingInfo) }
        .map { LspInlayHintItem(project, client, virtualFile, it.highlightingInfo, it.textRange.startOffset, maxChars, supportsResolve) }
    }
    else {
      emptyList()
    }
  }
}

private class LspInlayHintItem(
  private val project: Project,
  private val lspClient: LspClientImpl,
  private val file: VirtualFile,
  private var inlayHint: InlayHint,
  override val offset: Int,
  private val maxChars: Int,
  private val supportsResolve: Boolean,
) : LspInlayItem {
  private var resolvedHint: InlayHint? = null

  override val identity: Any = LspInlayHintIdentity(lspClient.descriptor, inlayHint.label, maxChars, supportsResolve)

  override fun buildPresentation(editor: Editor, factory: PresentationFactory): InlayPresentation {
    val presentation = buildInlayPresentation(factory, inlayHint, maxChars, supportsResolve,
                                            tooltipListener = { partIndex -> TooltipListener(editor, factory, partIndex) }) { partIndex ->
      navigateToLabelPart(editor, partIndex)
    }
    return factory.roundWithBackground(presentation)
  }

  override fun updateFrom(newItem: LspInlayItem) {
    val newHint = (newItem as LspInlayHintItem).inlayHint
    if (newHint !== inlayHint) {
      inlayHint = newHint
      resolvedHint = null
    }
  }

  @RequiresReadLock
  private fun tooltipHtml(tooltip: MarkupContent): @NlsSafe String {
    return XmlStringUtil.wrapInHtml(createLspDocumentationData(tooltip).toHtml(project))
  }

  private inner class TooltipListener(
    private val editor: Editor,
    private val factory: PresentationFactory,
    private val partIndex: Int?,
  ) : HoverListener {
    private var inlay: Inlay<*>? = null
    private var event: MouseEvent? = null
    private var shown: LightweightHint? = null
    private var job: Job? = null

    override fun onHover(event: MouseEvent, translated: Point) {
      if (editor.isDisposed) return
      this.event = event
      if (shown?.isVisible == true || job?.isActive == true) return
      val tooltip = getInlayHintTooltip(resolvedHint ?: inlayHint, partIndex) ?: return
      val hoveredInlay = inlay ?: editor.inlayModel.getElementAt(event.point)?.also {
        inlay = it
        Disposer.register(it) { onHoverFinished() }
      } ?: return
      if (!hoveredInlay.isValid) return

      job = LspCoroutineScopeService.getInstance(project).cs.launch {
        val html = readAction { tooltipHtml(tooltip) }
        withContext(Dispatchers.EDT) {
          val lastEvent = this@TooltipListener.event ?: return@withContext
          if (!editor.isDisposed && hoveredInlay.isValid &&
              editor.contentComponent.isShowing && shown?.isVisible != true) {
            shown = factory.showTooltip(lastEvent, html)
          }
        }
      }
    }

    override fun onHoverFinished() {
      event = null
      job?.cancel()
      job = null
      shown?.hide()
      shown = null
    }
  }

  private fun navigateToLabelPart(editor: Editor, partIndex: Int) {
    val hint = inlayHint
    val resolved = resolvedHint
    val location = (resolved ?: hint).label.right?.getOrNull(partIndex)?.location
    if (location != null) {
      navigateTo(lspClient.descriptor, project, location.uri, location.range)
      return
    }
    if (resolved != null) return

    LspCoroutineScopeService.getInstance(project).cs.launch {
      val newResolved = resolveInlayHint(lspClient, file, hint) ?: return@launch
      withContext(Dispatchers.EDT) {
        if (inlayHint === hint) {
          resolvedHint = newResolved
        }
        val resolvedLocation = newResolved.label.right?.getOrNull(partIndex)?.location
        if (resolvedLocation != null && !editor.isDisposed) {
          navigateTo(lspClient.descriptor, project, resolvedLocation.uri, resolvedLocation.range)
        }
      }
    }
  }
}

/** Returns the part tooltip, or the hint tooltip when the part has none. Empty text is a supplied tooltip. */
internal fun getInlayHintTooltip(inlayHint: InlayHint, partIndex: Int?): MarkupContent? {
  val tooltip = partIndex?.let { inlayHint.label.right?.getOrNull(it)?.tooltip } ?: inlayHint.tooltip ?: return null
  return tooltip.right ?: MarkupContent(MarkupKind.PLAINTEXT, tooltip.left)
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
  val supportsResolve: Boolean,
)

private fun buildInlayPresentation(
  factory: PresentationFactory,
  inlayHint: InlayHint,
  maxChars: Int,
  supportsResolve: Boolean,
  tooltipListener: (partIndex: Int?) -> HoverListener,
  onLabelPartClick: (partIndex: Int) -> Unit,
): InlayPresentation {
  val label = inlayHint.label
  val ellipsis = "..."
  // If the label consists of parts, compose an interactive presentation per part with truncation by total length
  val parts: List<InlayHintLabelPart>? = label.right
  if (parts != null) {
    val cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    val subPresentations = mutableListOf<InlayPresentation>()
    var currentInlayHintLength = 0

    for ((partIndex, part) in parts.withIndex()) {
      val value = part.value
      currentInlayHintLength += value.length
      if (currentInlayHintLength > maxChars) {
        // Do not add this part; append ellipsis to indicate truncation and stop
        subPresentations += factory.onHover(factory.smallText(ellipsis), tooltipListener(null))
        break
      }

      var inlayPresentation: InlayPresentation = factory.smallText(value)

      // Make clickable if location is present or can be resolved
      if (part.location != null || supportsResolve) {
        val clickable = factory.reference(inlayPresentation) {
          onLabelPartClick(partIndex)
        }
        inlayPresentation = factory.withCursorOnHoverWhenControlDown(clickable, cursor)
      }

      subPresentations += factory.onHover(inlayPresentation, tooltipListener(partIndex))
    }

    return SequencePresentation(subPresentations)
  }

  // If the label consists of a single text part, truncate it by total length
  val hintText = label.left ?: ""
  val shownText = if (hintText.length > maxChars) hintText.take(maxChars) + ellipsis else hintText
  return factory.onHover(factory.smallText(shownText), tooltipListener(null))
}

/**
 * Sends [inlayHint/resolve](https://microsoft.github.io/language-server-protocol/specification/#inlayHint_resolve).
 * Returns `null` when the server does not support it, fails, or does not answer in [DEFAULT_REQUEST_TIMEOUT_MS] milliseconds.
 */
private suspend fun resolveInlayHint(client: LspClientImpl, file: VirtualFile, inlayHint: InlayHint): InlayHint? {
  if (!client.supportsInlayHintResolve(file)) return null
  try {
    return withTimeoutOrNull(DEFAULT_REQUEST_TIMEOUT_MS.milliseconds) {
      client.sendRequest { it.textDocumentService.resolveInlayHint(inlayHint) }
    }
  }
  catch (e: Exception) {
    rethrowControlFlowException(e)
    client.logWarn("Inlay hint resolve failed for ${file.url}", e)
    return null
  }
}

private fun navigateTo(descriptor: LspClientDescriptor, project: Project, targetUri: String, targetRange: Range?) {
  val targetFile = descriptor.findFileByUri(targetUri) ?: return

  val fileEditorManager = FileEditorManager.getInstance(project)
  val openDescriptor = OpenFileDescriptor(project, targetFile)
  fileEditorManager.requestOpenTextEditor(openDescriptor, FileEditorOpenRequest.fromDescriptor(openDescriptor)
    .withRequestFocus(true)).thenAccept { editor ->
    // the callback runs on the EDT, so it may touch the editor directly
    if (editor != null && targetRange != null) {
      val offset = getOffsetInDocument(editor.document, targetRange.start)
      if (offset != null) {
        editor.caretModel.moveToOffset(offset)
      }
    }
  }
}
