package org.jetbrains.jewel.samples.standalone.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.modifier.trackActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.samples.standalone.IntUiThemes
import org.jetbrains.jewel.samples.standalone.StandaloneSampleIcons
import org.jetbrains.jewel.samples.standalone.viewmodel.MainViewModel
import org.jetbrains.jewel.samples.standalone.viewmodel.View
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.RadioButtonChip
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.styling.LocalCheckboxStyle
import org.jetbrains.jewel.ui.painter.hints.Selected
import org.jetbrains.jewel.ui.painter.rememberResourcePainterProvider

@OptIn(ExperimentalLayoutApi::class)
@Composable
@View(title = "Welcome", position = 0, icon = "icons/meetNewUi.svg")
fun WelcomeView() {
    Column(
        modifier = Modifier.trackActivation()
            .fillMaxSize()
            .background(JewelTheme.globalColors.paneBackground)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        val meetNewUiImage =
            rememberResourcePainterProvider("images/New UI Image.png", StandaloneSampleIcons::class.java)
        val meetNewUiImagePainter by meetNewUiImage.getPainter()
        Image(
            painter = meetNewUiImagePainter,
            contentDescription = null,
            modifier = Modifier.widthIn(max = 500.dp),
            contentScale = ContentScale.Crop,
        )

        Text("Meet Jewel", fontSize = 20.sp)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Theme:")

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeSelectionChip(IntUiThemes.Dark, "Dark", "icons/darkTheme.svg")

                ThemeSelectionChip(IntUiThemes.Light, "Light", "icons/lightTheme.svg")

                ThemeSelectionChip(
                    IntUiThemes.LightWithLightHeader,
                    "Light with Light Header",
                    "icons/lightWithLightHeaderTheme.svg",
                )

                ThemeSelectionChip(IntUiThemes.System, "System", "icons/systemTheme.svg")
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CheckboxRow(
                text = "Swing compatibility",
                checked = MainViewModel.swingCompat,
                onCheckedChange = { MainViewModel.swingCompat = it },
                colors = LocalCheckboxStyle.current.colors,
                metrics = LocalCheckboxStyle.current.metrics,
                icons = LocalCheckboxStyle.current.icons,
            )
        }
    }
}

@Composable
fun ThemeSelectionChip(theme: IntUiThemes, name: String, icon: String) {
    RadioButtonChip(
        selected = MainViewModel.theme == theme,
        onClick = { MainViewModel.theme = theme },
        enabled = true,
    ) {
        val painterProvider = rememberResourcePainterProvider(
            icon,
            StandaloneSampleIcons::class.java,
        )
        val painter by painterProvider.getPainter(Selected(MainViewModel.theme == theme))
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Icon(painter, name)
            Text(name)
        }
    }
}
