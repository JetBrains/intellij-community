package org.jetbrains.jewel.util

import androidx.compose.ui.Modifier

inline fun Modifier.appendIf(precondition: Boolean, action: Modifier.() -> Modifier) =
    if (precondition) action() else this
