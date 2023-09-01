package org.jetbrains.jewel.bridge

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Typeface
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBValue
import org.jetbrains.jewel.IntelliJThemeIconData
import org.jetbrains.jewel.InteractiveComponentState
import org.jetbrains.jewel.SvgLoader
import org.jetbrains.jewel.styling.StatefulPainterProvider
import org.jetbrains.skiko.DependsOnJBR
import org.jetbrains.skiko.awt.font.AwtFontManager
import org.jetbrains.skiko.toSkikoTypefaceOrNull
import javax.swing.UIManager

private val logger = Logger.getInstance("JewelBridge")

fun java.awt.Color.toComposeColor() = Color(
    red = red,
    green = green,
    blue = blue,
    alpha = alpha,
)

fun java.awt.Color?.toComposeColorOrUnspecified() = this?.toComposeColor() ?: Color.Unspecified

@Suppress("UnstableApiUsage")
internal fun retrieveColorOrNull(key: String) =
    JBColor.namedColor(key)
        .takeUnless { it.name == "NAMED_COLOR_FALLBACK_MARKER" }
        ?.toComposeColor()

internal fun retrieveColorOrUnspecified(key: String): Color {
    val color = retrieveColorOrNull(key)
    if (color == null) {
        logger.warn("Color with key \"$key\" not found, fallback to 'Color.Unspecified'")
    }
    return color ?: Color.Unspecified
}

internal fun retrieveColorsOrUnspecified(vararg keys: String) = keys.map { retrieveColorOrUnspecified(it) }

internal fun List<Color>.createVerticalBrush(
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

internal fun retrieveIntAsDp(key: String): Dp {
    val rawValue = UIManager.get(key)
    if (rawValue is Int) rawValue.dp

    keyNotFound(key, "Int")
}

internal fun retrieveIntAsDpOrUnspecified(key: String) =
    try {
        retrieveIntAsDp(key)
    } catch (ignored: JewelBridgeException) {
        Dp.Unspecified
    }

internal fun retrieveInsetsAsPaddingValues(key: String) =
    UIManager.getInsets(key)
        ?.let { PaddingValues(it.left.dp, it.top.dp, it.right.dp, it.bottom.dp) }
        ?: keyNotFound(key, "Insets")

internal fun retrieveArcAsCornerSize(key: String) =
    CornerSize(retrieveIntAsDp(key) / 2)

internal fun retrieveArcAsCornerSizeWithFallbacks(vararg keys: String): CornerSize {
    for (key in keys) {
        val rawValue = UIManager.get(key)
        if (rawValue is Int) {
            val cornerSize = rawValue.dp

            // Swing uses arcs, which are a diameter length, but we need a radius
            return CornerSize(cornerSize / 2)
        }
    }

    keysNotFound(keys.toList(), "Int")
}

@OptIn(DependsOnJBR::class)
private val awtFontManager = AwtFontManager()

internal suspend fun retrieveTextStyle(fontKey: String, colorKey: String? = null): TextStyle {
    val baseColor = colorKey?.let { retrieveColorOrUnspecified(colorKey) } ?: Color.Unspecified
    return retrieveTextStyle(fontKey, color = baseColor)
}

@OptIn(DependsOnJBR::class)
internal suspend fun retrieveTextStyle(
    key: String,
    color: Color = Color.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
): TextStyle {
    val font = UIManager.getFont(key) ?: keyNotFound(key, "Font")

    return with(font) {
        val typeface = toSkikoTypefaceOrNull(awtFontManager)
            ?: org.jetbrains.skia.Typeface.makeDefault()
                .also {
                    logger.warn(
                        "Unable to convert font ${font.fontName} into a Skiko typeface, " +
                            "fallback to 'Typeface.makeDefault()'",
                    )
                }

        TextStyle(
            color = color,
            fontSize = size.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = FontFamily(Typeface(typeface)),
            // todo textDecoration might be defined in the awt theme
            lineHeight = lineHeight,
        )
    }
}

internal val JBValue.dp
    get() = unscaled.dp

internal fun TextStyle.derive(sizeDelta: Float, weight: FontWeight? = fontWeight, color: Color = toSpanStyle().color) =
    copy(fontSize = fontSize - sizeDelta, fontWeight = weight, color = color)

internal operator fun TextUnit.minus(delta: Float) = plus(-delta)

internal operator fun TextUnit.plus(delta: Float) =
    when {
        isSp -> TextUnit(value + delta, type)
        isEm -> TextUnit(value + delta, type)
        else -> this
    }

internal fun <T : InteractiveComponentState> retrieveIcon(
    baseIconPath: String,
    iconData: IntelliJThemeIconData,
    svgLoader: SvgLoader,
    prefixTokensProvider: (state: T) -> String = { "" },
    suffixTokensProvider: (state: T) -> String = { "" },
): StatefulPainterProvider<T> = IntelliJResourcePainterProvider(
    basePath = iconData.iconOverrides[baseIconPath] ?: baseIconPath,
    svgLoader,
    prefixTokensProvider,
    suffixTokensProvider,
)
