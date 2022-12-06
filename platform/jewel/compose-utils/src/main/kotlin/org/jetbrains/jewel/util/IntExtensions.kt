package org.jetbrains.jewel.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity

@Composable
fun Int.pxToDp() = with(LocalDensity.current) { this@pxToDp.toDp() }
