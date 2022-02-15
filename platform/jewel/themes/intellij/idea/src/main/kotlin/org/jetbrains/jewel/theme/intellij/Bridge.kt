package org.jetbrains.jewel.theme.intellij

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Typeface
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.project.Project
import com.intellij.util.ui.DirProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import org.jetbrains.skiko.toSkikoTypeface
import javax.swing.UIManager
import java.awt.Color as AwtColor

@Suppress("UnstableApiUsage")
@ExperimentalCoroutinesApi
val Project.lafChangesFlow
    get() = callbackFlow {
        val connection = messageBus.simpleConnect()
        connection.subscribe(
            LafManagerListener.TOPIC,
            LafManagerListener { trySend(Unit) }
        )
        awaitClose { connection.disconnect() }
    }

@Composable
fun IntelliJTheme(project: Project, content: @Composable () -> Unit) {
    val themeDefinitionFlow by derivedStateOf {
        project.lafChangesFlow.map { CurrentIntelliJThemeDefinition() }
    }

    val themeDefinition by themeDefinitionFlow.collectAsState(CurrentIntelliJThemeDefinition())

    IntelliJTheme(
        palette = themeDefinition.palette,
        metrics = themeDefinition.metrics,
        painters = themeDefinition.painters,
        typography = themeDefinition.typography,
        content = content
    )
}

internal fun AwtColor.toColor() = Color(red, green, blue, alpha)

internal fun retrieveFloat(key: String) =
    UIManager.get(key) as? Float ?: error("Float with key '$key' not found")

internal fun retrieveColor(key: String) =
    retrieveColorOrNull(key) ?: error("Color with key '$key' not found")

internal fun retrieveColorOrNull(key: String) =
    UIManager.getColor(key)?.toColor()

private val dirProvider = DirProvider()

internal fun lookupSvgIcon(
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

internal fun retrieveColors(vararg keys: String) = keys.map { retrieveColor(it) }

internal fun retrieveIntAsDp(key: String) = UIManager.getInt(key).dp

internal fun retrieveInsetsAsPaddingValues(key: String) =
    UIManager.getInsets(key)
        .let { PaddingValues(it.left.dp, it.top.dp, it.right.dp, it.bottom.dp) }

suspend fun retrieveFont(
    key: String,
    color: Color = Color.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified
) = with(UIManager.getFont(key)) {
    TextStyle(
        color = color,
        fontSize = size.sp,
        fontWeight = FontWeight.Normal,
        fontFamily = FontFamily(Typeface(toSkikoTypeface()!!)),
        // todo textDecoration might be defined in the awt theme
        lineHeight = lineHeight
    )
}
