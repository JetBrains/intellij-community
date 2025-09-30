package org.jetbrains.jewel.bridge

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.isSpecified
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
import androidx.compose.ui.unit.isSpecified
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
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

private val logger = Logger.getInstance("JewelBridge")

/**
 * Converts an AWT [`Color`][java.awt.Color] to a Compose [Color].
 *
 * Assumes the AWT [`Color`][java.awt.Color] is expressed in the sRGB color space.
 */
public fun java.awt.Color.toComposeColor(): Color = Color(red = red, green = green, blue = blue, alpha = alpha)

/**
 * Converts a Compose [Color] to an AWT [`Color`][java.awt.Color].
 *
 * Assumes the Compose [Color] is expressed in the sRGB color space.
 */
@Suppress("UseJBColor") // We specifically do not want to, here
public fun Color.toAwtColor(): java.awt.Color = java.awt.Color(red, green, blue, alpha)

/**
 * Converts a Compose [Color] to an AWT [`Color`][java.awt.Color] if the color is specified. If it is unspecified,
 * returns `null`.
 *
 * Assumes the Compose [Color] is expressed in the sRGB color space.
 */
@Suppress("UseJBColor") // We specifically do not want to, here
public fun Color.toAwtColorOrNull(): java.awt.Color? =
    takeIf { it.isSpecified }?.let { java.awt.Color(red, green, blue, alpha) }

/**
 * Converts a nullable AWT [`Color`][java.awt.Color] to a Compose [Color], using [Color.Unspecified] if the color is
 * `null`.
 *
 * Assumes the AWT [`Color`][java.awt.Color] is expressed in the sRGB color space.
 */
public fun java.awt.Color?.toComposeColorOrUnspecified(): Color = this?.toComposeColor() ?: Color.Unspecified

/**
 * Retrieves a color from the current LaF using its [key].
 *
 * @param key The key to look up the color with.
 * @param default The color to return if the key is not found.
 * @return The color from the LaF, or [default] if the key is not found.
 * @see retrieveColorOrNull
 */
public fun retrieveColor(key: String, default: Color): Color = retrieveColorOrNull(key) ?: default

/**
 * Retrieves a color from the current LaF using its [key]. This is a convenience overload for when you need a different
 * color for dark themes.
 *
 * @param key The key to look up the color with.
 * @param isDark Whether the current theme is dark.
 * @param default The color to return if the key is not found in a light theme.
 * @param defaultDark The color to return if the key is not found in a dark theme.
 * @return The color from the LaF, or one of the defaults if the key is not found.
 * @see retrieveColorOrNull
 */
public fun retrieveColor(key: String, isDark: Boolean, default: Color, defaultDark: Color): Color =
    retrieveColorOrNull(key) ?: if (isDark) defaultDark else default

/**
 * Retrieves a color from the current LaF using its [key].
 *
 * @param key The key to look up the color with.
 * @return The color from the LaF, or `null` if the key is not found.
 * @see JBColor.namedColorOrNull
 */
public fun retrieveColorOrNull(key: String): Color? = JBColor.namedColorOrNull(key)?.toComposeColor()

/**
 * Retrieves a color from the current LaF using its [key], or returns [Color.Unspecified] if the key is not found.
 *
 * @param key The key to look up the color with.
 * @return The color from the LaF, or [Color.Unspecified] if the key is not found.
 * @see retrieveColorOrNull
 */
public fun retrieveColorOrUnspecified(key: String): Color {
    val color = retrieveColorOrNull(key)
    if (color == null) {
        logger.debug("Color with key \"$key\" not found, fallback to 'Color.Unspecified'")
    }
    return color ?: Color.Unspecified
}

/**
 * Retrieves multiple colors from the current LaF using their [keys].
 *
 * @param keys The keys to look up the colors with.
 * @return A list of colors, with [Color.Unspecified] for any keys that are not found.
 * @see retrieveColorOrUnspecified
 */
public fun retrieveColorsOrUnspecified(vararg keys: String): List<Color> = keys.map { retrieveColorOrUnspecified(it) }

/**
 * Creates a vertical [Brush] from a list of colors.
 *
 * @param startY The y-coordinate to start the gradient at.
 * @param endY The y-coordinate to end the gradient at.
 * @param tileMode The tile mode to use for the gradient.
 * @return A [Brush] that will paint a vertical gradient with the given colors.
 */
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

