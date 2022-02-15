package org.jetbrains.jewel.theme.toolbox.styles

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

fun ScrollbarStyle() = ScrollbarStyle(
    minimalHeight = 16.dp,
    thickness = 8.dp,
    shape = RoundedCornerShape(4.dp),
    hoverDurationMillis = 300,
    unhoverColor = Color.Black.copy(alpha = 0.12f),
    hoverColor = Color.Black.copy(alpha = 0.50f)
)
