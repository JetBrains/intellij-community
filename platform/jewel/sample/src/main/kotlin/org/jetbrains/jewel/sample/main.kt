package org.jetbrains.jewel.sample

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.runBlocking
import org.jetbrains.jewel.sample.controls.ControlsApplication
import org.jetbrains.jewel.sample.organization.OrganizationApplication
import org.jetbrains.jewel.theme.intellij.IntelliJMetrics
import org.jetbrains.jewel.theme.intellij.IntelliJPainters
import org.jetbrains.jewel.theme.intellij.IntelliJPalette
import org.jetbrains.jewel.theme.intellij.IntelliJTheme
import org.jetbrains.jewel.theme.intellij.IntelliJThemeDefinition
import org.jetbrains.jewel.theme.intellij.IntelliJTypography
import org.jetbrains.jewel.theme.toolbox.ToolboxMetrics
import org.jetbrains.jewel.theme.toolbox.ToolboxTheme
import org.jetbrains.jewel.theme.toolbox.Typography
import org.jetbrains.jewel.theme.toolbox.toolboxDarkPalette
import org.jetbrains.jewel.theme.toolbox.toolboxLightPalette
import java.io.ByteArrayInputStream

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
private fun DefaultIntelliJThemeDefinition(): IntelliJThemeDefinition {
    val buttonPalette = IntelliJPalette.Button(
        background = Brush.verticalGradient(listOf(Color.Black, Color.White)),
        foreground = Color.Black,
        foregroundDisabled = Color.Gray,
        shadow = Color.Unspecified,
        stroke = Brush.verticalGradient(listOf(Color.Red, Color.Green)),
        strokeFocused = Color.Black,
        strokeDisabled = Color.Gray,
        defaultBackground = Brush.verticalGradient(
            listOf(Color.Black, Color.White)
        ),
        defaultForeground = Color.Black,
        defaultStroke = Brush.verticalGradient(
            listOf(Color.Black, Color.White)
        ),
        defaultStrokeFocused = Color.Black,
        defaultShadow = Color.Unspecified
    )

    val textFieldPalette = IntelliJPalette.TextField(
        background = Color.White,
        backgroundDisabled = Color.White,
        foreground = Color.Black,
        foregroundDisabled = Color.Gray
    )

    val palette = IntelliJPalette(
        button = buttonPalette,
        background = Color.White,
        text = Color.Black,
        textDisabled = Color.Gray,
        controlStroke = Color.Gray,
        controlStrokeDisabled = Color.Gray,
        controlStrokeFocused = Color.Black,
        controlFocusHalo = Color.Black,
        controlInactiveHaloError = Color.Red,
        controlInactiveHaloWarning =  Color.Red,
        controlHaloError =  Color.Red,
        controlHaloWarning =  Color.Red,
        checkbox = IntelliJPalette.Checkbox(
            background = Color.White,
            foreground = Color.Black,
            foregroundDisabled = Color.Gray
        ),
        radioButton = IntelliJPalette.RadioButton(
            background = Color.White,
            foreground = Color.Black,
            foregroundDisabled = Color.Gray
        ),
        textField = textFieldPalette,
        separator = IntelliJPalette.Separator(
            color = Color.Black,
            background = Color.White
        ),
        scrollbar = IntelliJPalette.Scrollbar(
            thumbHoverColor = Color.Black,
            thumbIdleColor = Color.Gray
        )
    )

    val metrics = IntelliJMetrics(
        gridSize = 8.dp,
        singlePadding = 8.dp,
        doublePadding = 16.dp,
        controlFocusHaloWidth = 10.dp,
        controlArc = 10.dp,
        button = IntelliJMetrics.Button(
            strokeWidth = 1.dp,
            arc = CornerSize(1.dp),
            padding = PaddingValues(),
        ),
        controlFocusHaloArc = 10.dp,
        separator = IntelliJMetrics.Separator(
            strokeWidth = 1.dp
        ),
        scrollbar = IntelliJMetrics.Scrollbar(
            minSize = 29.dp,
            thickness = 7.dp,
            thumbCornerSize = CornerSize(4.dp)
        )
    )

   fun defaultSvgIcon(): @Composable () -> Painter  {
       return {
           val density = LocalDensity.current
           remember(density) {
               val svg = """
           <svg fill="#000" height="24" viewBox="0 0 24 24" width="24" xmlns="http://www.w3.org/2000/svg">
       <path d="M21 12a9 9 0 1 1-9-9 9 9 0 0 1 9 9zM10.5 7.5A1.5 1.5 0 1 0 12 6a1.5 1.5 0 0 0-1.5 1.5zm-.5 3.54v1h1V18h2v-6a.96.96 0 0 0-.96-.96z"/>
       </svg>
       """.trimIndent()
               ByteArrayInputStream(svg.toByteArray()).use {
                   loadSvgPainter(it, density)
               }
           }
       }
   }

    val painters = IntelliJPainters(
        checkbox = IntelliJPainters.CheckboxPainters(
            unselected = defaultSvgIcon(),
            unselectedDisabled = defaultSvgIcon(),
            unselectedFocused =  defaultSvgIcon(),
            selected =  defaultSvgIcon(),
            selectedDisabled = defaultSvgIcon(),
            selectedFocused = defaultSvgIcon(),
            indeterminate = defaultSvgIcon(),
            indeterminateDisabled =  defaultSvgIcon(),
            indeterminateFocused =  defaultSvgIcon()
        ),
        radioButton = IntelliJPainters.RadioButtonPainters(
            unselected = defaultSvgIcon(),
            unselectedDisabled = defaultSvgIcon(),
            unselectedFocused = defaultSvgIcon(),
            selected =  defaultSvgIcon(),
            selectedDisabled = defaultSvgIcon(),
            selectedFocused =  defaultSvgIcon(),
        )
    )

    val typography = runBlocking {
        fun defaultFont(
            color: Color = Color.Unspecified,
            lineHeight: TextUnit = TextUnit.Unspecified
        ) = TextStyle(
            color = color,
            fontSize = 20.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = FontFamily.Default,
            lineHeight = lineHeight
        )

        IntelliJTypography(
            default = defaultFont(palette.text),
            button = defaultFont(palette.button.foreground),
            checkBox = defaultFont(palette.checkbox.foreground),
            radioButton = defaultFont(palette.radioButton.foreground),
            textField = defaultFont(palette.textField.foreground)
        )
    }

    return IntelliJThemeDefinition(
        palette = palette,
        metrics = metrics,
        typography = typography,
        painters = painters
    )
}

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
