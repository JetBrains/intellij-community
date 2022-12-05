package org.jetbrains.jewel.themes.darcula.idebridge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.platform.LocalFocusManager
import com.intellij.openapi.application.Application
import org.jetbrains.jewel.IntelliJTheme
import org.jetbrains.jewel.IntelliJThemeDefinition
import java.awt.event.FocusEvent
import java.awt.event.FocusListener

@Composable
fun IntelliJTheme(app: Application = IntelliJApplication, content: @Composable () -> Unit) {
    val themeDefinition: IntelliJThemeDefinition? by remember(app) { app.intellijThemeFlow }
        .collectAsState(null)

    themeDefinition?.let {
        IntelliJTheme(
            palette = it.palette,
            metrics = it.metrics,
            painters = it.painters,
            typography = it.typography,
            content = content
        )
    }
}

@Composable
fun IntelliJTheme(
    composePanel: ComposePanel,
    app: Application = IntelliJApplication,
    content: @Composable () -> Unit
) {

    val fm = LocalFocusManager.current

    DisposableEffect(composePanel) {
        val listener = object : FocusListener {
            override fun focusGained(focusEvent: FocusEvent?) {
                // no-op
                println("ciao mamma $focusEvent")
            }

            override fun focusLost(focusEvent: FocusEvent?) = fm.clearFocus()
        }

        composePanel.addFocusListener(listener)

        onDispose { composePanel.removeFocusListener(listener) }
    }

    IntelliJTheme(app, content)
}
