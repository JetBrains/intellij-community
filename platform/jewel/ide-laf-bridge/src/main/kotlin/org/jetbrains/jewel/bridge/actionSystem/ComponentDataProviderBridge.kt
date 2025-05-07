package org.jetbrains.jewel.bridge.actionSystem

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.bridge.JewelComposePanelWrapper

@Suppress("FunctionName")
@Composable
internal fun ComponentDataProviderBridge(
    component: JewelComposePanelWrapper,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val rootDataProviderModifier = remember { RootDataProviderModifier() }

    Box(modifier = Modifier.then(rootDataProviderModifier).then(modifier)) { content() }

    DisposableEffect(component) {
        component.targetProvider = rootDataProviderModifier

        onDispose {
            if (component.targetProvider == rootDataProviderModifier) {
                component.targetProvider = null
            }
        }
    }
}
