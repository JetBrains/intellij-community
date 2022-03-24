package org.jetbrains.jewel.sample

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.jewel.sample.controls.ControlsApplication
import org.jetbrains.jewel.sample.organization.OrganizationApplication
import org.jetbrains.jewel.theme.intellij.IntelliJMetrics
import org.jetbrains.jewel.theme.intellij.IntelliJPainters
import org.jetbrains.jewel.theme.intellij.IntelliJPalette
import org.jetbrains.jewel.theme.intellij.IntelliJTheme
import org.jetbrains.jewel.theme.intellij.IntelliJThemeDefinition
import org.jetbrains.jewel.theme.intellij.IntelliJTypography
import org.jetbrains.jewel.theme.intellij.default
import org.jetbrains.jewel.theme.intellij.light
import org.jetbrains.jewel.theme.toolbox.ToolboxMetrics
import org.jetbrains.jewel.theme.toolbox.ToolboxTheme
import org.jetbrains.jewel.theme.toolbox.Typography
import org.jetbrains.jewel.theme.toolbox.toolboxDarkPalette
import org.jetbrains.jewel.theme.toolbox.toolboxLightPalette

enum class Application {
    Organization,
    Controls
}

enum class Palette {
    Light, Dark
}

enum class Theme {
    Toolbox, IntelliJ
}

@Suppress("FunctionName")
private fun DefaultIntelliJThemeDefinition() = IntelliJThemeDefinition(
    IntelliJPalette.light,
    IntelliJMetrics.default,
    IntelliJTypography.light,
    IntelliJPainters.light
)

fun main() = application {
    var theme by mutableStateOf(Theme.IntelliJ)
    var palette by mutableStateOf(Palette.Light)
    var metrics by mutableStateOf(ToolboxMetrics())
    var selectedApplication by mutableStateOf(Application.Controls)

    val topTheme = remember { DefaultIntelliJThemeDefinition() }

    Window(
            onCloseRequest = ::exitApplication,
            title = "Jewel Sample",
            state = rememberWindowState(
                size = DpSize(950.dp, 650.dp),
                position = WindowPosition.Aligned(Alignment.Center)
            ),
        ) {
        IntelliJTheme(topTheme.palette, topTheme.metrics, topTheme.painters, topTheme.typography) {
            MenuBar {
                Menu("Application") {
                    RadioButtonItem(
                        "Organization",
                        selected = selectedApplication == Application.Organization,
                        onClick = { selectedApplication = Application.Organization },

                        )
                    RadioButtonItem(
                        "Controls",
                        selected = selectedApplication == Application.Controls,
                        onClick = { selectedApplication = Application.Controls },
                    )
                }
                Menu("Theme") {
                    RadioButtonItem(
                        "Toolbox",
                        selected = theme == Theme.Toolbox,
                        onClick = { theme = Theme.Toolbox },
                    )
                    RadioButtonItem(
                        "IntelliJ",
                        selected = theme == Theme.IntelliJ,
                        onClick = { theme = Theme.IntelliJ },
                    )
                    Separator()
                    RadioButtonItem(
                        "Light",
                        selected = palette == Palette.Light,
                        onClick = { palette = Palette.Light },
                    )
                    RadioButtonItem(
                        "Dark",
                        selected = palette == Palette.Dark,
                        onClick = { palette = Palette.Dark },
                    )
                    Separator()
                    RadioButtonItem(
                        "Normal",
                        selected = metrics.base == 8.dp,
                        onClick = { metrics = ToolboxMetrics(8.dp) },
                    )
                    RadioButtonItem(
                        "Small",
                        selected = metrics.base == 6.dp,
                        onClick = { metrics = ToolboxMetrics(6.dp) },
                    )
                    RadioButtonItem(
                        "Large",
                        selected = metrics.base == 12.dp,
                        onClick = { metrics = ToolboxMetrics(12.dp) },
                    )
                }
            }

            val toolboxPalette = when (palette) {
                Palette.Light -> toolboxLightPalette
                Palette.Dark -> toolboxDarkPalette
            }
            val toolboxTypography = remember(metrics) { Typography(metrics) }

            ToolboxTheme(toolboxPalette, metrics, toolboxTypography) {
                when (selectedApplication) {
                    Application.Organization -> OrganizationApplication()
                    Application.Controls -> ControlsApplication()
                }
            }
        }
    }
}
