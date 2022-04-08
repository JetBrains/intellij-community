package org.jetbrains.jewel

import androidx.compose.ui.graphics.Color

fun Color.toAwtColor() = java.awt.Color(red, green, blue, alpha)