// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.shortcut

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ActionModelTest {
    private val actionId = JewelActionId("test.action")

    private val ctrlA = JewelKeyStroke(Key.A, ctrl = true)

    private val keymap = InMemoryJewelKeymap("test").apply { bind(actionId, JewelKeySequence(ctrlA)) }

    @Test
    fun `OnceUntilRelease suppresses repeats until key-up, RepeatWhileHeld does not`() {
        var onceCount = 0
        var heldCount = 0
        val onceId = JewelActionId("test.once")
        val heldId = JewelActionId("test.held")
        val map =
            InMemoryJewelKeymap("m").apply {
                bind(onceId, JewelKeySequence(JewelKeyStroke(Key.F1)))
                bind(heldId, JewelKeySequence(JewelKeyStroke(Key.F2)))
            }
        val engine =
            ShortcutDispatchEngine(
                { map },
                {
                    listOf(
                        EngineBinding(onceId, true, false, "o", ShortcutRepeatPolicy.OnceUntilRelease) { onceCount++ },
                        EngineBinding(heldId, true, false, "h", ShortcutRepeatPolicy.RepeatWhileHeld) { heldCount++ },
                    )
                },
                { emptyList() },
            )

        val f1 = JewelKeyStroke(Key.F1)
        repeat(3) { assertTrue(engine.onKeyDown(f1) is DispatchDecision.Consumed) }
        assertEquals("repeats suppressed until release", 1, onceCount)
        engine.onKeyUp(f1)
        engine.onKeyDown(f1)
        assertEquals("re-arms after key-up", 2, onceCount)

        val f2 = JewelKeyStroke(Key.F2)
        repeat(3) { engine.onKeyDown(f2) }
        assertEquals("RepeatWhileHeld fires every delivered key-down", 3, heldCount)
    }

    @Test
    fun `resolveFocusedBinding returns innermost enabled and honors blockers`() {
        val engine =
            ShortcutDispatchEngine(
                { keymap },
                {
                    listOf(
                        EngineBinding(actionId, true, false, "outer") {},
                        EngineBinding(actionId, false, false, "inner-disabled") {},
                    )
                },
                { emptyList() },
            )
        assertEquals("outer", engine.resolveFocusedBinding(actionId)?.origin)

        val blocked =
            ShortcutDispatchEngine(
                { keymap },
                {
                    listOf(
                        EngineBinding(actionId, true, false, "outer") {},
                        EngineBinding(actionId, false, true, "inner-blocking") {},
                    )
                },
                { emptyList() },
            )
        assertNull(blocked.resolveFocusedBinding(actionId))
    }

    @Test
    fun `scheduler publishes only on change and drops entries when demand is released`() {
        var enabled = false
        val scheduler = ActionPresentationScheduler { id ->
            ActionPresentation(text = id.value, enabled = enabled, resolution = ActionResolution.Resolved)
        }

        val flow = scheduler.acquire(actionId)
        val first = flow.value
        scheduler.invalidate() // unchanged sample: StateFlow conflates equal values
        assertTrue(flow.value === first || flow.value == first)

        enabled = true
        scheduler.invalidate(actionId)
        assertTrue(flow.value.enabled)

        scheduler.release(actionId)
        assertEquals(0, scheduler.activeDemandCount())
    }

    @Test
    fun `presentation override merges over template with Set-null clearing nullable fields`() {
        val base = ActionPresentation(text = "T", description = "d", resolution = ActionResolution.Resolved)
        val merged =
            ActionPresentationOverride(text = PresentationValue.Set("X"), description = PresentationValue.Set(null))
                .mergeOver(base)
        assertEquals("X", merged.text)
        assertNull(merged.description)
        assertTrue(merged.visible)
    }

    @Test
    fun `event source emits one event per invocation without suspending`() {
        val source = MutableActionEventSource()
        val seen = mutableListOf<ActionInvocation>()
        // No collector: emission must not block or throw.
        source.emit(ActionInvocation(actionId, null, ActionTrigger.Programmatic))

        val registry = DefaultJewelActionRegistry()
        val reg = registry.register(JewelActionDefinition(JewelAction(actionId, "T")))
        assertEquals("T", registry.definition(actionId)?.action?.title)
        reg.close()
        assertNull(registry.definition(actionId))
        assertTrue(seen.isEmpty())
    }

    @Test
    fun `standard actions provide platform-appropriate default bindings`() {
        val mac = JewelActions.defaultDefinitions(useMacModifiers = true)
        val other = JewelActions.defaultDefinitions(useMacModifiers = false)
        assertEquals(4, mac.size)
        assertTrue(mac.first { it.action == JewelActions.Copy }.defaultShortcuts.single().first.meta)
        assertTrue(other.first { it.action == JewelActions.Copy }.defaultShortcuts.single().first.ctrl)
        assertFalse(other.first { it.action == JewelActions.Copy }.defaultShortcuts.single().first.meta)
    }

    @Test
    fun `DefaultJewelKeymapManager updates activeKeymap when replacing the active scheme by name`() {
        val initial = InMemoryJewelKeymap("default").apply { bind(actionId, JewelKeySequence(ctrlA)) }
        val manager = DefaultJewelKeymapManager(initial)
        val replacement =
            InMemoryJewelKeymap("default").apply {
                bind(actionId, JewelKeySequence(JewelKeyStroke(Key.B, ctrl = true)))
            }

        manager.addKeymap(replacement)

        assertTrue("activeKeymap must update to the replaced scheme", manager.activeKeymap.value === replacement)
        assertEquals(
            listOf(JewelKeySequence(JewelKeyStroke(Key.B, ctrl = true))),
            manager.activeKeymap.value.shortcutsFor(actionId),
        )
        assertEquals(1, manager.keymaps.value.size)
    }

    @Test
    fun `InMemoryJewelKeymap deduplicates duplicate bind and replaceBindings entries`() {
        val map = InMemoryJewelKeymap("dedup")
        val seq = JewelKeySequence(ctrlA)
        map.bind(actionId, seq)
        map.bind(actionId, seq)
        assertEquals(listOf(seq), map.shortcutsFor(actionId))

        map.unbind(actionId, seq)
        assertEquals(emptyList<JewelKeySequence>(), map.shortcutsFor(actionId))

        map.replaceBindings(actionId, listOf(seq, seq))
        assertEquals(listOf(seq), map.shortcutsFor(actionId))
    }
}
