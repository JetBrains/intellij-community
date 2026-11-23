package org.jetbrains.jewel.samples.standalone

import org.jetbrains.jewel.foundation.shortcut.DefaultJewelActionRegistry
import org.jetbrains.jewel.foundation.shortcut.InMemoryJewelKeymap
import org.jetbrains.jewel.foundation.shortcut.JewelShortcutHostState
import org.jetbrains.jewel.samples.showcase.ShowcaseShortcuts
import org.jetbrains.skiko.hostOs

/**
 * The standalone sample's shortcut host: the action catalog, the (mutable, live-rebindable) keymap seeded from the
 * catalog's OS-appropriate defaults, and the per-window host state. The window installs
 * [JewelShortcutHostState.onPreviewKeyEvent] as its AWT-level pre-scene hook and
 * [JewelShortcutHostState.resolverRootModifier] at the content root; pages then declare commands with plain
 * `Modifier.shortcut` and never see host-specific types.
 */
public object ShowcaseShortcutHost {
    /** Action registry holding the showcase's command definitions. */
    public val registry: DefaultJewelActionRegistry = DefaultJewelActionRegistry()

    init {
        ShowcaseShortcuts.definitions(useMacModifiers = hostOs.isMacOS).forEach(registry::register)
    }

    /** Mutable keymap seeded from the OS-appropriate default shortcuts. */
    public val keymap: InMemoryJewelKeymap = InMemoryJewelKeymap.fromDefaults("showcase", registry)

    /** Shortcut dispatch host state bound to [registry] and [keymap]. */
    public val state: JewelShortcutHostState = JewelShortcutHostState(registry) { keymap }
}
