package com.intellij.platform.lsp.impl.features.inlayHintColor

import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.ScaleAwarePresentationFactory
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.JBUI
import java.awt.Color
import kotlin.math.roundToInt

/** The 0..255 components that actually determine the rendered swatch (and serve as its diff identity). */
internal data class LspColorRgba(val red: Int, val green: Int, val blue: Int, val alpha: Int)

internal fun buildColorPresentation(editor: Editor, factory: PresentationFactory, rgba: LspColorRgba): InlayPresentation {
  // 12 is the standard size for inlay icons
  val scaledIconSize = (12 * JBUIScale.getFontScale(editor.colorsScheme.editorFontSize2D)).roundToInt()
  val iconArc = JBUI.scale(4)
  val borderColor = editor.colorsScheme.getColor(EditorColors.INDENT_GUIDE_COLOR)
  val scaleAwareFactory = ScaleAwarePresentationFactory(editor, factory)

  @Suppress("UseJBColor")
  val color = Color(rgba.red, rgba.green, rgba.blue, rgba.alpha)
  val scaledIcon = ColorIcon(scaledIconSize, scaledIconSize, scaledIconSize, scaledIconSize, color, borderColor, iconArc)
  // scaleAwareFactory.icon(unscaledIcon) didn't work for this use case at the moment of writing (Zoom IDE looked not great for this icon)
  // factory.icon(scaledIcon) works great because the icon already scaled according to both the scale factor and the font size
  var presentation: InlayPresentation = factory.icon(scaledIcon)
  presentation = scaleAwareFactory.lineCentered(presentation)
  presentation = scaleAwareFactory.inset(presentation, left = 2, right = 2)
  presentation = factory.withTooltip(getColorTooltip(rgba), presentation)
  return presentation
}

private fun getColorTooltip(rgba: LspColorRgba): @NlsSafe String {
  val (r, g, b, a) = rgba
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
