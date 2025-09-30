package org.jetbrains.jewel.samples.standalone

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Density
import androidx.compose.ui.window.application
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToSvgPainter
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.util.JewelLogger
import org.jetbrains.jewel.intui.markdown.standalone.ProvideMarkdownStyling
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.createDefaultTextStyle
import org.jetbrains.jewel.intui.standalone.theme.createEditorTextStyle
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.intui.window.styling.dark
import org.jetbrains.jewel.intui.window.styling.light
import org.jetbrains.jewel.intui.window.styling.lightWithLightHeader
import org.jetbrains.jewel.samples.standalone.view.TitleBarView
import org.jetbrains.jewel.samples.standalone.viewmodel.MainViewModel
import org.jetbrains.jewel.samples.standalone.viewmodel.MainViewModel.currentView
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.styling.TitleBarStyle

@ExperimentalLayoutApi
public fun main() {
    JewelLogger.getInstance("StandaloneSample").info("Starting Jewel Standalone sample")
    val icon = svgResource("icons/jewel-logo.svg")

    application {
        val textStyle = JewelTheme.createDefaultTextStyle()
        val editorStyle = JewelTheme.createEditorTextStyle()

        val themeDefinition =
            if (MainViewModel.theme.isDark()) {
                JewelTheme.darkThemeDefinition(defaultTextStyle = textStyle, editorTextStyle = editorStyle)
            } else {
                JewelTheme.lightThemeDefinition(defaultTextStyle = textStyle, editorTextStyle = editorStyle)
            }

        IntUiTheme(
            theme = themeDefinition,
            styling =
                ComponentStyling.default()
                    .decoratedWindow(
                        titleBarStyle =
                            when (MainViewModel.theme) {
                                IntUiThemes.Light -> TitleBarStyle.light()
                                IntUiThemes.LightWithLightHeader -> TitleBarStyle.lightWithLightHeader()
                                IntUiThemes.Dark -> TitleBarStyle.dark()
                                IntUiThemes.System ->
                                    if (MainViewModel.theme.isDark()) {
                                        TitleBarStyle.dark()
                                    } else {
                                        TitleBarStyle.light()
                                    }
                            }
                    ),
            swingCompatMode = MainViewModel.swingCompat,
        ) {
            DecoratedWindow(
                onCloseRequest = { exitApplication() },
                title = "Jewel standalone sample",
                icon = icon,
                onKeyEvent = { keyEvent ->
                    processKeyShortcuts(keyEvent = keyEvent, onNavigateTo = MainViewModel::onNavigateTo)
                },
                content = {
                    TitleBarView()
                    ProvideMarkdownStyling { currentView.content() }
                },
            )
        }
    }
}

/*
   Alt + W -> Welcome
   Alt + M -> Markdown
   Alt + C -> Components
*/
private fun processKeyShortcuts(keyEvent: KeyEvent, onNavigateTo: (String) -> Unit): Boolean {
    if (!keyEvent.isAltPressed || keyEvent.type != KeyEventType.KeyDown) return false
    return when (keyEvent.key) {
        Key.W -> {
            onNavigateTo("Welcome")
            true
        }

        Key.M -> {
            onNavigateTo("Markdown")
            true
        }

        Key.C -> {
            onNavigateTo("Components")
            true
        }

        else -> false
    }
}

@Suppress("SameParameterValue")
@OptIn(ExperimentalResourceApi::class)
private fun svgResource(resourcePath: String): Painter =
    checkNotNull(ResourceLoader.javaClass.classLoader.getResourceAsStream(resourcePath)) {
            "Could not load resource $resourcePath: it does not exist or can't be read."
        }
        .readAllBytes()
        .decodeToSvgPainter(Density(1f))

private object ResourceLoader
