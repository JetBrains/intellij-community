package org.jetbrains.jewel.util

import androidx.compose.ui.graphics.Color

fun Color.toAwtColor() = java.awt.Color(red, green, blue, alpha)