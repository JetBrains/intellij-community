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
import org.jetbrains.jewel.foundation.JewelFlags
import org.jetbrains.jewel.foundation.modifier.trackActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.samples.showcase.ShowcaseIcons
import org.jetbrains.jewel.samples.standalone.IntUiThemes
import org.jetbrains.jewel.samples.standalone.viewmodel.MainViewModel
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.RadioButtonChip
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.styling.LocalCheckboxStyle
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.painter.hints.Selected
import org.jetbrains.jewel.ui.typography

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WelcomeView() {
    Column(
        modifier =
            Modifier.trackActivation().fillMaxSize().background(JewelTheme.globalColors.panelBackground).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Icon(key = ShowcaseIcons.jewelLogo, contentDescription = null, modifier = Modifier.size(200.dp))

        Text("Meet Jewel", style = JewelTheme.typography.h1TextStyle)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Theme:")

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeSelectionChip(IntUiThemes.Dark, "Dark", ShowcaseIcons.themeDark)

                ThemeSelectionChip(IntUiThemes.Light, "Light", ShowcaseIcons.themeLight)

                ThemeSelectionChip(
                    IntUiThemes.LightWithLightHeader,
                    "Light with Light Header",
                    ShowcaseIcons.themeLightWithLightHeader,
                )

                ThemeSelectionChip(IntUiThemes.System, "System", ShowcaseIcons.themeSystem)
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

            CheckboxRow(
                text = "Custom Popup Renderer",
                checked = MainViewModel.useCustomPopupRenderer,
                onCheckedChange = {
                    MainViewModel.useCustomPopupRenderer = it
                    JewelFlags.useCustomPopupRenderer = it
                },
                colors = LocalCheckboxStyle.current.colors,
                metrics = LocalCheckboxStyle.current.metrics,
                icons = LocalCheckboxStyle.current.icons,
            )
        }
    }
}

@ExperimentalLayoutApi
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
