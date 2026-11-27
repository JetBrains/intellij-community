// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.shortcut

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

/**
 * The shortcut host for the current Compose surface. Provided by the surface owner (theme/bridge integration);
 * action-bound components read presentations and invoke through it without ever seeing host-specific types. Null when
 * no shortcut host is installed — components render disabled.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
@Suppress("CompositionLocalAllowlist")
public val LocalJewelShortcutHost: ProvidableCompositionLocal<JewelShortcutHostState?> = staticCompositionLocalOf {
    null
}

/**
 * Provides [state] as [LocalJewelShortcutHost] for [content]. Standalone windows and dialogs should also install
 * [JewelShortcutHostState.resolverRootModifier] at their content root; Jewel `Popup` components attach it automatically
 * when a host is in scope.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
@Composable
public fun ProvideJewelShortcutHost(state: JewelShortcutHostState, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalJewelShortcutHost provides state, content = content)
}
