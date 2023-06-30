@file:Suppress("UnstableApiUsage")

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

internal val Application.intellijThemeFlow
    get() = lookAndFeelFlow.map { ObtainIntelliJTheme() }

internal val Application.lookAndFeelFlow: Flow<LafManager>
    get() = messageBusFlow(LafManagerListener.TOPIC, { LafManager.getInstance()!! }) {
        LafManagerListener { trySend(it) }
    }

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
