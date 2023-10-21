package org.jetbrains.jewel.util

import androidx.compose.ui.Modifier

@Deprecated(
    "Use thenIf instead",
    ReplaceWith("thenIf(precondition, action)"),
)
inline fun Modifier.appendIf(precondition: Boolean, action: Modifier.() -> Modifier) =
    thenIf(precondition, action)

inline fun Modifier.thenIf(precondition: Boolean, action: Modifier.() -> Modifier) =
    if (precondition) action() else this
