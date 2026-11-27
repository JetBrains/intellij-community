// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.shortcut

import androidx.compose.ui.input.key.Key
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

/**
 * Standard edit actions shared by both hosts.
 *
 * Common code binds these IDs with [androidx.compose.ui.Modifier.shortcut] and never names a host-specific action ID.
 * The IJPL bridge maps them to the corresponding platform actions (`JewelActionMappings.installStandardMappings`), so
 * the IDE keymap, enablement, and invocation remain authoritative there; the standalone host registers
 * [defaultDefinitions] in its action catalog and resolves them through the Jewel keymap.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public object JewelActions {
    /** Standard Copy action. */
    public val Copy: JewelAction = JewelAction(JewelActionId("org.jetbrains.jewel.action.copy"), "Copy")

    /** Standard Cut action. */
    public val Cut: JewelAction = JewelAction(JewelActionId("org.jetbrains.jewel.action.cut"), "Cut")

    /** Standard Paste action. */
    public val Paste: JewelAction = JewelAction(JewelActionId("org.jetbrains.jewel.action.paste"), "Paste")

    /** Standard Select All action. */
    public val SelectAll: JewelAction = JewelAction(JewelActionId("org.jetbrains.jewel.action.selectAll"), "Select All")

    /** Returns all standard edit actions. */
    public fun all(): List<JewelAction> = listOf(Copy, Cut, Paste, SelectAll)

    /** Default one-stroke bindings using the OS primary modifier (Cmd on macOS, Ctrl elsewhere). */
    public fun defaultDefinitions(useMacModifiers: Boolean): List<JewelActionDefinition> {
        fun primary(key: Key) =
            if (useMacModifiers) JewelKeyStroke(key, meta = true) else JewelKeyStroke(key, ctrl = true)
        return listOf(
            JewelActionDefinition(Copy, listOf(JewelKeySequence(primary(Key.C)))),
            JewelActionDefinition(Cut, listOf(JewelKeySequence(primary(Key.X)))),
            JewelActionDefinition(Paste, listOf(JewelKeySequence(primary(Key.V)))),
            JewelActionDefinition(SelectAll, listOf(JewelKeySequence(primary(Key.A)))),
        )
    }
}
