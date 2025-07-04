package org.jetbrains.jewel.bridge

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.FontRasterizationSettings
import androidx.compose.ui.text.FontSmoothing
import androidx.compose.ui.text.PlatformParagraphStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.asComposeFontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.takeOrElse
import com.intellij.ide.ui.AntialiasingType
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsUtils
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.ui.JBColor
import com.intellij.ui.NewUI
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBValue
import java.awt.Dimension
import java.awt.Insets
import javax.swing.UIManager
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.component.Typography

private val logger = Logger.getInstance("JewelBridge")

public fun java.awt.Color.toComposeColor(): Color = Color(red = red, green = green, blue = blue, alpha = alpha)

public fun Color.toAwtColor(): java.awt.Color = java.awt.Color(red, green, blue, alpha)

public fun java.awt.Color?.toComposeColorOrUnspecified(): Color = this?.toComposeColor() ?: Color.Unspecified

public fun retrieveColor(key: String, default: Color): Color = retrieveColorOrNull(key) ?: default

public fun retrieveColor(key: String, isDark: Boolean, default: Color, defaultDark: Color): Color =
    retrieveColorOrNull(key) ?: if (isDark) defaultDark else default

public fun retrieveColorOrNull(key: String): Color? = JBColor.namedColorOrNull(key)?.toComposeColor()

public fun retrieveColorOrUnspecified(key: String): Color {
    val color = retrieveColorOrNull(key)
    if (color == null) {
        logger.debug("Color with key \"$key\" not found, fallback to 'Color.Unspecified'")
    }
    return color ?: Color.Unspecified
}

public fun retrieveColorsOrUnspecified(vararg keys: String): List<Color> = keys.map { retrieveColorOrUnspecified(it) }

public fun List<Color>.createVerticalBrush(
    startY: Float = 0.0f,
    endY: Float = Float.POSITIVE_INFINITY,
    tileMode: TileMode = TileMode.Clamp,
): Brush {
    if (isEmpty()) return SolidColor(Color.Transparent)
    if (size == 1) return SolidColor(first())

    // Optimization: use a cheaper SolidColor if all colors are identical
    if (all { it == first() }) SolidColor(first())

    return Brush.verticalGradient(this, startY, endY, tileMode)
}

public fun retrieveIntAsDp(key: String, default: Dp? = null): Dp {
    val rawValue = UIManager.get(key)
    if (rawValue is Int) return rawValue.dp
    if (default != null) return default

    keyNotFound(key, "Int")
}

public fun retrieveIntAsDpOrUnspecified(key: String): Dp =
    try {
        retrieveIntAsDp(key)
    } catch (_: JewelBridgeException) {
        Dp.Unspecified
    }

@ExperimentalJewelApi
public fun retrieveIntAsNonNegativeDpOrUnspecified(key: String): Dp =
    try {
        retrieveIntAsDp(key).safeValue()
    } catch (_: JewelBridgeException) {
        Dp.Unspecified
    }

public fun retrieveInsetsAsPaddingValues(key: String, default: PaddingValues? = null): PaddingValues =
    UIManager.getInsets(key)?.toPaddingValues() ?: default ?: keyNotFound(key, "Insets")

/**
 * Converts a [Insets] to [PaddingValues]. If the receiver is a [JBInsets] instance, this function delegates to the
 * specific [toPaddingValues] for it, which is scaling-aware.
 */
public fun Insets.toPaddingValues(): PaddingValues =
    if (this is JBInsets) {
        toPaddingValues()
    } else {
        PaddingValues(start = left.dp, top = top.dp, end = right.dp, bottom = bottom.dp)
    }

/**
 * Converts a [JBInsets] to [PaddingValues], in a scaling-aware way. This means that the resulting [PaddingValues] will
 * be constructed from the [JBInsets.getUnscaled] values, treated as [Dp]. This avoids double scaling.
 */
