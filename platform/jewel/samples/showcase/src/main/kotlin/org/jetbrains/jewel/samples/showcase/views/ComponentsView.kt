// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.samples.showcase.views

import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.jewel.foundation.modifier.trackActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.SelectableIconActionButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Typography
import org.jetbrains.jewel.ui.component.styling.IconButtonMetrics
import org.jetbrains.jewel.ui.component.styling.IconButtonStyle
import org.jetbrains.jewel.ui.component.styling.TooltipMetrics
import org.jetbrains.jewel.ui.component.styling.TooltipStyle
import org.jetbrains.jewel.ui.painter.hints.Size
import org.jetbrains.jewel.ui.theme.iconButtonStyle
import org.jetbrains.jewel.ui.theme.tooltipStyle

@ExperimentalLayoutApi
@Composable
public fun ComponentsView(viewModel: ComponentsViewModel, toolbarButtonMetrics: IconButtonMetrics) {
    Row(Modifier.trackActivation().fillMaxSize().background(JewelTheme.globalColors.panelBackground)) {
        ComponentsToolBar(viewModel, toolbarButtonMetrics)
        Divider(Orientation.Vertical, Modifier.fillMaxHeight())
        ComponentView(viewModel.getCurrentView())
    }
}

@ExperimentalLayoutApi
@Composable
public fun ComponentsToolBar(viewModel: ComponentsViewModel, buttonMetrics: IconButtonMetrics) {
    Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState())) {
        val iconButtonStyle = JewelTheme.iconButtonStyle
        val style = remember(iconButtonStyle) { IconButtonStyle(iconButtonStyle.colors, buttonMetrics) }
        viewModel.getViews().forEach {
            SelectableIconActionButton(
                key = it.iconKey,
                contentDescription = "Show ${it.title}",
                selected = viewModel.getCurrentView() == it,
                onClick = { viewModel.setCurrentView(it) },
                style = style,
                tooltip = { Text(it.title) },
                tooltipStyle =
                    TooltipStyle(JewelTheme.tooltipStyle.colors, TooltipMetrics.defaults(showDelay = 150.milliseconds)),
                tooltipPlacement = TooltipPlacement.ComponentRect(Alignment.CenterEnd, Alignment.CenterEnd),
                extraHints = arrayOf(Size(20)),
            )
        }
    }
}

@Composable
internal fun ComponentView(view: ViewInfo) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(view.title, style = Typography.h1TextStyle())
        view.content()
    }
}
