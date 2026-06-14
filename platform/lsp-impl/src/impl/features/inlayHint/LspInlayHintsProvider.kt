package com.intellij.platform.lsp.impl.features.inlayHint

import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.NoSettings
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import javax.swing.JComponent
import javax.swing.JPanel

private val lspInlayHintsKey: SettingsKey<NoSettings> = SettingsKey("lsp.inlay.hints")

/**
 * @see com.intellij.platform.lsp.impl.features.inlayHintColor.LspColorInlayHintsProvider
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

    val inlayHints = collectInlayHintData(file.project, virtualFile)
    if (inlayHints.isEmpty()) return null

    return object : InlayHintsCollector {
      override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
        if (element !== file) return true

        for (hintData in inlayHints) {
          val factory = PresentationFactory(editor)
          val presentation = buildInlayPresentation(
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
}