/**
 * Retrieves an integer value from the current LaF as a [Dp].
 *
 * @param key The key to look up the integer with.
 * @param default An optional default value to return if the key is not found.
 * @return The integer value from the LaF as a [Dp], or [default] if the key is not found and a default is provided.
 * @throws JewelBridgeException if the key is not found and no default is provided.
 * @see retrieveIntAsDpOrUnspecified
 */
public fun retrieveIntAsDp(key: String, default: Dp? = null): Dp =
    retrieveIntAsDpOrUnspecified(key).takeIf { it.isSpecified } ?: default ?: keyNotFound(key, "Int")

/**
 * Retrieves an integer value from the current LaF as a [Dp], or returns [Dp.Unspecified] if the key is not found.
 *
 * @param key The key to look up the integer with.
 * @return The integer value from the LaF as a [Dp], or [Dp.Unspecified] if the key is not found.
 */
public fun retrieveIntAsDpOrUnspecified(key: String): Dp {
    val rawValue = UIManager.get(key)
    if (rawValue is Int) return rawValue.dp
    return Dp.Unspecified
}

/**
 * Retrieves an integer value from the current LaF as a non-negative [Dp], or returns [Dp.Unspecified] if the key is not
 * found.
 *
 * Any negative values read from the LaF will be coerced to `0.dp`.
 *
 * @param key The key to look up the integer with.
 * @return The integer value from the LaF as a [Dp], or [Dp.Unspecified] if the key is not found.
 * @see retrieveIntAsDpOrUnspecified
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public fun retrieveIntAsNonNegativeDpOrUnspecified(key: String): Dp =
    retrieveIntAsDpOrUnspecified(key).takeIf { it.isSpecified }?.safeValue() ?: Dp.Unspecified

/**
 * Retrieves insets from the current LaF as [PaddingValues].
 *
 * @param key The key to look up the insets with.
 * @param default An optional default value to return if the key is not found.
 * @return The insets from the LaF as [PaddingValues], or [default] if the key is not found and a default is provided.
 * @throws JewelBridgeException if the key is not found and no default is provided.
 */
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
 *
 * Any negative values read from the LaF will be coerced to `0.dp`.
 */
@ApiStatus.Experimental
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
 *
 * Any negative values read from the LaF will be coerced to `0.dp`.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public fun JBDimension.toNonNegativeDpSize(): DpSize {
    val scaleFactor = scale(1f)
    return DpSize((width2d() / scaleFactor).dp.safeValue(), (height2d() / scaleFactor).dp.safeValue())
}

/**
 * Retrieves an arc value from the current LaF as a [CornerSize].
 *
 * In Swing, arcs are defined as a diameter, but Compose's [CornerSize] uses a radius, so the value is divided by 2.
 *
 * @param key The key to look up the arc with.
 * @return The arc value from the LaF as a [CornerSize].
 * @throws JewelBridgeException if the key is not found.
 */
public fun retrieveArcAsCornerSize(key: String): CornerSize = CornerSize(retrieveIntAsDp(key) / 2)

/**
 * Retrieves an arc value from the current LaF as a [CornerSize], or returns a [default] value if the key is not found.
 *
 * In Swing, arcs are defined as a diameter, but Compose's [CornerSize] uses a radius, so the value is divided by 2.
 *
 * @param key The key to look up the arc with.
 * @param default The value to return if the key is not found.
 * @return The arc value from the LaF as a [CornerSize], or [default] if the key is not found.
 */
public fun retrieveArcAsCornerSizeOrDefault(key: String, default: CornerSize): CornerSize {
    val intValue = retrieveIntAsDpOrUnspecified(key)
    if (intValue.isUnspecified) return default
    return CornerSize(intValue / 2)
}

/**
 * Retrieves an arc value from the current LaF as a non-negative [CornerSize], or returns a [default] value if the key
 * is not found.
 *
 * In Swing, arcs are defined as a diameter, but Compose's [CornerSize] uses a radius, so the value is divided by 2. Any
 * negative values will be coerced to `0.dp`.
 *
 * @param key The key to look up the arc with.
 * @param default The value to return if the key is not found.
 * @return The arc value from the LaF as a [CornerSize], or [default] if the key is not found.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public fun retrieveArcAsNonNegativeCornerSizeOrDefault(key: String, default: CornerSize): CornerSize {
    val intValue = retrieveIntAsNonNegativeDpOrUnspecified(key)
    if (intValue.isUnspecified) return default
    return CornerSize(intValue / 2)
}

/**
 * Retrieves an arc value from the current LaF as a [CornerSize], trying multiple keys in order until a value is found.
 *
 * In Swing, arcs are defined as a diameter, but Compose's [CornerSize] uses a radius, so the value is divided by 2.
 *
 * @param keys The keys to look up the arc with, in order of preference.
 * @return The arc value from the LaF as a [CornerSize].
 * @throws JewelBridgeException if none of the keys are found.
 */
