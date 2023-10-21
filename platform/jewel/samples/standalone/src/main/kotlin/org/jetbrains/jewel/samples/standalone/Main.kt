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
import org.jetbrains.jewel.JewelTheme
import org.jetbrains.jewel.Orientation
import org.jetbrains.jewel.Text
import org.jetbrains.jewel.Tooltip
import org.jetbrains.jewel.VerticalScrollbar
import org.jetbrains.jewel.intui.standalone.IntUiTheme
import org.jetbrains.jewel.intui.standalone.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.lightThemeDefinition
import org.jetbrains.jewel.intui.window.decoratedWindowComponentStyling
import org.jetbrains.jewel.intui.window.styling.dark
import org.jetbrains.jewel.intui.window.styling.light
import org.jetbrains.jewel.intui.window.styling.lightWithLightHeader
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
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.TitleBar
import org.jetbrains.jewel.window.newFullscreenControls
import org.jetbrains.jewel.window.styling.TitleBarStyle
import java.awt.Desktop
import java.io.InputStream
import java.net.URI

fun main() {
    val icon = svgResource("icons/jewel-logo.svg")
    application {
        var theme by remember { mutableStateOf(IntUiThemes.Light) }

        var swingCompat by remember { mutableStateOf(false) }
        val themeDefinition =
            if (theme.isDark()) {
                JewelTheme.darkThemeDefinition()
            } else {
                JewelTheme.lightThemeDefinition()
            }

        val projectColor by rememberUpdatedState(
            if (theme.isLightHeader()) {
                Color(0xFFF5D4C1)
            } else {
                Color(0xFF654B40)
            },
        )

        IntUiTheme(
            themeDefinition,
            componentStyling = {
                themeDefinition.decoratedWindowComponentStyling(
                    titleBarStyle = when (theme) {
                        IntUiThemes.Light -> TitleBarStyle.light()
                        IntUiThemes.LightWithLightHeader -> TitleBarStyle.lightWithLightHeader()
                        IntUiThemes.Dark -> TitleBarStyle.dark()
                    },
                )
            },
            swingCompat,
        ) {
            DecoratedWindow(
                onCloseRequest = { exitApplication() },
                title = "Jewel component catalog",
                icon = icon,
            ) {
                val windowBackground = if (theme.isDark()) {
                    JewelTheme.colorPalette.grey(1)
                } else {
                    JewelTheme.colorPalette.grey(14)
                }
                TitleBar(Modifier.newFullscreenControls(), gradientStartColor = projectColor) {
                    Row(Modifier.align(Alignment.Start)) {
                        Dropdown(Modifier.height(30.dp), menuContent = {
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
                                Icon(
                                    "icons/jewel-logo.svg",
                                    "Jewel Logo",
                                    StandaloneSampleIcons::class.java,
                                    Modifier.padding(horizontal = 4.dp).size(20.dp),
                                )
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
                                Icon("icons/github@20x20.svg", "Github", StandaloneSampleIcons::class.java)
                            }
                        }

                        Tooltip({
                            when (theme) {
                                IntUiThemes.Light -> Text("Switch to light theme with light header")
                                IntUiThemes.LightWithLightHeader -> Text("Switch to dark theme")
                                IntUiThemes.Dark -> Text("Switch to light theme")
                            }
                        }) {
                            IconButton({
                                theme = when (theme) {
                                    IntUiThemes.Light -> IntUiThemes.LightWithLightHeader
                                    IntUiThemes.LightWithLightHeader -> IntUiThemes.Dark
                                    IntUiThemes.Dark -> IntUiThemes.Light
                                }
                            }, Modifier.size(40.dp).padding(5.dp)) {
                                if (theme.isDark()) {
                                    Icon("icons/darkTheme@20x20.svg", "Themes", StandaloneSampleIcons::class.java)
                                } else {
                                    Icon("icons/lightTheme@20x20.svg", "Themes", StandaloneSampleIcons::class.java)
                                }
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
                        CheckboxRow("Swing compat", swingCompat, { swingCompat = it })
                    }

                    Divider(Orientation.Horizontal, Modifier.fillMaxWidth())

                    ComponentShowcase()
                }
            }
        }
    }
}

@Composable
private fun ComponentShowcase() {
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
            Buttons()
            Dropdowns()
            Checkboxes()
            RadioButtons()
            Links()
            Tooltips()
            TextFields()
            TextAreas()
            ProgressBar()
            ChipsAndTree()
            Tabs()
            Icons()
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
