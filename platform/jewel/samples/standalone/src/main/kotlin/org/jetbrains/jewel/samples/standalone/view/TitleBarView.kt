package org.jetbrains.jewel.samples.standalone.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.awt.Desktop
import java.net.URI
import org.jetbrains.jewel.samples.showcase.ShowcaseIcons
import org.jetbrains.jewel.samples.showcase.views.forCurrentOs
import org.jetbrains.jewel.samples.standalone.IntUiThemes
import org.jetbrains.jewel.samples.standalone.viewmodel.MainViewModel
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.painter.hints.Size
import org.jetbrains.jewel.window.DecoratedWindowScope
import org.jetbrains.jewel.window.TitleBar
import org.jetbrains.jewel.window.newFullscreenControls

@ExperimentalLayoutApi
@Composable
internal fun DecoratedWindowScope.TitleBarView() {
    TitleBar(Modifier.newFullscreenControls(), gradientStartColor = MainViewModel.projectColor) {
        Row(Modifier.align(Alignment.Start)) {
            Dropdown(
                Modifier.height(30.dp),
                menuContent = {
                    MainViewModel.views.forEach {
                        selectableItem(
                            selected = MainViewModel.currentView == it,
                            onClick = { MainViewModel.currentView = it },
                            keybinding = it.keyboardShortcut?.forCurrentOs(),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(it.iconKey, null, modifier = Modifier.size(20.dp), hint = Size(20))
                                Text(it.title)
                            }
                        }
                    }
                },
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(MainViewModel.currentView.iconKey, null, hint = Size(20))
                        Text(MainViewModel.currentView.title)
                    }
                }
            }
        }

        Text(title)

        Row(Modifier.align(Alignment.End)) {
            Tooltip({ Text("Open Jewel Github repository") }) {
                IconButton(
                    { Desktop.getDesktop().browse(URI.create("https://github.com/JetBrains/jewel")) },
                    Modifier.size(40.dp).padding(5.dp),
                ) {
                    Icon(ShowcaseIcons.gitHub, "Github")
                }
            }

            Tooltip(
                tooltip = {
                    when (MainViewModel.theme) {
                        IntUiThemes.Light -> Text("Switch to light theme with light header")
                        IntUiThemes.LightWithLightHeader -> Text("Switch to dark theme")
                        IntUiThemes.Dark,
                        IntUiThemes.System -> Text("Switch to light theme")
                    }
                }
            ) {
                IconButton(
                    {
                        MainViewModel.theme =
                            when (MainViewModel.theme) {
                                IntUiThemes.Light -> IntUiThemes.LightWithLightHeader
                                IntUiThemes.LightWithLightHeader -> IntUiThemes.Dark
                                IntUiThemes.Dark,
                                IntUiThemes.System -> IntUiThemes.Light
                            }
                    },
                    Modifier.size(40.dp).padding(5.dp),
                ) {
                    when (MainViewModel.theme) {
                        IntUiThemes.Light ->
                            Icon(
                                key = ShowcaseIcons.themeLight,
                                contentDescription = "Light",
                                hints = arrayOf(Size(20)),
                            )

                        IntUiThemes.LightWithLightHeader ->
                            Icon(
                                key = ShowcaseIcons.themeLightWithLightHeader,
                                contentDescription = "Light with light header",
                                hints = arrayOf(Size(20)),
                            )

                        IntUiThemes.Dark ->
                            Icon(key = ShowcaseIcons.themeDark, contentDescription = "Dark", hints = arrayOf(Size(20)))

                        IntUiThemes.System ->
                            Icon(
                                key = ShowcaseIcons.themeSystem,
                                contentDescription = "System",
                                hints = arrayOf(Size(20)),
                            )
                    }
                }
            }
        }
    }
}
