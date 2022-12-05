package org.jetbrains.jewel.util

import androidx.compose.ui.Modifier

fun Modifier.appendIf(condition: Boolean, transformer: Modifier.() -> Modifier): Modifier =
    if (!condition) this else transformer()
