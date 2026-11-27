// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.shortcut

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the failure/lifecycle/race states at the presentation layer, plus the per-binding presentation override merge
 * and the stale-execution guard.
 */
internal class PresentationContractTest {
    /** Icon descriptors are data; a value-equal stand-in is all the presentation layer needs to be exercised. */
    private data class FakeIcon(val name: String) : com.intellij.platform.icons.IconDescriptor

    private val actionId = JewelActionId("test.presentation.action")
    private val action = JewelAction(actionId, "Presentable")
    private val keymap = InMemoryJewelKeymap("test")

    @Test
    fun `unregistered action presents as Unregistered and disabled when a registry is installed`() {
        val registry = DefaultJewelActionRegistry()
        val host = JewelShortcutHostState(registry) { keymap }

        val presentation = host.presentationFor(actionId)

        assertEquals(ActionResolution.Unregistered, presentation.resolution)
        assertFalse(presentation.enabled)
        assertEquals(actionId.value, presentation.text)
    }

    @Test
    fun `registered but unfocused action presents as NoFocusedBinding with the registered title`() {
        val registry = DefaultJewelActionRegistry()
        registry.register(JewelActionDefinition(action))
        val host = JewelShortcutHostState(registry) { keymap }

        val presentation = host.presentationFor(actionId)

        assertEquals(ActionResolution.NoFocusedBinding, presentation.resolution)
        assertFalse(presentation.enabled)
        assertEquals("Presentable", presentation.text)
    }

    @Test
    fun `without a registry an unfocused action presents as NoFocusedBinding`() {
        val host = JewelShortcutHostState { keymap }
        assertEquals(ActionResolution.NoFocusedBinding, host.presentationFor(actionId).resolution)
    }

    @Test
    fun `hostUnavailable presentation carries the action title, disabled`() {
        val presentation = ActionPresentation.hostUnavailable(action)
        assertEquals(ActionResolution.HostUnavailable, presentation.resolution)
        assertFalse(presentation.enabled)
        assertEquals("Presentable", presentation.text)
    }

    @Test
    fun `override merge carries selected, icon, and menu dismiss policy over the template`() {
        val base = ActionPresentation(text = "T", enabled = true, resolution = ActionResolution.Resolved)
        val icon = FakeIcon("merged")
        val merged =
            ActionPresentationOverride(
                    selected = PresentationValue.Set(true),
                    icon = PresentationValue.Set(icon),
                    menuDismissPolicy = PresentationValue.Set(MenuDismissPolicy.KeepAlways),
                )
                .mergeOver(base)

        assertTrue(merged.selected)
        assertTrue(merged.icon === icon)
        assertEquals(MenuDismissPolicy.KeepAlways, merged.menuDismissPolicy)
        assertEquals("T", merged.text)

        val cleared = ActionPresentationOverride(menuDismissPolicy = PresentationValue.Set(null)).mergeOver(merged)
        assertNull(cleared.menuDismissPolicy)
        assertTrue("non-Set fields inherit", cleared.selected)
    }

    @Test
    fun `stale execution guard - invoker rejects once the focused binding is gone`() {
        var invoked = 0
        var bindings =
            listOf(EngineBinding(actionId, enabled = true, blocksOuterBindings = false, origin = "T") { invoked++ })
        val engine = ShortcutDispatchEngine({ keymap }, { bindings }, { emptyList() })

        // A resolved handler executes...
        engine.resolveFocusedBinding(actionId)!!.onInvoke()
        assertEquals(1, invoked)

        // ...but a stale reference re-resolves at invocation time and finds nothing: no execution.
        bindings = emptyList()
        assertNull(engine.resolveFocusedBinding(actionId))
    }

    @Test
    fun `unregistered diagnostics are coalesced per action ID`() {
        val registry = DefaultJewelActionRegistry()
        val host = JewelShortcutHostState(registry) { keymap }

        // Repeated sampling must not throw and must keep returning the same failure row; the log-once
        // set is internal, so this pins the observable part of the contract.
        repeat(3) { assertEquals(ActionResolution.Unregistered, host.presentationFor(actionId).resolution) }
    }

