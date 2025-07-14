@file:Suppress("RedundantSuppression")

package org.jetbrains.jewel.ui.component

import androidx.annotation.Px
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Typography.DefaultLineHeightMultiplier
import org.jetbrains.jewel.ui.component.Typography.editorTextStyle

/**
 * A quick way to obtain text styles derived from [the default `TextStyle`][JewelTheme.defaultTextStyle]. These match
 * the functionality provided by `JBFont` in the IntelliJ Platform.
 */
@Deprecated(
    "Use JewelTheme.typography instead",
    ReplaceWith(
        "JewelTheme.typography",
        "org.jetbrains.jewel.foundation.theme.JewelTheme",
        "org.jetbrains.jewel.ui.typography",
    ),
)
public object Typography {
    /** The text style to use for labels. Identical to [JewelTheme.defaultTextStyle]. */
    @Composable public fun labelTextStyle(): TextStyle = JewelTheme.defaultTextStyle

    /** The text size to use for labels. Identical to the size set in [JewelTheme.defaultTextStyle]. */
    @Composable public fun labelTextSize(): TextUnit = JewelTheme.defaultTextStyle.fontSize

    @Suppress("ktlint:standard:property-naming", "ConstPropertyName")
    /**
     * The factor to use when creating a [TextStyle] with a changed font size:
     * ```kotlin
     * myTextStyle.copy(
     *     fontSize = newFontSize,
     *     lineHeight = newFontSize * Typography.DefaultLineHeightMultiplier,
     * )
     * ```
     *
     * You should use [TextStyle.copyWithSize] to create copies of a [TextStyle] with a changed font size, as that
     * function will automatically apply the correct line height, too.
     *
     * @see TextStyle.copyWithSize
     */
    @Deprecated("No longer used")
    public const val DefaultLineHeightMultiplier: Float = 1.3f

    @Suppress("ktlint:standard:property-naming", "ConstPropertyName")
    /**
     * The factor to use when creating an editor [TextStyle] with a changed font size:
     * ```kotlin
     * myTextStyle.copy(
     *     fontSize = newFontSize,
     *     lineHeight = newFontSize * Typography.DefaultLineHeightMultiplier,
     * )
     * ```
     *
     * You should use [TextStyle.copyWithSize] to create copies of a [TextStyle] with a changed font size, as that
     * function will automatically apply the correct line height, too.
     */
    @Deprecated("No longer used")
    public const val EditorLineHeightMultiplier: Float = 1.5f

    /** The text style to use for h0 titles. Derived from [JewelTheme.defaultTextStyle]. */
    @Composable
    public fun h0TextStyle(): TextStyle =
        JewelTheme.defaultTextStyle.copy(fontSize = labelTextSize() + 12.sp, fontWeight = FontWeight.Bold)

    /** The text style to use for h1 titles. Derived from [JewelTheme.defaultTextStyle]. */
    @Composable
    public fun h1TextStyle(): TextStyle =
        JewelTheme.defaultTextStyle.copy(fontSize = labelTextSize() + 9.sp, fontWeight = FontWeight.Bold)

    /** The text style to use for h2 titles. Derived from [JewelTheme.defaultTextStyle]. */
    @Composable
    public fun h2TextStyle(): TextStyle = JewelTheme.defaultTextStyle.copy(fontSize = labelTextSize() + 5.sp)

    /** The text style to use for h3 titles. Derived from [JewelTheme.defaultTextStyle]. */
    @Composable
    public fun h3TextStyle(): TextStyle = JewelTheme.defaultTextStyle.copy(fontSize = labelTextSize() + 3.sp)

    /** The text style to use for h4 titles. Derived from [JewelTheme.defaultTextStyle]. */
    @Composable
    public fun h4TextStyle(): TextStyle =
        JewelTheme.defaultTextStyle.copy(fontSize = labelTextSize() + 1.sp, fontWeight = FontWeight.Bold)

    /** The text style used for code editors. Usually is a monospaced font. */
    @Composable public fun editorTextStyle(): TextStyle = JewelTheme.editorTextStyle

    /** The text style used for code consoles. Usually is a monospaced font. Can be the same as [editorTextStyle]. */
    @Composable public fun consoleTextStyle(): TextStyle = JewelTheme.consoleTextStyle
}

/**
 * Creates a copy of this [TextStyle] with a new [fontSize] and an appropriately set line height.
 *
 * @see Typography.DefaultLineHeightMultiplier
 */
@Suppress("DEPRECATION")
@Deprecated("Use computeLineHeightPx() to set the line height appropriately instead.")
public fun TextStyle.copyWithSize(
    fontSize: TextUnit,
    color: Color = this.color,
    fontWeight: FontWeight? = this.fontWeight,
    fontStyle: FontStyle? = this.fontStyle,
    fontSynthesis: FontSynthesis? = this.fontSynthesis,
    fontFamily: FontFamily? = this.fontFamily,
    fontFeatureSettings: String? = this.fontFeatureSettings,
    letterSpacing: TextUnit = this.letterSpacing,
    baselineShift: BaselineShift? = this.baselineShift,
    textGeometricTransform: TextGeometricTransform? = this.textGeometricTransform,
    localeList: LocaleList? = this.localeList,
    background: Color = this.background,
    textDecoration: TextDecoration? = this.textDecoration,
    shadow: Shadow? = this.shadow,
    drawStyle: DrawStyle? = this.drawStyle,
    textAlign: TextAlign = this.textAlign,
    textDirection: TextDirection = this.textDirection,
    textIndent: TextIndent? = this.textIndent,
    platformStyle: PlatformTextStyle? = this.platformStyle,
    lineHeightStyle: LineHeightStyle? = this.lineHeightStyle,
    lineBreak: LineBreak = this.lineBreak,
    hyphens: Hyphens = this.hyphens,
    textMotion: TextMotion? = this.textMotion,
): TextStyle =
    copy(
        color,
        fontSize,
        fontWeight,
        fontStyle,
        fontSynthesis,
        fontFamily,
        fontFeatureSettings,
        letterSpacing,
        baselineShift,
        textGeometricTransform,
        localeList,
        background,
        textDecoration,
        shadow,
        drawStyle,
        textAlign,
        textDirection,
        lineHeight = fontSize * DefaultLineHeightMultiplier,
        textIndent,
        platformStyle,
        lineHeightStyle,
        lineBreak,
        hyphens,
        textMotion,
    )

