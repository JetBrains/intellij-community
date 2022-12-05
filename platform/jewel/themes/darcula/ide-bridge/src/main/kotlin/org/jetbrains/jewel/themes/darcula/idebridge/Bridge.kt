@file:Suppress("UnstableApiUsage")

package org.jetbrains.jewel.themes.darcula.idebridge

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
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.messages.SimpleMessageBusConnection
import com.intellij.util.messages.Topic
import com.intellij.util.ui.DirProvider
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import org.jetbrains.skiko.toSkikoTypeface
import javax.swing.UIManager
import java.awt.Color as AwtColor

internal val IntelliJApplication: Application
    get() = ApplicationManager.getApplication()

internal fun <L : Any, K> Application.messageBusFlow(
    topic: Topic<L>,
    initialValue: (suspend () -> K)? = null,
    @BuilderInference listener: ProducerScope<K>.() -> L
): Flow<K> = callbackFlow {
    initialValue?.let { send(it()) }
    val connection: SimpleMessageBusConnection = messageBus.simpleConnect()
    connection.subscribe(topic, listener())
    awaitClose { connection.disconnect() }
}

internal val Application.lookAndFeelFlow: Flow<LafManager>
    get() = messageBusFlow(LafManagerListener.TOPIC, { LafManager.getInstance()!! }) {
        LafManagerListener { trySend(it) }
    }

internal val Application.intellijThemeFlow
    get() = lookAndFeelFlow.map { CurrentIntelliJThemeDefinition() }

internal fun AwtColor.toColor() = Color(
    red = red,
    green = green,
    blue = blue,
    alpha = alpha
)

internal fun retrieveColorOrNull(key: String) =
    UIManager.getColor(key)?.toColor()

internal fun retrieveColorOrUnspecified(key: String): Color {
    val color = retrieveColorOrNull(key)
    if (color == null) {
        logger.warn("Color with key \"$key\" not found, fallback to 'Color.Unspecified'")
    }
    return color ?: Color.Unspecified
}

internal fun retrieveColorsOrUnspecified(vararg keys: String) = keys.map { retrieveColorOrUnspecified(it) }

internal val logger = Logger.getInstance("#org.jetbrains.compose.desktop.ide.theme")

internal val dirProvider = DirProvider()

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

suspend fun retrieveFont(
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
