package org.jetbrains.jewel.foundation.utils

import androidx.compose.ui.Modifier

/**
 * Concatenate the result of [action] if [condition] is `true`,
 * otherwise return the receiver [Modifier] untouched.
 */
fun Modifier.thenIf(condition: Boolean, action: Modifier.() -> Modifier): Modifier =
    if (condition) action(this) else this