/**
 * Creates a copy of this [TextStyle] with a new [fontSize] and an appropriately set line height.
 *
 * @see Typography.DefaultLineHeightMultiplier
 */
@Suppress("DEPRECATION")
@Deprecated("Use computeLineHeightPx() to set the line height appropriately instead.")
public fun TextStyle.copyWithSize(
    fontSize: TextUnit,
    brush: Brush?,
    alpha: Float = this.alpha,
    fontWeight: FontWeight? = this.fontWeight,
    fontStyle: FontStyle? = this.fontStyle,
    fontSynthesis: FontSynthesis? = this.fontSynthesis,
    fontFamily: FontFamily? = this.fontFamily,
    fontFeatureSettings: String? = this.fontFeatureSettings,
    letterSpacing: TextUnit = this.letterSpacing,
    baselineShift: BaselineShift? = this.baselineShift,
    textGeometricTransform: TextGeometricTransform? = this.textGeometricTransform,
    localeList: LocaleList? = this.localeList,
    background: Color = this.background,
    textDecoration: TextDecoration? = this.textDecoration,
    shadow: Shadow? = this.shadow,
    drawStyle: DrawStyle? = this.drawStyle,
    textAlign: TextAlign = this.textAlign,
    textDirection: TextDirection = this.textDirection,
    textIndent: TextIndent? = this.textIndent,
    platformStyle: PlatformTextStyle? = this.platformStyle,
    lineHeightStyle: LineHeightStyle? = this.lineHeightStyle,
    lineBreak: LineBreak = this.lineBreak,
    hyphens: Hyphens = this.hyphens,
    textMotion: TextMotion? = this.textMotion,
): TextStyle =
    copy(
        brush,
        alpha,
        fontSize,
        fontWeight,
        fontStyle,
        fontSynthesis,
        fontFamily,
        fontFeatureSettings,
        letterSpacing,
        baselineShift,
        textGeometricTransform,
        localeList,
        background,
        textDecoration,
        shadow,
        drawStyle,
        textAlign,
        textDirection,
        lineHeight = fontSize * DefaultLineHeightMultiplier,
        textIndent,
        platformStyle,
        lineHeightStyle,
        lineBreak,
        hyphens,
        textMotion,
    )

public operator fun TextUnit.plus(other: TextUnit): TextUnit =
    when {
        isSp && other.isSp -> TextUnit(value + other.value, TextUnitType.Sp)
        isEm && other.isEm -> TextUnit(value + other.value, TextUnitType.Em)
        isUnspecified && other.isUnspecified -> TextUnit(value + other.value, TextUnitType.Unspecified)
        else -> error("Can't add together different TextUnits. Got $type and ${other.type}")
    }

public operator fun TextUnit.minus(other: TextUnit): TextUnit =
    when {
        isSp && other.isSp -> TextUnit(value - other.value, TextUnitType.Sp)
        isEm && other.isEm -> TextUnit(value - other.value, TextUnitType.Em)
        isUnspecified && other.isUnspecified -> TextUnit(value - other.value, TextUnitType.Unspecified)
        else -> error("Can't subtract different TextUnits. Got $type and ${other.type}")
    }

@Suppress("UndesirableClassUsage") // We're not in the IJP in this module (suppression only needed for JPS)
private val image = BufferedImage(1, 1, TYPE_INT_ARGB)

/**
 * Computes the font's "base" line height with the same logic used by
 * [com.intellij.openapi.editor.impl.view.EditorView.initMetricsIfNeeded].
 *
 * This is useful to set a valid [TextStyle.lineHeight] when trying to match the metrics used by Swing and the IJP.
 */
@ExperimentalJewelApi
@Px
public fun Font.computeLineHeightPx(): Int {
    // We need to create a Graphics2D to get its FontRendererContext. The only way we have is
    // by requesting a BufferedImage to create one for us. We can't reuse it because the FRC
    // instance is cached inside the Graphics2D and may lead to incorrect scales being applied.
    val graphics2D = image.createGraphics()

    // The logic below emulates the JBR API's FontMetricsAccessor_fallback logic to set up the
    // measurements. Ideally, we'd use JBR.getFontMetricsAccessor(), like IJP does, but that
    // crashes at runtime in standalone.
    val context = getDefaultFrc()
    with(graphics2D) {
        transform = context.transform
        setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, context.antiAliasingHint)
        setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, context.fractionalMetricsHint)
    }
    val metrics = graphics2D.getFontMetrics(this)
    return metrics.height
}

private var defaultFrc: FontRenderContext? = null

private fun getDefaultFrc(): FontRenderContext {
    // This code is lifted from the JBR's FontDesignMetrics
    if (defaultFrc == null) {
        val tx =
            if (GraphicsEnvironment.isHeadless()) {
                AffineTransform()
            } else {
                GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .defaultScreenDevice
                    .defaultConfiguration
                    .defaultTransform
            }
        defaultFrc = FontRenderContext(tx, false, false)
    }
    return checkNotNull(defaultFrc) { "The defaultFrc should never be null" }
}
