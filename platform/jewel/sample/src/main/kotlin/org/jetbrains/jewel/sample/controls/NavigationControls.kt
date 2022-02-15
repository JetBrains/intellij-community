package org.jetbrains.jewel.sample.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.styles.Styles
import org.jetbrains.jewel.theme.toolbox.components.Tab
import org.jetbrains.jewel.theme.toolbox.components.TabRow
import org.jetbrains.jewel.theme.toolbox.components.Text
import org.jetbrains.jewel.theme.toolbox.components.rememberTabContainerState
import org.jetbrains.jewel.theme.toolbox.metrics

@Composable
fun NavigationControls() {
    Column(
        verticalArrangement = Arrangement.spacedBy(Styles.metrics.smallPadding),
        modifier = Modifier.fillMaxSize().padding(Styles.metrics.largePadding),
    ) {
        val tabState = rememberTabContainerState(1)
        TabRow(tabState) {
            Tab(1) { Text("One") }
            Tab(2) { Text("Two") }
            Tab(3) { Text("Three") }
        }
    }
}
