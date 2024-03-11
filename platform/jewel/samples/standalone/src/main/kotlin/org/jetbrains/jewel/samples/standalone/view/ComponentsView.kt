package org.jetbrains.jewel.samples.standalone.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.modifier.trackActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.samples.standalone.StandaloneSampleIcons
import org.jetbrains.jewel.samples.standalone.viewmodel.ComponentsViewModel
import org.jetbrains.jewel.samples.standalone.viewmodel.View
import org.jetbrains.jewel.samples.standalone.viewmodel.ViewInfo
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.SelectableIconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.Typography
import org.jetbrains.jewel.ui.component.styling.LocalIconButtonStyle
import org.jetbrains.jewel.ui.painter.hints.Size
import org.jetbrains.jewel.ui.painter.hints.Stroke
import org.jetbrains.jewel.ui.painter.rememberResourcePainterProvider

@Composable
@View(title = "Components", position = 1, icon = "icons/structure.svg")
fun ComponentsView() {
    Row(Modifier.trackActivation().fillMaxSize().background(JewelTheme.globalColors.paneBackground)) {
        ComponentsToolBar()
        Divider(Orientation.Vertical)
        ComponentView(ComponentsViewModel.currentView)
    }
}

@Composable
fun ComponentsToolBar() {
    Column(Modifier.fillMaxHeight().width(40.dp)) {
        ComponentsViewModel.views.forEach {
            Tooltip({
                Text("Show ${it.title}")
            }) {
                SelectableIconButton(ComponentsViewModel.currentView == it, {
                    ComponentsViewModel.currentView = it
                }, Modifier.size(40.dp).padding(5.dp)) { state ->
                    val tint by LocalIconButtonStyle.current.colors.foregroundFor(state)
                    val painterProvider = rememberResourcePainterProvider(it.icon, StandaloneSampleIcons::class.java)
                    val painter by painterProvider.getPainter(Size(20), Stroke(tint))
                    Icon(painter = painter, "icon")
                }
            }
        }
    }
}

@Composable
fun ComponentView(view: ViewInfo) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(view.title, style = Typography.h1TextStyle())
        view.content()
    }
}
