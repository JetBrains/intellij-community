package org.jetbrains.jewel.sample.controls

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.styles.Styles
import org.jetbrains.jewel.theme.toolbox.components.LinearProgressIndicator
import org.jetbrains.jewel.theme.toolbox.components.Text
import org.jetbrains.jewel.theme.toolbox.components.TextField
import org.jetbrains.jewel.theme.toolbox.metrics
import org.jetbrains.jewel.theme.toolbox.typography

@Composable
fun InformationControls() {
    Column(
        verticalArrangement = Arrangement.spacedBy(Styles.metrics.smallPadding),
        modifier = Modifier.fillMaxSize().padding(Styles.metrics.largePadding),
    ) {
        Column {
            val progressTarget = remember { mutableStateOf(0f) }
            val progress = animateFloatAsState(progressTarget.value, animationSpec = tween(2000))
            val animateProgressModifier = Modifier.clickable {
                progressTarget.value = 1f - progressTarget.value
            }
            LinearProgressIndicator(progress.value, modifier = animateProgressModifier.width(200.dp))
            Spacer(Modifier.height(Styles.metrics.smallPadding))
            Text("Click for animation", style = Styles.typography.caption, modifier = animateProgressModifier)
            TextField(progressTarget.value.toString(), {
                progressTarget.value = it.toFloatOrNull() ?: progressTarget.value
            })

            Spacer(Modifier.height(Styles.metrics.largePadding))
            LinearProgressIndicator(modifier = animateProgressModifier.size(400.dp, 8.dp))
        }
    }
}
