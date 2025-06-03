package org.jetbrains.jewel.bridge.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.platform.asComposeFontFamily
import androidx.compose.ui.unit.sp
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.impl.FontInfo
import com.intellij.openapi.editor.impl.view.FontLayoutService
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBFont
import java.awt.Font
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import javax.swing.UIManager
import org.jetbrains.jewel.bridge.keyNotFound
import org.jetbrains.jewel.bridge.retrieveEditorColorScheme
import org.jetbrains.jewel.bridge.retrieveTextStyle
import org.jetbrains.jewel.bridge.safeValue
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

private const val KEY_LABEL_FONT = "Label.font"

/**
 * Retrieves the default text style from the Swing LaF.
 *
 * This is the font set in _Settings | Appearance & Behavior | Appearance_, and defaults to 13px Inter.
 */
public fun retrieveDefaultTextStyle(): TextStyle = retrieveDefaultTextStyle(1.0f)

/**
 * Retrieves the default text style from the Swing LaF, applying a line height multiplier.
 *
 * This is the font set in _Settings | Appearance & Behavior | Appearance_, and defaults to 13px Inter.
 */
@ExperimentalJewelApi
public fun retrieveDefaultTextStyle(lineHeightMultiplier: Float): TextStyle {
    val lafFont = UIManager.getFont(KEY_LABEL_FONT) ?: keyNotFound(KEY_LABEL_FONT, "Font")
    val font = JBFont.create(lafFont, false)
    val baseLineHeight = computeBaseLineHeightFor(font)
    val textStyle = retrieveTextStyle(KEY_LABEL_FONT, "Label.foreground")
    return textStyle.copy(lineHeight = (baseLineHeight * lineHeightMultiplier).sp)
}

/**
 * Compensates for some difference in how line height is applied between Skia and Swing. Matches perfectly with the
 * default editor font.
 */
private const val EDITOR_LINE_HEIGHT_FACTOR = 0.85f

/**
 * Retrieves the editor style from the Swing LaF.
 *
 * This is the font set in _Settings | Editor | Font_, or _Settings | Editor | Color Scheme | Color Scheme Font_, and
 * defaults to 13px JetBrains Mono, with a line height factor of 1.2.
 */
@OptIn(ExperimentalTextApi::class)
public fun retrieveEditorTextStyle(): TextStyle {
    val editorColorScheme = retrieveEditorColorScheme()

    val font = editorColorScheme.getFont(EditorFontType.PLAIN)
    val baseLineHeight = computeBaseLineHeightFor(font)
    val computedLineHeight = baseLineHeight * editorColorScheme.lineSpacing
    return retrieveDefaultTextStyle()
        .copy(
            color = editorColorScheme.defaultForeground.toComposeColor(),
            fontFamily = font.asComposeFontFamily(),
            fontSize = editorColorScheme.editorFontSize.sp,
            lineHeight = (computedLineHeight * EDITOR_LINE_HEIGHT_FACTOR).coerceAtLeast(1f).sp,
            fontFeatureSettings = if (!editorColorScheme.isUseLigatures) "liga 0" else "liga 1",
        )
}

/**
 * Retrieves the editor style from the Swing LaF.
 *
 * This is the font set in _Settings | Editor | Color Scheme | Console Font_, and defaults to the same as the
 * [editor font][retrieveEditorTextStyle].
 */
@OptIn(ExperimentalTextApi::class)
public fun retrieveConsoleTextStyle(): TextStyle {
    val editorColorScheme = retrieveEditorColorScheme()
    if (editorColorScheme.isUseEditorFontPreferencesInConsole) return retrieveEditorTextStyle()

    val fontSize = editorColorScheme.consoleFontSize.safeValue().sp
    val fontColor =
        editorColorScheme.getColor(ColorKey.createColorKey("BLOCK_TERMINAL_DEFAULT_FOREGROUND"))
            ?: editorColorScheme.defaultForeground

    val font = editorColorScheme.getFont(EditorFontType.CONSOLE_PLAIN)
    val baseLineHeight = computeBaseLineHeightFor(font)
    return retrieveDefaultTextStyle()
        .copy(
            color = fontColor.toComposeColor(),
            fontFamily = font.asComposeFontFamily(),
            fontSize = fontSize,
            lineHeight = (baseLineHeight * editorColorScheme.lineSpacing).coerceAtLeast(1f).sp,
            fontFeatureSettings = if (!editorColorScheme.isUseLigatures) "liga 0" else "liga 1",
        )
}

private val image = ImageUtil.createImage(1, 1, TYPE_INT_ARGB)

/**
 * Computes the "base" line height with the same logic used by
 * [com.intellij.openapi.editor.impl.view.EditorView.initMetricsIfNeeded].
 */
internal fun computeBaseLineHeightFor(font: Font): Int {
    // We need to create a Graphics2D to get its FontRendererContext. The only way we have is
    // by requesting a BufferedImage to create one for us. We can't reuse it because the FRC
    // instance is cached inside the Graphics2D and may lead to incorrect scales being applied.
    val graphics2D = image.createGraphics()
    val fm = FontInfo.getFontMetrics(font, graphics2D.fontRenderContext)

    return FontLayoutService.getInstance().getHeight(fm)
}
