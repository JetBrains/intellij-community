package org.jetbrains.jewel.samples.standalone.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.modifier.trackActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.samples.standalone.IntUiThemes
import org.jetbrains.jewel.samples.standalone.StandaloneSampleIcons
import org.jetbrains.jewel.samples.standalone.viewmodel.MainViewModel
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.RadioButtonChip
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Typography
import org.jetbrains.jewel.ui.component.styling.LocalCheckboxStyle
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.painter.hints.Selected

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WelcomeView() {
    Column(
        modifier =
            Modifier.trackActivation().fillMaxSize().background(JewelTheme.globalColors.panelBackground).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Icon(key = StandaloneSampleIcons.jewelLogo, contentDescription = null, modifier = Modifier.size(200.dp))

        Text("Meet Jewel", style = Typography.h1TextStyle())

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Theme:")

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeSelectionChip(IntUiThemes.Dark, "Dark", StandaloneSampleIcons.themeDark)

                ThemeSelectionChip(IntUiThemes.Light, "Light", StandaloneSampleIcons.themeLight)

                ThemeSelectionChip(
                    IntUiThemes.LightWithLightHeader,
                    "Light with Light Header",
                    StandaloneSampleIcons.themeLightWithLightHeader,
                )

                ThemeSelectionChip(IntUiThemes.System, "System", StandaloneSampleIcons.themeSystem)
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
internal fun ThemeSelectionChip(theme: IntUiThemes, name: String, iconKey: IconKey) {
    RadioButtonChip(
        selected = MainViewModel.theme == theme,
        onClick = { MainViewModel.theme = theme },
        enabled = true,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Icon(iconKey, name, hint = Selected(MainViewModel.theme == theme))
            Text(name)
        }
    }
}
