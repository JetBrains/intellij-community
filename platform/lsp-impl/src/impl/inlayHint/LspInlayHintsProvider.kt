package com.intellij.platform.lsp.impl.inlayHint

import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.NoSettings
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.SequencePresentation
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspServerDescriptor
import com.intellij.platform.lsp.api.customization.LspInlayHintSupport
import com.intellij.platform.lsp.impl.LspServerManagerImpl
import com.intellij.platform.lsp.impl.highlightingCommon.LspCachedHighlighting
import com.intellij.platform.lsp.util.getOffsetInDocument
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.eclipse.lsp4j.InlayHint
import org.eclipse.lsp4j.InlayHintLabelPart
import org.eclipse.lsp4j.Range
import java.awt.Cursor
import javax.swing.JComponent
import javax.swing.JPanel

private val lspInlayHintsKey: SettingsKey<NoSettings> = SettingsKey("lsp.inlay.hints")

private const val MAX_ALLOWED_INLAY_HINT_LENGTH = 100
private const val MIN_ALLOWED_INLAY_HINT_LENGTH = 1

/**
 * @see com.intellij.platform.lsp.impl.inlayHintColor.LspColorInlayHintsProvider
 */
internal class LspInlayHintsProvider : InlayHintsProvider<NoSettings>, DumbAware {

  override val isVisibleInSettings: Boolean = false

  override val name: String = "LSP-based inlay hints" // doesn't show up in UI

  override val key: SettingsKey<NoSettings> = lspInlayHintsKey

  override fun createSettings(): NoSettings = NoSettings()

  override val previewText: String? = null

  override fun createConfigurable(settings: NoSettings): ImmediateConfigurable = object : ImmediateConfigurable {
    override fun createComponent(listener: ChangeListener): JComponent = JPanel()
  }

  override fun getCollectorFor(file: PsiFile, editor: Editor, settings: NoSettings, sink: InlayHintsSink): InlayHintsCollector? {
    val virtualFile = file.virtualFile ?: return null
    if (virtualFile is VirtualFileWindow || !virtualFile.isInLocalFileSystem) return null

    val servers = LspServerManagerImpl.getInstanceImpl(file.project).getServersWithThisFileOpen(virtualFile)
    val inlayHints: List<InlayHintData> = servers.flatMap { server ->
      val customizer = server.descriptor.lspCustomization.inlayHintCustomizer
      if (customizer is LspInlayHintSupport) {
        val raw = customizer.getMaxInlayHintChars()
        val maxChars = raw.coerceIn(MIN_ALLOWED_INLAY_HINT_LENGTH, MAX_ALLOWED_INLAY_HINT_LENGTH)
        server.getInlayHints(virtualFile)
          .filter { customizer.shouldDisplayInlayHint(virtualFile, it.highlightingInfo) }
          .map { InlayHintData(server.descriptor, it, maxChars) }
      }
      else {
        emptyList()
      }
    }

    if (inlayHints.isEmpty()) return null

    return object : InlayHintsCollector {
      override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
        if (element !== file) return true

        for (hintData in inlayHints) {
          val factory = PresentationFactory(editor)
          val presentation: InlayPresentation = buildPresentation(
            factory, file.project,
            hintData.descriptor,
            hintData.cached.highlightingInfo,
            hintData.maxChars
          )
          val styledPresentation = factory.roundWithBackground(presentation)
          sink.addInlineElement(hintData.cached.textRange.startOffset, false, styledPresentation, false)
        }

        return true
      }
    }
  }

  private fun buildPresentation(
    factory: PresentationFactory,
    project: Project,
    descriptor: LspServerDescriptor,
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

  private fun navigateTo(descriptor: LspServerDescriptor, project: Project, targetUri: String, targetRange: Range?) {
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
}

private data class InlayHintData(
  val descriptor: LspServerDescriptor,
  val cached: LspCachedHighlighting<InlayHint>,
  val maxChars: Int,
)