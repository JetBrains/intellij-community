package org.jetbrains.jewel.sample.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.jewel.Orientation
import org.jetbrains.jewel.styles.Styles
import org.jetbrains.jewel.theme.toolbox.components.Divider
import org.jetbrains.jewel.theme.toolbox.components.Tab
import org.jetbrains.jewel.theme.toolbox.components.TabColumn
import org.jetbrains.jewel.theme.toolbox.components.TabScope
import org.jetbrains.jewel.theme.toolbox.components.Text
import org.jetbrains.jewel.theme.toolbox.components.rememberTabContainerState
import org.jetbrains.jewel.theme.toolbox.metrics
import org.jetbrains.jewel.theme.toolbox.styles.frame
import org.jetbrains.jewel.theme.toolbox.typography

@Composable
fun ControlsApplication() {
    val backgroundColor = Styles.frame.appearance(Unit).backgroundColor
    Row(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        val page = rememberTabContainerState("input")
        Column {
            Text(
                "Categories",
                style = Styles.typography.body.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(Styles.metrics.largePadding)
            )
            TabColumn(
                page,
                modifier = Modifier.fillMaxHeight().padding(Styles.metrics.smallPadding),
                verticalArrangement = Arrangement.spacedBy(Styles.metrics.smallPadding)
            ) {
                Section("input", "Input")
                Section("information", "Information")
                Section("navigation", "Navigation")
                Section("typography", "Typography")
            }
        }
        Divider(orientation = Orientation.Vertical)
        Column(modifier = Modifier.fillMaxSize()) {
            when (page.selectedKey) {
                "input" -> InputControls()
                "information" -> InformationControls()
                "navigation" -> NavigationControls()
                "typography" -> Typography()
            }
        }
    }
}

@Composable
private fun TabScope<String>.Section(key: String, caption: String) {
    Tab(key) {
        Text(caption)
    }
}
