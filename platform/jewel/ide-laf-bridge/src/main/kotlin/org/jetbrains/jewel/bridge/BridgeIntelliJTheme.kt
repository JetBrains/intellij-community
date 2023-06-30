package org.jetbrains.jewel.bridge

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Typeface
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.NewUI
import com.intellij.util.ui.DirProvider
import org.jetbrains.jewel.IntelliJTheme
import org.jetbrains.skiko.toSkikoTypeface
import javax.swing.UIManager

private val logger = Logger.getInstance("JewelBridge")

private val dirProvider = DirProvider()

internal fun ObtainIntelliJTheme(): IntelliJTheme {
    val isIntUi = NewUI.isEnabled()

    if (!isIntUi) {
        // TODO return Darcula/IntelliJ Light theme instead
        logger.warn("Darcula LaF (aka \"old UI\" are not supported yet, falling back to Int UI")
    }

    return bridgeIntUi()
}

fun java.awt.Color.toComposeColor() = Color(
    red = red,
    green = green,
    blue = blue,
    alpha = alpha
)

internal fun retrieveColorOrNull(key: String) =
    UIManager.getColor(key)?.toComposeColor()

internal fun retrieveColorOrUnspecified(key: String): Color {
    val color = retrieveColorOrNull(key)
    if (color == null) {
        logger.warn("Color with key \"$key\" not found, fallback to 'Color.Unspecified'")
    }
    return color ?: Color.Unspecified
}

internal fun retrieveColorsOrUnspecified(vararg keys: String) = keys.map { retrieveColorOrUnspecified(it) }

// Based on LafIconLookup#findIcon
// TODO inject additional logic from ImageLoader#addFileNameVariant to support loading the right icon
//  variants ([_dark], [@2x], [_stroke])
internal fun lookupIJSvgIcon(
    name: String,
    selected: Boolean = false,
    focused: Boolean = false,
    enabled: Boolean = true,
    editable: Boolean = false,
    pressed: Boolean = false
): @Composable () -> Painter {
    var key = name
    if (editable) {
        key += "Editable"
    }
    if (selected) {
        key += "Selected"
    }

    when {
        pressed -> key += "Pressed"
        focused -> key += "Focused"
        !enabled -> key += "Disabled"
    }

    // for Mac blue theme and other LAFs use default directory icons
    val dir = dirProvider.dir()
    val path = "$dir$key.svg"

    return {
        rememberSvgResource(path.removePrefix("/"), dirProvider.javaClass.classLoader)
    }
}

internal fun retrieveIntAsDp(key: String) = UIManager.getInt(key).dp

internal fun retrieveInsetsAsPaddingValues(key: String) =
    UIManager.getInsets(key)
        .let { PaddingValues(it.left.dp, it.top.dp, it.right.dp, it.bottom.dp) }

internal suspend fun retrieveFont(
    key: String,
    color: Color = Color.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified
): TextStyle {
    val font = UIManager.getFont(key) ?: error("Font with key \"$key\" not found, fallback to 'Typeface.makeDefault()'")
    return with(font) {
        val typeface = toSkikoTypeface() ?: org.jetbrains.skia.Typeface.makeDefault().also {
            logger.warn("Unable to convert font ${font.fontName} into a Skiko typeface, fallback to 'Typeface.makeDefault()'")
        }
        TextStyle(
            color = color,
            fontSize = size.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = FontFamily(Typeface(typeface)),
            // todo textDecoration might be defined in the awt theme
            lineHeight = lineHeight
        )
    }
}
