package org.jetbrains.jewel.bridge.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.asComposeFontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.takeOrElse
import com.intellij.ide.ui.UISettingsUtils
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.impl.FontInfo
import com.intellij.openapi.editor.impl.view.FontLayoutService
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBFont
import java.awt.Font as AwtFont
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import javax.swing.UIManager
import kotlin.math.roundToInt
import org.jetbrains.annotations.ApiStatus
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
@ApiStatus.Experimental
@ExperimentalJewelApi
public fun retrieveDefaultTextStyle(lineHeightMultiplier: Float): TextStyle {
    val lafFont = UIManager.getFont(KEY_LABEL_FONT) ?: keyNotFound(KEY_LABEL_FONT, "Font")
    val font = JBFont.create(lafFont, false)
    val baseLineHeight = computeBaseLineHeightFor(font, treatAsUnscaled = false)
    val textStyle = retrieveTextStyle(KEY_LABEL_FONT, "Label.foreground")
    return textStyle.copy(lineHeight = baseLineHeight * lineHeightMultiplier)
}

/**
 * Retrieves the default text style from the Swing LaF with the requested font attributes.
 *
 * Prefer
 * [`JewelTheme.typography.rememberDefaultTextStyle(...)`][org.jetbrains.jewel.ui.Typography.rememberDefaultTextStyle]
 * in Jewel UI code.
 *
 * @param fontSize The font size to use. If unspecified, the Swing LaF default is used.
 * @param fontWeight The typeface thickness to use. If null, the Swing LaF default is used.
 * @param fontStyle The typeface variant to use. If null, the Swing LaF default is used.
 * @return The default [TextStyle] from the Swing LaF with the requested font attributes.
 */
@OptIn(ExperimentalTextApi::class)
public fun retrieveDefaultTextStyle(
    fontSize: TextUnit,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
): TextStyle {
    val lafFont = UIManager.getFont(KEY_LABEL_FONT) ?: keyNotFound(KEY_LABEL_FONT, "Font")
    val font = JBFont.create(lafFont, false).deriveBridgeFont(fontWeight, fontStyle)
    return retrieveDefaultTextStyle().withBridgeTypographyRequest(
        fontSize,
        fontWeight,
        fontStyle,
        fontFamily = font.asComposeFontFamily(),
    ) { requestedFontSize ->
        val scaledFontSize = requestedFontSize.value * UISettingsUtils.getInstance().currentIdeScale
        computeBaseLineHeightFor(font.deriveFont(scaledFontSize), treatAsUnscaled = false)
    }
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
    val baseLineHeight = computeBaseLineHeightFor(font, treatAsUnscaled = true)
    val computedLineHeight = baseLineHeight * editorColorScheme.lineSpacing * EDITOR_LINE_HEIGHT_FACTOR
    return retrieveDefaultTextStyle()
        .copy(
            color = editorColorScheme.defaultForeground.toComposeColor(),
            fontFamily = font.asComposeFontFamily(),
            fontSize = editorColorScheme.editorFontSize.sp,
            lineHeight = computedLineHeight,
            fontFeatureSettings = if (!editorColorScheme.isUseLigatures) "liga 0, calt 0" else "liga 1, calt 1",
        )
}

/**
 * Retrieves the editor text style from the Swing LaF with the requested font attributes.
 *
 * Prefer
 * [`JewelTheme.typography.rememberEditorTextStyle(...)`][org.jetbrains.jewel.ui.Typography.rememberEditorTextStyle] in
 * Jewel UI code.
 *
 * @param fontSize The font size to use. If unspecified, the editor color scheme default is used.
 * @param fontWeight The typeface thickness to use. If null, the editor color scheme default is used.
 * @param fontStyle The typeface variant to use. If null, the editor color scheme default is used.
 * @return The editor [TextStyle] from the Swing LaF with the requested font attributes.
 */
@OptIn(ExperimentalTextApi::class)
public fun retrieveEditorTextStyle(
    fontSize: TextUnit,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
): TextStyle {
    val editorColorScheme = retrieveEditorColorScheme()
    val font = editorColorScheme.getFont(EditorFontType.PLAIN).deriveBridgeFont(fontWeight, fontStyle)
    return retrieveEditorTextStyle().withBridgeTypographyRequest(
        fontSize,
        fontWeight,
        fontStyle,
        fontFamily = font.asComposeFontFamily(),
    ) { requestedFontSize ->
        computeBaseLineHeightFor(font.deriveFont(requestedFontSize.value), treatAsUnscaled = true) *
            editorColorScheme.lineSpacing *
            EDITOR_LINE_HEIGHT_FACTOR
    }
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
    val baseLineHeight = computeBaseLineHeightFor(font, treatAsUnscaled = true)
    val computedLineHeight = baseLineHeight * editorColorScheme.lineSpacing * EDITOR_LINE_HEIGHT_FACTOR
    return retrieveDefaultTextStyle()
        .copy(
            color = fontColor.toComposeColor(),
            fontFamily = font.asComposeFontFamily(),
            fontSize = fontSize,
            lineHeight = computedLineHeight,
            fontFeatureSettings = if (!editorColorScheme.isUseLigatures) "liga 0, calt 0" else "liga 1, calt 1",
        )
}