public fun retrieveArcAsCornerSizeWithFallbacks(vararg keys: String): CornerSize =
    retrieveArcAsCornerSizeWithFallbacksOrNull(*keys) ?: keysNotFound(keys.toList(), "Int")

/**
 * Retrieves an arc value from the current LaF as a [CornerSize], trying multiple keys in order until a value is found.
 *
 * In Swing, arcs are defined as a diameter, but Compose's [CornerSize] uses a radius, so the value is divided by 2.
 *
 * @param keys The keys to look up the arc with, in order of preference.
 * @return The arc value from the LaF as a [CornerSize], or `null` if none of the keys are found.
 */
public fun retrieveArcAsCornerSizeWithFallbacksOrNull(vararg keys: String): CornerSize? {
    for (key in keys) {
        val rawValue = UIManager.get(key)
        if (rawValue is Int) {
            val cornerSize = rawValue.dp.safeValue()

            // Swing uses arcs, which are a diameter length, but we need a radius
            return CornerSize(cornerSize / 2)
        }
    }

    return null
}

/**
 * To avoid having Jewel crash due to negative values that third-party themes can define, we need to constrain values
 * that are read from LaF. This by default coerces negative values to 0.
 */
internal fun Dp.safeValue(minimumValue: Dp = 0.dp) = this.coerceAtLeast(minimumValue)

/**
 * To avoid having Jewel crash due to negative values that third-party themes can define we need to constrain values
 * that are read from LaF. This by default coerces negative values to 0.
 */
internal fun Int.safeValue(minimumValue: Int = 0) = this.coerceAtLeast(minimumValue)

/**
 * Retrieves a [TextStyle] from the current LaF.
 *
 * @param fontKey The key to look up the font with.
 * @param colorKey The key to look up the color with. If `null`, the color will be [Color.Unspecified].
 * @param lineHeight The line height to use for the text style.
 * @param bold Whether to use a bold font weight.
 * @param fontStyle The font style to use.
 * @param size The font size to use.
 * @return The [TextStyle] from the LaF.
 */
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
    return resolvedStyle.copy(lineHeight = lineHeight.takeOrElse { resolvedStyle.lineHeight })
}

/**
 * Retrieves a [TextStyle] from the current LaF.
 *
 * @param key The key to look up the font with.
 * @param color The color to use for the text style.
 * @param lineHeight The line height to use for the text style.
 * @param bold Whether to use a bold font weight.
 * @param fontStyle The font style to use.
 * @param size The font size to use.
 * @return The [TextStyle] from the LaF.
 */
@OptIn(ExperimentalTextApi::class)
public fun retrieveTextStyle(
    key: String,
    color: Color = Color.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    bold: Boolean = false,
    fontStyle: FontStyle = FontStyle.Normal,
    size: TextUnit = TextUnit.Unspecified,
): TextStyle {
    val jbFont = retrieveJBFont(key)
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

internal fun retrieveJBFont(key: String): JBFont {
    val lafFont = UIManager.getFont(key) ?: keyNotFound(key, "Font")
    return JBFont.create(lafFont, false)
}

/**
 * Retrieves the platform-specific text style, which includes anti-aliasing and font smoothing settings.
 *
 * @return The [PlatformTextStyle] from the LaF.
 */
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

/** Converts a [JBValue] to a [Dp]. */
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

internal fun isDarculaTheme(): Boolean = lafName() == "Darcula"

/**
 * Retrieves the current editor color scheme.
 *
 * This is an unstable API, and may be removed in the future.
 */
@Suppress("UnstableApiUsage") // We need to use @Internal APIs
public fun retrieveEditorColorScheme(): EditorColorsScheme {
    val manager = EditorColorsManager.getInstance() as EditorColorsManagerImpl
    return manager.schemeManager.activeScheme ?: DefaultColorSchemesManager.getInstance().firstScheme
}
