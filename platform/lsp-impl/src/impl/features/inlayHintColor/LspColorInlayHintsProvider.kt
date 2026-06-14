package com.intellij.platform.lsp.impl.features.inlayHintColor

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
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.roundToInt

private val lspColorInlayHintsKey: SettingsKey<NoSettings> = SettingsKey("lsp.color.inlay.hints")

/**
 * @see com.intellij.platform.lsp.impl.features.inlayHint.LspInlayHintsProvider
 */
internal class LspColorInlayHintsProvider : InlayHintsProvider<NoSettings>, DumbAware {

  override val isVisibleInSettings: Boolean = false

  override val name: String = "LSP-based color hints" // doesn't show up in UI

  override val key: SettingsKey<NoSettings> = lspColorInlayHintsKey

  override fun createSettings(): NoSettings = NoSettings()

  override val previewText: String? = null

  override fun createConfigurable(settings: NoSettings): ImmediateConfigurable = object : ImmediateConfigurable {
    override fun createComponent(listener: ChangeListener): JComponent = JPanel()
  }

  override fun getCollectorFor(file: PsiFile, editor: Editor, settings: NoSettings, sink: InlayHintsSink): InlayHintsCollector? {
    val virtualFile = file.virtualFile ?: return null
    if (virtualFile is VirtualFileWindow || !virtualFile.isInLocalFileSystem) return null

    val colorInfos = LspClientManagerImpl.getInstanceImpl(file.project)
      .getClientsWithThisFileOpen(virtualFile)
      .flatMap { it.getColorInfos(virtualFile) }

    if (colorInfos.isEmpty()) return null

    return object : InlayHintsCollector {
      override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
        if (element !== file) return true

        val factory = PresentationFactory(editor)

        for (colorInfo in colorInfos) {
          val r: Int = (colorInfo.highlightingInfo.red * 255).roundToInt()
          val g: Int = (colorInfo.highlightingInfo.green * 255).roundToInt()
          val b: Int = (colorInfo.highlightingInfo.blue * 255).roundToInt()
          val a: Int = (colorInfo.highlightingInfo.alpha * 255).roundToInt()
          val rgbRange = 0..255
          if (r !in rgbRange || g !in rgbRange || b !in rgbRange || a !in rgbRange) continue // invalid color
          val rgba = LspColorRgba(r, g, b, a)
          val presentation = buildColorPresentation(editor, factory, rgba)
          sink.addInlineElement(colorInfo.textRange.startOffset, false, presentation, false)
        }

        return true
      }
    }
  }
}
