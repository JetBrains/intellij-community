package org.jetbrains.jewel.samples.standalone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.ResourceLoader
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.singleWindowApplication
import org.jetbrains.jewel.CheckboxRow
import org.jetbrains.jewel.GroupHeader
import org.jetbrains.jewel.LocalResourceLoader
import org.jetbrains.jewel.VerticalScrollbar
import org.jetbrains.jewel.samples.standalone.components.Borders
import org.jetbrains.jewel.samples.standalone.components.Buttons
import org.jetbrains.jewel.samples.standalone.components.Checkboxes
import org.jetbrains.jewel.samples.standalone.components.ChipsAndTree
import org.jetbrains.jewel.samples.standalone.components.Dropdowns
import org.jetbrains.jewel.samples.standalone.components.Links
import org.jetbrains.jewel.samples.standalone.components.ProgressBar
import org.jetbrains.jewel.samples.standalone.components.RadioButtons
import org.jetbrains.jewel.samples.standalone.components.Tabs
import org.jetbrains.jewel.samples.standalone.components.TextAreas
import org.jetbrains.jewel.samples.standalone.components.TextFields
import org.jetbrains.jewel.themes.intui.standalone.IntUiTheme
import java.io.InputStream

fun main() {
    val icon = svgResource("icons/jewel-logo.svg")
    singleWindowApplication(
        title = "Jewel sample",
        icon = icon
    ) {
        var isDark by remember { mutableStateOf(false) }
        var swingCompat by remember { mutableStateOf(false) }
        val theme = if (isDark) IntUiTheme.dark() else IntUiTheme.light()

        val verticalScrollState = rememberScrollState(0)
        val resourceLoader = LocalResourceLoader.current
        val palette = IntUiTheme.palette

        IntUiTheme(theme, swingCompat) {
            Box(
                Modifier.fillMaxSize()
                    .background(if (isDark) palette.grey(1) else palette.grey(14)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    Modifier.width(IntrinsicSize.Max).padding(24.dp).verticalScroll(verticalScrollState),
                    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GroupHeader("Themes")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CheckboxRow("Dark", isDark, resourceLoader, { isDark = it })
                        CheckboxRow("Swing compat", swingCompat, resourceLoader, { swingCompat = it })
                    }
                    Borders()
                    Buttons()
                    Dropdowns()
                    Checkboxes()
                    RadioButtons()
                    Links()
                    TextAreas()
                    ProgressBar()
                    TextFields()
                    Tabs()
                    ChipsAndTree()
                }
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    adapter = rememberScrollbarAdapter(verticalScrollState)
                )
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
