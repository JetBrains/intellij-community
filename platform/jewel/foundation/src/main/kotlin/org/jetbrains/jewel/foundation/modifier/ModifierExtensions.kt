package org.jetbrains.jewel.foundation.modifier

import androidx.compose.ui.Modifier

/**
 * Conditionally applies the [action] to the receiver [Modifier], if [precondition] is true. Returns the receiver as-is
 * otherwise.
 */
public inline fun Modifier.thenIf(precondition: Boolean, action: Modifier.() -> Modifier): Modifier =
    if (precondition) action() else this
