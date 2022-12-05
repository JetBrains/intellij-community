package org.jetbrains.jewel.util.font

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.FileFont

data class SystemFontFamily(
    val name: String,
    val fontFamily: FontFamily,
    val fonts: List<FileFont>
)
