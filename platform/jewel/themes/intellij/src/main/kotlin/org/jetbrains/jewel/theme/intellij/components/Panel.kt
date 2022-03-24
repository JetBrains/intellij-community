package org.jetbrains.jewel.theme.intellij.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.theme.intellij.LocalPalette

@Composable
fun Surface(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val background by animateColorAsState(LocalPalette.current.background)
    Box(modifier.background(background), content = content)
}