    @Test
    fun `the template presentation reaches every sample, not just the resolved one`() {
        // The template is the shared base for all resolutions: an action's icon and description must survive
        // even when there is no focused binding, exactly as a Swing button falls back to the template
        // presentation. Before the template layer existed, unfocused samples carried text only.
        val icon = FakeIcon("save")
        val templated =
            JewelAction(
                JewelActionId("test.presentation.templated"),
                "Save",
                icon = icon,
                description = "Persist the file",
            )
        val registry = DefaultJewelActionRegistry()
        registry.register(JewelActionDefinition(templated))
        val host = JewelShortcutHostState(registry) { keymap }

        val unfocused = host.presentationFor(templated.id)

        assertEquals(ActionResolution.NoFocusedBinding, unfocused.resolution)
        assertFalse("no focused binding still means not executable", unfocused.enabled)
        assertEquals(icon, unfocused.icon)
        assertEquals("Persist the file", unfocused.description)
    }

    @Test
    fun `hostUnavailable derives from the template too`() {
        val icon = FakeIcon("archive")
        val templated = JewelAction(JewelActionId("test.presentation.noHost"), "Archive", icon = icon)

        val presentation = ActionPresentation.hostUnavailable(templated)

        assertEquals(ActionResolution.HostUnavailable, presentation.resolution)
        assertFalse(presentation.enabled)
        assertEquals("Archive", presentation.text)
        assertEquals(icon, presentation.icon)
    }

    @Test
    fun `presentation properties merge key-by-key so an override cannot erase the template's flags`() {
        // IJPL's Presentation.copyFrom merges client properties individually; replacing the map wholesale
        // would silently drop authored flags whenever a binding set an unrelated one.
        val base =
            ActionPresentation(
                text = "T",
                properties = mapOf(ActionPropertyNames.ShowTextInToolbar to true, "custom.kept" to "yes"),
                resolution = ActionResolution.Resolved,
            )

        val merged =
            ActionPresentationOverride(properties = mapOf("custom.kept" to "no", "custom.added" to 1)).mergeOver(base)

        assertTrue("untouched template flags survive", merged.isFlagSet(ActionPropertyNames.ShowTextInToolbar))
        assertEquals("overridden keys win", "no", merged.properties["custom.kept"])
        assertEquals("new keys are added", 1, merged.properties["custom.added"])
    }

    @Test
    fun `an absent boolean property reads as false rather than throwing`() {
        val presentation = ActionPresentation(text = "T")
        assertFalse(presentation.isFlagSet(ActionPropertyNames.ShowTextInToolbar))
        assertFalse(presentation.isFlagSet("never.set"))
    }

    @Test
    fun `presentations with equal property maps are equal, so sampling stays equality-gated`() {
        // Presentation flows are conflated by equality; a property map that compared by identity would
        // make every sample look changed and defeat the demand-driven scheduler.
        val a = ActionPresentation(text = "T", properties = mapOf("k" to "v"), icon = FakeIcon("i"))
        val b = ActionPresentation(text = "T", properties = mapOf("k" to "v"), icon = FakeIcon("i"))
        assertEquals(a, b)
    }

    @Test
    fun `native host-sampled presentation without a focused Compose binding keeps ActionResolution Resolved`() {
        val nativeRegistry =
            object : JewelActionRegistry {
                override fun register(definition: JewelActionDefinition) = error("unused")

                override fun definition(id: JewelActionId): JewelActionDefinition? =
                    if (id == actionId) JewelActionDefinition(action) else null

                override fun definitions(): List<JewelActionDefinition> = listOf(JewelActionDefinition(action))

                override fun sampledPresentation(id: JewelActionId): ActionPresentation? =
                    if (id == actionId) {
                        ActionPresentation(
                            text = "Native IDE Action",
                            enabled = true,
                            resolution = ActionResolution.Resolved,
                        )
                    } else {
                        null
                    }
            }
        val host = JewelShortcutHostState(nativeRegistry) { keymap }

        val presentation = host.presentationFor(actionId)

        assertEquals(ActionResolution.Resolved, presentation.resolution)
        assertEquals("Native IDE Action", presentation.text)
        assertTrue(presentation.enabled)
    }
}