@Suppress("ktlint:standard:function-signature") // False positive
public fun JBInsets.toPaddingValues(): PaddingValues =
    PaddingValues(start = unscaled.left.dp, top = unscaled.top.dp, end = unscaled.right.dp, bottom = unscaled.bottom.dp)

/**
 * Converts a [Dimension] to [DpSize]. If the receiver is a [JBDimension] instance, this function delegates to the
 * specific [toDpSize] for it, which is scaling-aware.
 */
public fun Dimension.toDpSize(): DpSize = if (this is JBDimension) toDpSize() else DpSize(width.dp, height.dp)

/**
 * Converts a [JBDimension] to [DpSize], in a scaling-aware way. This means that the resulting [DpSize] will be
 * constructed by first obtaining the unscaled values. This avoids double scaling.
 */
public fun JBDimension.toDpSize(): DpSize {
    val scaleFactor = scale(1f)
    return DpSize((width2d() / scaleFactor).dp, (height2d() / scaleFactor).dp)
}

/**
 * Converts a [Dimension] to a non-negative [DpSize]. If the receiver is a [JBDimension] instance, this function
 * delegates to the specific [toNonNegativeDpSize] for it, which is scaling-aware.
 */
@ExperimentalJewelApi
public fun Dimension.toNonNegativeDpSize(): DpSize =
    if (this is JBDimension) {
        toNonNegativeDpSize()
    } else {
        DpSize(width = width.dp.safeValue(), height = height.dp.safeValue())
    }

/**
 * Converts a [JBDimension] to a non-negative [DpSize], in a scaling-aware way. This means that the resulting [DpSize]
 * will be constructed by first obtaining the unscaled values. This avoids double scaling.
 */
@ExperimentalJewelApi
public fun JBDimension.toNonNegativeDpSize(): DpSize {
    val scaleFactor = scale(1f)
    return DpSize((width2d() / scaleFactor).dp.safeValue(), (height2d() / scaleFactor).dp.safeValue())
}

public fun retrieveArcAsCornerSize(key: String): CornerSize = CornerSize(retrieveIntAsDp(key) / 2)

public fun retrieveArcAsCornerSizeOrDefault(key: String, default: CornerSize): CornerSize {
    val intValue = retrieveIntAsDpOrUnspecified(key)
    if (intValue.isUnspecified) return default
    return CornerSize(intValue / 2)
}

@ExperimentalJewelApi
public fun retrieveArcAsNonNegativeCornerSizeOrDefault(key: String, default: CornerSize): CornerSize {
    val intValue = retrieveIntAsNonNegativeDpOrUnspecified(key)
    if (intValue.isUnspecified) return default
    return CornerSize(intValue / 2)
}

public fun retrieveArcAsCornerSizeWithFallbacks(vararg keys: String): CornerSize {
    for (key in keys) {
        val rawValue = UIManager.get(key)
        if (rawValue is Int) {
            val cornerSize = rawValue.dp.safeValue()

            // Swing uses arcs, which are a diameter length, but we need a radius
            return CornerSize(cornerSize / 2)
        }
    }

    keysNotFound(keys.toList(), "Int")
}

/**
 * To avoid having Jewel crash due to negative values that third-party themes can define, we need to constrain values
 * that are read from LaF.
 */
internal fun Dp.safeValue(minimumValue: Dp = 0.dp) = this.coerceAtLeast(minimumValue)

/**
 * To avoid having Jewel crash due to negative values that third-party themes can define we need to constrain values
 * that are read from LaF.
 */
internal fun Int.safeValue(minimumValue: Int = 0) = this.coerceAtLeast(minimumValue)

public fun retrieveTextStyle(
    fontKey: String,
    colorKey: String? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    bold: Boolean = false,
    fontStyle: FontStyle = FontStyle.Normal,
    size: TextUnit = TextUnit.Unspecified,
): TextStyle {
    val baseColor = colorKey?.let { retrieveColorOrUnspecified(colorKey) } ?: Color.Unspecified
    val resolvedStyle = retrieveTextStyle(fontKey, color = baseColor, lineHeight, bold, fontStyle, size)
    return resolvedStyle.copy(
        lineHeight = lineHeight.takeOrElse { resolvedStyle.fontSize * Typography.DefaultLineHeightMultiplier }
    )
}

