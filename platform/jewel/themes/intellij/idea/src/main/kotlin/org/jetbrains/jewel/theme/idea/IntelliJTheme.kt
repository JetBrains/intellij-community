package org.jetbrains.jewel.theme.idea

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.platform.LocalFocusManager
import com.intellij.openapi.application.Application
import org.jetbrains.jewel.theme.intellij.IntelliJTheme
import org.jetbrains.jewel.theme.intellij.IntelliJThemeDefinition
import java.awt.event.FocusEvent
import java.awt.event.FocusListener

@Composable
fun IntelliJTheme(app: Application = IntelliJApplication, content: @Composable () -> Unit) {
    val themeDefinition: IntelliJThemeDefinition? by remember(app) { app.intellijThemeFlow }
        .collectAsState(null)

    themeDefinition?.let { themeDefinition ->
        IntelliJTheme(
            palette = themeDefinition.palette,
            metrics = themeDefinition.metrics,
            painters = themeDefinition.painters,
            typography = themeDefinition.typography,
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
