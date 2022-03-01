package org.jetbrains.jewel.theme.idea

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.intellij.openapi.application.Application
import kotlinx.coroutines.flow.map
import org.jetbrains.jewel.theme.intellij.IntelliJTheme

@Composable
fun IntelliJTheme(app: Application = IntelliJApplication, content: @Composable () -> Unit) {
    val themeDefinition by remember { app.lookAndFeelFlow.map { CurrentIntelliJThemeDefinition() } }
        .collectAsState(CurrentIntelliJThemeDefinition())

    IntelliJTheme(
        palette = themeDefinition.palette,
        metrics = themeDefinition.metrics,
        painters = themeDefinition.painters,
        typography = themeDefinition.typography,
        content = content
    )
}
