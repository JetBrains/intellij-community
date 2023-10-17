package org.jetbrains.jewel.samples.standalone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.ResourceLoader
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.application
import org.jetbrains.jewel.CheckboxRow
import org.jetbrains.jewel.Divider
import org.jetbrains.jewel.Dropdown
import org.jetbrains.jewel.Icon
import org.jetbrains.jewel.IconButton
import org.jetbrains.jewel.JewelSvgLoader
import org.jetbrains.jewel.LocalResourceLoader
import org.jetbrains.jewel.Orientation
import org.jetbrains.jewel.Text
import org.jetbrains.jewel.Tooltip
import org.jetbrains.jewel.VerticalScrollbar
import org.jetbrains.jewel.intui.standalone.IntUiTheme
import org.jetbrains.jewel.intui.standalone.rememberSvgLoader
import org.jetbrains.jewel.intui.window.styling.IntUiTitleBarStyle
import org.jetbrains.jewel.intui.window.withDecoratedWindow
import org.jetbrains.jewel.samples.standalone.components.Borders
import org.jetbrains.jewel.samples.standalone.components.Buttons
import org.jetbrains.jewel.samples.standalone.components.Checkboxes
import org.jetbrains.jewel.samples.standalone.components.ChipsAndTree
import org.jetbrains.jewel.samples.standalone.components.Dropdowns
import org.jetbrains.jewel.samples.standalone.components.Icons
import org.jetbrains.jewel.samples.standalone.components.Links
import org.jetbrains.jewel.samples.standalone.components.ProgressBar
import org.jetbrains.jewel.samples.standalone.components.RadioButtons
import org.jetbrains.jewel.samples.standalone.components.Tabs
import org.jetbrains.jewel.samples.standalone.components.TextAreas
import org.jetbrains.jewel.samples.standalone.components.TextFields
import org.jetbrains.jewel.samples.standalone.components.Tooltips
import org.jetbrains.jewel.separator
import org.jetbrains.jewel.styling.rememberStatelessPainterProvider
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.TitleBar
import org.jetbrains.jewel.window.newFullscreenControls
import java.awt.Desktop
import java.io.InputStream
import java.net.URI

fun main() {
    val icon = svgResource("icons/jewel-logo.svg")
    application {
        var intUiTheme by remember { mutableStateOf(IntUiThemes.Light) }

        var swingCompat by remember { mutableStateOf(false) }
        val theme = if (intUiTheme.isDark()) IntUiTheme.darkThemeDefinition() else IntUiTheme.lightThemeDefinition()
        val projectColor by rememberUpdatedState(
            if (intUiTheme.isLightHeader()) {
                Color(0xFFF5D4C1)
            } else {
                Color(0xFF654B40)
            },
        )

        IntUiTheme(
            theme.withDecoratedWindow(
                titleBarStyle = when (intUiTheme) {
                    IntUiThemes.Light -> IntUiTitleBarStyle.light()
                    IntUiThemes.LightWithLightHeader -> IntUiTitleBarStyle.lightWithLightHeader()
                    IntUiThemes.Dark -> IntUiTitleBarStyle.dark()
                },
            ),
            swingCompat,
        ) {
            val resourceLoader = LocalResourceLoader.current
            val svgLoader by rememberSvgLoader()

            DecoratedWindow(
                onCloseRequest = { exitApplication() },
                title = "Jewel component catalog",
                icon = icon,
            ) {
                val windowBackground = if (intUiTheme.isDark()) {
                    IntUiTheme.colorPalette.grey(1)
                } else {
                    IntUiTheme.colorPalette.grey(14)
                }
                TitleBar(Modifier.newFullscreenControls(), gradientStartColor = projectColor) {
                    val jewelLogoProvider = rememberStatelessPainterProvider("icons/jewel-logo.svg", svgLoader)
                    val jewelLogo by jewelLogoProvider.getPainter(resourceLoader)

                    Row(Modifier.align(Alignment.Start)) {
                        Dropdown(resourceLoader, Modifier.height(30.dp), menuContent = {
                            selectableItem(false, {
                            }) {
                                Text("New Project...")
                            }
                            separator()
                            selectableItem(false, {
                            }) {
                                Text("jewel")
                            }
                        }) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(jewelLogo, "Jewel Logo", Modifier.padding(horizontal = 4.dp).size(20.dp))
                                Text("jewel")
                            }
                        }
                    }

                    Text(title)

                    Row(Modifier.align(Alignment.End)) {
                        Tooltip({
                            Text("Open Jewel Github repository")
                        }) {
                            IconButton({
                                Desktop.getDesktop().browse(URI.create("https://github.com/JetBrains/jewel"))
                            }, Modifier.size(40.dp).padding(5.dp)) {
                                val iconProvider = rememberStatelessPainterProvider("icons/github@20x20.svg", svgLoader)
                                Icon(iconProvider.getPainter(resourceLoader).value, "Github")
                            }
                        }

                        Tooltip({
                            when (intUiTheme) {
                                IntUiThemes.Light -> Text("Switch to light theme with light header")
                                IntUiThemes.LightWithLightHeader -> Text("Switch to dark theme")
                                IntUiThemes.Dark -> Text("Switch to light theme")
                            }
                        }) {
                            IconButton({
                                intUiTheme = when (intUiTheme) {
                                    IntUiThemes.Light -> IntUiThemes.LightWithLightHeader
                                    IntUiThemes.LightWithLightHeader -> IntUiThemes.Dark
                                    IntUiThemes.Dark -> IntUiThemes.Light
                                }
                            }, Modifier.size(40.dp).padding(5.dp)) {
                                val lightThemeIcon =
                                    rememberStatelessPainterProvider("icons/lightTheme@20x20.svg", svgLoader)
                                val darkThemeIcon =
                                    rememberStatelessPainterProvider("icons/darkTheme@20x20.svg", svgLoader)

                                val iconProvider = if (intUiTheme.isDark()) darkThemeIcon else lightThemeIcon
                                Icon(iconProvider.getPainter(resourceLoader).value, "Themes")
                            }
                        }
                    }
                }

                Column(Modifier.fillMaxSize().background(windowBackground)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CheckboxRow("Swing compat", swingCompat, resourceLoader, { swingCompat = it })
                    }

                    Divider(Orientation.Horizontal, Modifier.fillMaxWidth())

                    ComponentShowcase(svgLoader, resourceLoader)
                }
            }
        }
    }
}

@Composable
private fun ComponentShowcase(svgLoader: JewelSvgLoader, resourceLoader: ResourceLoader) {
    val verticalScrollState = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.width(IntrinsicSize.Max)
                .verticalScroll(verticalScrollState)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Borders()
            Buttons(svgLoader, resourceLoader)
            Dropdowns()
            Checkboxes()
            RadioButtons()
            Links()
            Tooltips()
            TextFields(svgLoader, resourceLoader)
            TextAreas()
            ProgressBar(svgLoader)
            ChipsAndTree()
            Tabs(svgLoader, resourceLoader)
            Icons(svgLoader, resourceLoader)
        }

        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(verticalScrollState),
        )
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
