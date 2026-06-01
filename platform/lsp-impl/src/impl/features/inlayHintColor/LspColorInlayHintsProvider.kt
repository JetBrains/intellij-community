package com.intellij.platform.lsp.impl.features.inlayHintColor

import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.NoSettings
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.ScaleAwarePresentationFactory
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.lsp.impl.LspServerManagerImpl
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.JBUI
import java.awt.Color
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

    val colorInfos = LspServerManagerImpl.getInstanceImpl(file.project)
      .getServersWithThisFileOpen(virtualFile)
      .flatMap { it.getColorInfos(virtualFile) }

    if (colorInfos.isEmpty()) return null

    return object : InlayHintsCollector {
      override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
        if (element !== file) return true

        // 12 is the standard size for inlay icons
        val scaledIconSize = (12 * JBUIScale.getFontScale(editor.colorsScheme.editorFontSize2D)).roundToInt()
        val iconArc = JBUI.scale(4)
        val borderColor = editor.colorsScheme.getColor(EditorColors.INDENT_GUIDE_COLOR)
        val factory = PresentationFactory(editor)
        val scaleAwareFactory = ScaleAwarePresentationFactory(editor, factory)

        for (colorInfo in colorInfos) {
          val r: Int = (colorInfo.highlightingInfo.red * 255).roundToInt()
          val g: Int = (colorInfo.highlightingInfo.green * 255).roundToInt()
          val b: Int = (colorInfo.highlightingInfo.blue * 255).roundToInt()
          val a: Int = (colorInfo.highlightingInfo.alpha * 255).roundToInt()
          val rgbRange = 0..255
          if (r !in rgbRange || g !in rgbRange || b !in rgbRange || a !in rgbRange) continue // invalid color

          @Suppress("UseJBColor")
          val color = Color(r, g, b, a)
          val scaledIcon = ColorIcon(scaledIconSize, scaledIconSize, scaledIconSize, scaledIconSize, color, borderColor, iconArc)
          // scaleAwareFactory.icon(unscaledIcon) didn't work for this use case at the moment of writing (Zoom IDE looked not great for this icon)
          // factory.icon(scaledIcon) works great because the icon already scaled according to both the scale factor and the font size
          var presentation: InlayPresentation = factory.icon(scaledIcon)
          presentation = scaleAwareFactory.lineCentered(presentation)
          presentation = scaleAwareFactory.inset(presentation, left = 2, right = 2)
          presentation = factory.withTooltip(getTooltip(r, g, b, a), presentation)

          sink.addInlineElement(colorInfo.textRange.startOffset, false, presentation, false)
        }

        return true
      }
    }
  }

  private fun getTooltip(r: Int, g: Int, b: Int, a: Int): @NlsSafe String {
    val slashAndAlfaPercentage = when (a) {
      255 -> ""
      0 -> " / 0"
      else -> " / ${(a * 100.0 / 255.0).roundToInt()}%"
    }

    val hex = buildString {
      append("#")
      append(r.toString(16).padStart(2, '0'))
      append(g.toString(16).padStart(2, '0'))
      append(b.toString(16).padStart(2, '0'))
      if (a != 255) append(a.toString(16).padStart(2, '0'))
    }

    return "rgb($r $g $b$slashAndAlfaPercentage)\n$hex"
  }
}
