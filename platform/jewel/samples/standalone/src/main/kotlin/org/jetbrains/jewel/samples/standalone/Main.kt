package org.jetbrains.jewel.samples.standalone

import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.ResourceLoader
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.window.application
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.intui.window.styling.dark
import org.jetbrains.jewel.intui.window.styling.light
import org.jetbrains.jewel.intui.window.styling.lightWithLightHeader
import org.jetbrains.jewel.samples.standalone.view.TitleBarView
import org.jetbrains.jewel.samples.standalone.viewmodel.MainViewModel
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.styling.TitleBarStyle
import java.io.InputStream

@OptIn(ExperimentalTextApi::class)
fun main() {
    val icon = svgResource("icons/jewel-logo.svg")

    application {
        val textStyle = TextStyle(fontFamily = FontFamily("Inter"))

        val themeDefinition =
            if (MainViewModel.theme.isDark()) {
                JewelTheme.darkThemeDefinition(defaultTextStyle = textStyle)
            } else {
                JewelTheme.lightThemeDefinition(defaultTextStyle = textStyle)
            }

        IntUiTheme(
            themeDefinition,
            ComponentStyling.decoratedWindow(
                titleBarStyle = when (MainViewModel.theme) {
                    IntUiThemes.Light -> TitleBarStyle.light()
                    IntUiThemes.LightWithLightHeader -> TitleBarStyle.lightWithLightHeader()
                    IntUiThemes.Dark -> TitleBarStyle.dark()
                    IntUiThemes.System -> if (MainViewModel.theme.isDark()) {
                        TitleBarStyle.dark()
                    } else {
                        TitleBarStyle.light()
                    }
                },
            ),
            MainViewModel.swingCompat,
        ) {
            DecoratedWindow(
                onCloseRequest = { exitApplication() },
                title = "Jewel component catalog",
                icon = icon,
            ) {
                TitleBarView()
                MainViewModel.currentView.content()
            }
        }
    }
}

private fun svgResource(
    resourcePath: String,
    loader: ResourceLoader = ResourceLoader.Default,
): Painter =
    loader.load(resourcePath)
        .use { stream: InputStream ->
            loadSvgPainter(stream, Density(1f))
        }
