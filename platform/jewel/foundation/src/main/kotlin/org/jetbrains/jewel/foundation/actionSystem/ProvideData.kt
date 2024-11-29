package org.jetbrains.jewel.foundation.actionSystem

import androidx.compose.foundation.focusable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode

/**
 * Configure component to provide data for IntelliJ Actions system.
 *
 * Use this modifier to provide context related data that can be used by IntelliJ Actions functionality such as Search
 * Everywhere, Action Popups etc.
 *
 * Important note: modifiers order is important, so be careful with order of [focusable] and [provideData] (see
 * [FocusEventModifierNode]).
 *
 * This can be traversed from Modifier.Node() using Compose traversal API using DataProviderNode as a TraverseKey
 */
@Suppress("unused")
public fun Modifier.provideData(dataProvider: DataProviderContext.() -> Unit): Modifier =
    this then DataProviderElement(dataProvider)