/**
 * Retrieves the console text style from the Swing LaF with the requested font attributes.
 *
 * Prefer
 * [`JewelTheme.typography.rememberConsoleTextStyle(...)`][org.jetbrains.jewel.ui.Typography.rememberConsoleTextStyle]
 * in Jewel UI code.
 *
 * @param fontSize The font size to use. If unspecified, the console color scheme default is used.
 * @param fontWeight The typeface thickness to use. If null, the console color scheme default is used.
 * @param fontStyle The typeface variant to use. If null, the console color scheme default is used.
 * @return The console [TextStyle] from the Swing LaF with the requested font attributes.
 */
@OptIn(ExperimentalTextApi::class)
public fun retrieveConsoleTextStyle(
    fontSize: TextUnit,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
): TextStyle {
    val editorColorScheme = retrieveEditorColorScheme()
    val baseFont =
        if (editorColorScheme.isUseEditorFontPreferencesInConsole) {
            editorColorScheme.getFont(EditorFontType.PLAIN)
        } else {
            editorColorScheme.getFont(EditorFontType.CONSOLE_PLAIN)
        }
    val font = baseFont.deriveBridgeFont(fontWeight, fontStyle)
    return retrieveConsoleTextStyle().withBridgeTypographyRequest(
        fontSize,
        fontWeight,
        fontStyle,
        fontFamily = font.asComposeFontFamily(),
    ) { requestedFontSize ->
        computeBaseLineHeightFor(font.deriveFont(requestedFontSize.value), treatAsUnscaled = true) *
            editorColorScheme.lineSpacing *
            EDITOR_LINE_HEIGHT_FACTOR
    }
}

private fun TextStyle.withBridgeTypographyRequest(
    fontSize: TextUnit,
    fontWeight: FontWeight?,
    fontStyle: FontStyle?,
    fontFamily: FontFamily,
    spLineHeight: (TextUnit) -> TextUnit,
): TextStyle =
    copy(
        fontSize = fontSize.takeOrElse { this.fontSize },
        fontWeight = fontWeight ?: this.fontWeight,
        fontStyle = fontStyle ?: this.fontStyle,
        fontFamily = fontFamily,
        lineHeight =
            lineHeight.forBridgeTypographyRequest(
                baseFontSize = this.fontSize,
                requestedFontSize = fontSize,
                recomputeForFontAttributes = fontWeight != null || fontStyle != null,
                spLineHeight = spLineHeight,
            ),
    )

private fun AwtFont.deriveBridgeFont(fontWeight: FontWeight?, fontStyle: FontStyle?): AwtFont {
    var awtStyle = style
    if (fontWeight != null) {
        awtStyle =
            if (fontWeight.weight >= FontWeight.SemiBold.weight) {
                awtStyle or AwtFont.BOLD
            } else {
                awtStyle and AwtFont.BOLD.inv()
            }
    }
    if (fontStyle != null) {
        awtStyle =
            if (fontStyle == FontStyle.Italic) {
                awtStyle or AwtFont.ITALIC
            } else {
                awtStyle and AwtFont.ITALIC.inv()
            }
    }
    return deriveFont(awtStyle)
}

private fun TextUnit.forBridgeTypographyRequest(
    baseFontSize: TextUnit,
    requestedFontSize: TextUnit,
    recomputeForFontAttributes: Boolean,
    spLineHeight: (TextUnit) -> TextUnit,
): TextUnit =
    when {
        requestedFontSize.isUnspecified && recomputeForFontAttributes && baseFontSize.isSp -> spLineHeight(baseFontSize)
        requestedFontSize.isUnspecified -> this
        requestedFontSize.isSp -> spLineHeight(requestedFontSize)
        isSp && baseFontSize.isSp && requestedFontSize.isEm -> {
            val baseLineHeight = if (recomputeForFontAttributes) spLineHeight(baseFontSize) else this
            val lineHeightToFontSizeRatio = baseLineHeight.value / baseFontSize.value
            lineHeightToFontSizeRatio.em
        }
        else -> this
    }

private val image = ImageUtil.createImage(1, 1, TYPE_INT_ARGB)

/**
 * Computes the "base" line height with the same logic used by
 * [com.intellij.openapi.editor.impl.view.EditorView.initMetricsIfNeeded].
 *
 * @param font The font for which to compute the line height.
 * @param treatAsUnscaled When true, the font metrics are treated as "unscaled" (i.e., they do not need compensation for
 *   the IDE scale). This is useful e.g., for editor scheme fonts, which are not scaled, contrary to LaF fonts.
 */
internal fun computeBaseLineHeightFor(font: AwtFont, treatAsUnscaled: Boolean): TextUnit {
    // We need to create a Graphics2D to get its FontRendererContext. The only way we have is
    // by requesting a BufferedImage to create one for us. We can't reuse it because the FRC
    // instance is cached inside the Graphics2D and may lead to incorrect scales being applied.
    val graphics2D = image.createGraphics()
    val fm = FontInfo.getFontMetrics(font, graphics2D.fontRenderContext)

    val heightPx = FontLayoutService.getInstance().getHeight(fm).coerceAtLeast(font.size)
    graphics2D.dispose()

    val unscaledLineHeightValue =
        if (treatAsUnscaled) {
            heightPx
        } else {
            (heightPx / UISettingsUtils.getInstance().currentIdeScale).roundToInt()
        }

    return unscaledLineHeightValue.sp
}
