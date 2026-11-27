// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.samples.showcase

import androidx.compose.ui.input.key.Key
import org.jetbrains.jewel.foundation.shortcut.JewelAction
import org.jetbrains.jewel.foundation.shortcut.JewelActionDefinition
import org.jetbrains.jewel.foundation.shortcut.JewelActionId
import org.jetbrains.jewel.foundation.shortcut.JewelKeySequence
import org.jetbrains.jewel.foundation.shortcut.JewelKeyStroke

/**
 * The showcase's host-visible commands, declared once and bound where the owning UI lives: page navigation is bound at
 * the window content root (ambient — active while focus is anywhere in the window), section switching on the components
 * page, and the tester page binds its own demo actions.
 *
 * Hosts register [definitions] in their action registry and seed a keymap from the default shortcuts
 * (`InMemoryJewelKeymap.fromDefaults`); users of the standalone sample can then rebind them from the tester page's
 * keymap panel.
 */
public object ShowcaseShortcuts {
    /** Navigates to the Welcome showcase page. */
    public val NavigateWelcome: JewelAction =
        JewelAction(JewelActionId("org.jetbrains.jewel.showcase.navigate.welcome"), "Go to Welcome")

    /** Navigates to the Components showcase page. */
    public val NavigateComponents: JewelAction =
        JewelAction(JewelActionId("org.jetbrains.jewel.showcase.navigate.components"), "Go to Components")

    /** Navigates to the Markdown showcase page. */
    public val NavigateMarkdown: JewelAction =
        JewelAction(JewelActionId("org.jetbrains.jewel.showcase.navigate.markdown"), "Go to Markdown")

    /** Navigates to the Shortcuts showcase section. */
    public val NavigateShortcuts: JewelAction =
        JewelAction(JewelActionId("org.jetbrains.jewel.showcase.navigate.shortcuts"), "Go to Shortcuts")

    /** Selects the next component showcase section. */
    public val NextSection: JewelAction =
        JewelAction(JewelActionId("org.jetbrains.jewel.showcase.section.next"), "Next Section")

    /** Selects the previous component showcase section. */
    public val PreviousSection: JewelAction =
        JewelAction(JewelActionId("org.jetbrains.jewel.showcase.section.previous"), "Previous Section")

    // Tester page demo actions.

    /** Demonstrates a keymap-bound Select All action. */
    public val DemoSelectAll: JewelAction =
        JewelAction(JewelActionId("org.jetbrains.jewel.showcase.demo.selectAll"), "Select All (demo)")

    /** Demonstrates a keymap-bound Ping action. */
    public val DemoPing: JewelAction =
        JewelAction(JewelActionId("org.jetbrains.jewel.showcase.demo.ping"), "Ping (demo)")

    /** Demonstrates a two-stroke chord Reformat action. */
    public val DemoReformat: JewelAction =
        JewelAction(JewelActionId("org.jetbrains.jewel.showcase.demo.reformat"), "Reformat (demo chord)")

    /**
     * Catalog with default one-stroke and chord bindings. [useMacModifiers] picks ⌘ for the section-switching strokes
     * (Ctrl elsewhere); page navigation stays Alt-based on every OS, matching the sample's historical bindings.
     */
    public fun definitions(useMacModifiers: Boolean): List<JewelActionDefinition> {
        fun alt(key: Key) = JewelKeySequence(JewelKeyStroke(key, alt = true))

        fun primary(key: Key) =
            if (useMacModifiers) JewelKeyStroke(key, meta = true) else JewelKeyStroke(key, ctrl = true)
        return listOf(
            JewelActionDefinition(NavigateWelcome, listOf(alt(Key.W))),
            JewelActionDefinition(NavigateComponents, listOf(alt(Key.C))),
            JewelActionDefinition(NavigateMarkdown, listOf(alt(Key.M))),
            JewelActionDefinition(NavigateShortcuts, listOf(alt(Key.S))),
            JewelActionDefinition(NextSection, listOf(JewelKeySequence(primary(Key.DirectionDown)))),
            JewelActionDefinition(PreviousSection, listOf(JewelKeySequence(primary(Key.DirectionUp)))),
            JewelActionDefinition(DemoSelectAll, listOf(JewelKeySequence(primary(Key.A)))),
            JewelActionDefinition(DemoPing, listOf(alt(Key.G))),
            JewelActionDefinition(DemoReformat, listOf(JewelKeySequence(primary(Key.K), primary(Key.D)))),
        )
    }
}