@OptIn(ExperimentalTextApi::class)
public fun retrieveTextStyle(
    key: String,
    color: Color = Color.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    bold: Boolean = false,
    fontStyle: FontStyle = FontStyle.Normal,
    size: TextUnit = TextUnit.Unspecified,
): TextStyle {
    val lafFont = UIManager.getFont(key) ?: keyNotFound(key, "Font")
    val jbFont = JBFont.create(lafFont, false)

    val derivedFont =
        jbFont
            .let { if (bold) it.asBold() else it.asPlain() }
            .let { if (fontStyle == FontStyle.Italic) it.asItalic() else it }

    val safeFontSize = if (derivedFont.size > 0) derivedFont.size.sp else TextUnit.Unspecified
    val safeLineHeight = if (lineHeight.value > 0) lineHeight else TextUnit.Unspecified
    val safeSize = if (size.value > 0) size else TextUnit.Unspecified

    return TextStyle(
        color = color,
        fontSize = safeSize.takeOrElse { safeFontSize / UISettingsUtils.getInstance().currentIdeScale },
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = fontStyle,
        fontFamily = derivedFont.asComposeFontFamily(),
        // TODO textDecoration might be defined in the AWT theme
        lineHeight = safeLineHeight,
        platformStyle = retrievePlatformTextStyle(),
    )
}

@OptIn(ExperimentalTextApi::class)
public fun retrievePlatformTextStyle(): PlatformTextStyle {
    val uiSettings = UISettings.instanceOrNull
    val aa = uiSettings?.ideAAType ?: AntialiasingType.GREYSCALE
    val platformDefaultFontRasterization = FontRasterizationSettings.PlatformDefault

    return PlatformTextStyle(
        null,
        paragraphStyle =
            PlatformParagraphStyle(
                fontRasterizationSettings =
                    FontRasterizationSettings(
                        smoothing = aa.asComposeFontSmoothing(),
                        hinting = platformDefaultFontRasterization.hinting,
                        subpixelPositioning = platformDefaultFontRasterization.subpixelPositioning,
                        platformDefaultFontRasterization.autoHintingForced,
                    )
            ),
    )
}

@OptIn(ExperimentalTextApi::class)
private fun AntialiasingType.asComposeFontSmoothing(): FontSmoothing =
    when (this) {
        AntialiasingType.GREYSCALE -> FontSmoothing.AntiAlias
        AntialiasingType.SUBPIXEL -> FontSmoothing.SubpixelAntiAlias
        AntialiasingType.OFF -> FontSmoothing.None
    }

public val JBValue.dp: Dp
    get() = unscaled.dp

internal operator fun TextUnit.minus(delta: Float): TextUnit = plus(-delta)

internal operator fun TextUnit.plus(delta: Float): TextUnit =
    when {
        isSp -> TextUnit(value + delta, type)
        isEm -> TextUnit(value + delta, type)
        else -> this
    }

internal fun scaleDensityWithIdeScale(sourceDensity: Density): Density {
    val ideaScale = UISettingsUtils.getInstance().currentIdeScale
    val density = sourceDensity.density * ideaScale

    return Density(density, sourceDensity.fontScale)
}

internal fun isNewUiTheme(): Boolean = NewUI.isEnabled()

internal fun lafName(): String {
    val lafInfo = LafManager.getInstance().currentUIThemeLookAndFeel
    return lafInfo.name
}

@Suppress("UnstableApiUsage") // We need to use @Internal APIs
public fun retrieveEditorColorScheme(): EditorColorsScheme {
    val manager = EditorColorsManager.getInstance() as EditorColorsManagerImpl
    return manager.schemeManager.activeScheme ?: DefaultColorSchemesManager.getInstance().firstScheme
}
