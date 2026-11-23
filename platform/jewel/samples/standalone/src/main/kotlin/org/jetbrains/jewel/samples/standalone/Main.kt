package org.jetbrains.jewel.samples.standalone

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.window.application
import org.jetbrains.compose.resources.decodeToSvgPainter
import org.jetbrains.jewel.foundation.shortcut.ProvideJewelShortcutHost
import org.jetbrains.jewel.foundation.shortcut.shortcut
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
import org.jetbrains.jewel.samples.showcase.ShowcaseShortcuts
import org.jetbrains.jewel.samples.standalone.view.TitleBarView
import org.jetbrains.jewel.samples.standalone.viewmodel.MainViewModel
import org.jetbrains.jewel.samples.standalone.viewmodel.MainViewModel.currentView
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.styling.TitleBarStyle

/** Entry point for the Jewel standalone sample application. */
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
            val shortcutHost = ShowcaseShortcutHost.state
            DecoratedWindow(
                onCloseRequest = { exitApplication() },
                title = "Jewel standalone sample",
                icon = icon,
                // The AWT-level pre-scene hook: sees KEY_TYPED, which typed suppression requires.
                onPreviewKeyEvent = shortcutHost::onPreviewKeyEvent,
                content = {
                    TitleBarView()
                    ProvideJewelShortcutHost(shortcutHost) {
                        val windowFocus = remember { FocusRequester() }
                        Box(
                            shortcutHost.resolverRootModifier
                                .fillMaxSize()
                                // Ambient, window-wide commands: active while focus is anywhere below.
                                .shortcut(ShowcaseShortcuts.NavigateWelcome) { MainViewModel.onNavigateTo("Welcome") }
                                .shortcut(ShowcaseShortcuts.NavigateComponents) {
                                    MainViewModel.onNavigateTo("Components")
                                }
                                .shortcut(ShowcaseShortcuts.NavigateMarkdown) { MainViewModel.onNavigateTo("Markdown") }
                                .shortcut(ShowcaseShortcuts.NavigateShortcuts) {
                                    MainViewModel.onNavigateTo("Shortcuts")
                                }
                                .focusRequester(windowFocus)
                                .focusable()
                        ) {
                            LaunchedEffect(Unit) { windowFocus.requestFocus() }
                            ProvideMarkdownStyling { currentView.content() }
                        }
                    }
                },
            )
        }
    }
}

@Suppress("SameParameterValue")
private fun svgResource(resourcePath: String): Painter =
    checkNotNull(ResourceLoader.javaClass.classLoader.getResourceAsStream(resourcePath)) {
            "Could not load resource $resourcePath: it does not exist or can't be read."
        }
        .readAllBytes()
        .decodeToSvgPainter(Density(1f))

private object ResourceLoader
