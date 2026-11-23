// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.shortcut

import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.getOrNull
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

/**
 * Semantics published by the shortcut modifiers, so UI tests and tooling can discover keyboard behavior declaratively:
 * - [JewelShortcutActions] — the action IDs bound on a node via `Modifier.shortcut` (multiple modifiers on one chain
 *   accumulate);
 * - [JewelClaimedShortcuts] — the display texts of sequences claimed via `Modifier.claimShortcut`.
 *
 * Match them in tests with `SemanticsMatcher.expectValue(JewelShortcutActions, listOf("my.action"))` or a keyed lookup;
 * combined with scene key injection (`performKeyInput`), a plain Compose UI test can both find shortcut-bearing nodes
 * and verify they dispatch.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public val JewelShortcutActions: SemanticsPropertyKey<List<String>> =
    SemanticsPropertyKey("JewelShortcutActions") { parent, child -> parent.orEmpty() + child }

/** Semantics key listing the display texts of key sequences claimed on a node. */
@ApiStatus.Experimental
@ExperimentalJewelApi
public val JewelClaimedShortcuts: SemanticsPropertyKey<List<String>> =
    SemanticsPropertyKey("JewelClaimedShortcuts") { parent, child -> parent.orEmpty() + child }

/** Publishes [actionIds] on [JewelShortcutActions] for UI tests and semantics inspection. */
@ApiStatus.Experimental
@ExperimentalJewelApi
public fun SemanticsPropertyReceiver.jewelShortcutActions(actionIds: List<String>) {
    // Compose visits multiple SemanticsModifierNodes on the same LayoutNode tail-to-head (innermost to outermost) into
    // one SemanticsConfiguration, so prepending preserves the outer-to-inner modifier chain order.
    val existing = (this as? SemanticsConfiguration)?.getOrNull(JewelShortcutActions).orEmpty()
    this[JewelShortcutActions] = actionIds + existing
}

/** Publishes [displayTexts] on [JewelClaimedShortcuts] for UI tests and semantics inspection. */
@ApiStatus.Experimental
@ExperimentalJewelApi
public fun SemanticsPropertyReceiver.jewelClaimedShortcuts(displayTexts: List<String>) {
    val existing = (this as? SemanticsConfiguration)?.getOrNull(JewelClaimedShortcuts).orEmpty()
    this[JewelClaimedShortcuts] = displayTexts + existing
}
