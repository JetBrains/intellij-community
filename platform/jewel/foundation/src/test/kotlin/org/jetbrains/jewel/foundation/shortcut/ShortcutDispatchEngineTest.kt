// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.shortcut

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the dispatch contract, including the corner cases: claim precedence, nearest-enabled-binding with fallthrough,
 * chord complete/cancel-consume/recover, exact-single-stroke-beats-chord-prefix, modifier-only filtering, typed-event
 * suppression, and never swallowing unbound input.
 */
internal class ShortcutDispatchEngineTest {
    private val selectAll = JewelActionId("test.selectAll")
    private val reformat = JewelActionId("test.reformat")

    private val ctrlA = JewelKeyStroke(Key.A, ctrl = true)
    private val ctrlK = JewelKeyStroke(Key.K, ctrl = true)
    private val ctrlD = JewelKeyStroke(Key.D, ctrl = true)
    private val ctrlEnter = JewelKeyStroke(Key.Enter, ctrl = true)
    private val plainQ = JewelKeyStroke(Key.Q)

    private val keymap =
        InMemoryJewelKeymap("test").apply {
            bind(selectAll, JewelKeySequence(ctrlA))
            bind(reformat, JewelKeySequence(ctrlK, ctrlD))
        }

    private class Recorder {
        val invocations = mutableListOf<String>()

        fun binding(
            actionId: JewelActionId,
            name: String,
            enabled: Boolean = true,
            blocksOuter: Boolean = false,
        ): EngineBinding = EngineBinding(actionId, enabled, blocksOuter, name) { invocations.add(name) }

        fun claim(
            sequence: JewelKeySequence,
            name: String,
            enabled: Boolean = true,
            blocksOuter: Boolean = false,
        ): EngineClaim = EngineClaim(sequence, enabled, blocksOuterClaims = blocksOuter) { invocations.add(name) }
    }

    private fun engine(
        bindings: () -> List<EngineBinding>,
        claims: () -> List<EngineClaim> = { emptyList() },
    ): ShortcutDispatchEngine = ShortcutDispatchEngine({ keymap }, bindings, claims)

    @Test
    fun `keymap command resolves to the focused binding`() {
        val recorder = Recorder()
        val engine = engine({ listOf(recorder.binding(selectAll, "table")) })

        val decision = engine.onKeyDown(ctrlA)

        assertTrue(decision is DispatchDecision.Consumed)
        assertEquals(listOf("table"), recorder.invocations)
    }

    @Test
    fun `innermost focused binding wins over outer binding for the same action`() {
        val recorder = Recorder()
        // Innermost bindings come last, matching pre-order traversal of the focused path.
        val engine = engine({ listOf(recorder.binding(selectAll, "table"), recorder.binding(selectAll, "editor")) })

        engine.onKeyDown(ctrlA)

        assertEquals(listOf("editor"), recorder.invocations)
    }

    @Test
    fun `disabled inner binding falls through to the outer one`() {
        val recorder = Recorder()
        val engine =
            engine({
                listOf(recorder.binding(selectAll, "table"), recorder.binding(selectAll, "editor", enabled = false))
            })

        engine.onKeyDown(ctrlA)

        assertEquals(listOf("table"), recorder.invocations)
    }

    @Test
    fun `disabled inner binding with blocksOuterBindings makes the action unavailable`() {
        val recorder = Recorder()
        val engine =
            engine({
                listOf(
                    recorder.binding(selectAll, "table"),
                    recorder.binding(selectAll, "editor", enabled = false, blocksOuter = true),
                )
            })

        val decision = engine.onKeyDown(ctrlA)

        assertEquals(DispatchDecision.Pass, decision)
        assertTrue(recorder.invocations.isEmpty())
    }

    @Test
    fun `focused claim wins before keymap lookup`() {
        val recorder = Recorder()
        val engine =
            engine(
                bindings = { listOf(recorder.binding(selectAll, "table")) },
                claims = { listOf(recorder.claim(JewelKeySequence(ctrlA), "claim")) },
            )

        val decision = engine.onKeyDown(ctrlA)

        assertTrue(decision is DispatchDecision.Consumed)
        assertEquals(DispatchDecision.Consumed.Route.Claim, (decision as DispatchDecision.Consumed).route)
        assertEquals(listOf("claim"), recorder.invocations)
    }

    @Test
    fun `claims work for strokes with no keymap mapping`() {
        val recorder = Recorder()
        val engine = engine({ emptyList() }, { listOf(recorder.claim(JewelKeySequence(ctrlEnter), "submit")) })

        assertTrue(engine.onKeyDown(ctrlEnter) is DispatchDecision.Consumed)
        assertEquals(listOf("submit"), recorder.invocations)
    }

    @Test
    fun `unbound input is never swallowed`() {
        val engine = engine({ emptyList() })

        assertEquals(DispatchDecision.Pass, engine.onKeyDown(JewelKeyStroke(Key.B)))
    }

    @Test
    fun `keymap hit with no focused binding is not consumed`() {
        val engine = engine({ emptyList() })

        assertEquals(DispatchDecision.Pass, engine.onKeyDown(ctrlA))
    }

    @Test
    fun `chord completes through pending state`() {
        val recorder = Recorder()
        val engine = engine({ listOf(recorder.binding(reformat, "table")) })

        val first = engine.onKeyDown(ctrlK)
        assertEquals(DispatchDecision.Consumed.Route.ChordPrefix, (first as DispatchDecision.Consumed).route)
        assertTrue(engine.isAwaitingSecondStroke)

        val second = engine.onKeyDown(ctrlD)
        assertEquals(DispatchDecision.Consumed.Route.Keymap, (second as DispatchDecision.Consumed).route)
        assertEquals(listOf("table"), recorder.invocations)
        assertFalse(engine.isAwaitingSecondStroke)
    }

    @Test
    fun `nonmatching second stroke cancels and consumes without invoking its own binding`() {
        val recorder = Recorder()
        val engine =
            engine({ listOf(recorder.binding(reformat, "table"), recorder.binding(selectAll, "table-select")) })

        engine.onKeyDown(ctrlK)
        val cancel = engine.onKeyDown(ctrlA) // bound as a single stroke, but arrives as a wrong second stroke

        assertEquals(DispatchDecision.Consumed.Route.ChordCancelled, (cancel as DispatchDecision.Consumed).route)
        assertTrue(recorder.invocations.isEmpty())

        // Recovery: the next Ctrl+A dispatches normally.
        engine.onKeyDown(ctrlA)
        assertEquals(listOf("table-select"), recorder.invocations)
    }

    @Test
    fun `modifier-only key-downs do not cancel a pending chord`() {
        val recorder = Recorder()
        val engine = engine({ listOf(recorder.binding(reformat, "table")) })

        engine.onKeyDown(ctrlK)
        // The Ctrl press ahead of the second stroke reports null (modifier-only) and must be ignored.
        assertEquals(DispatchDecision.Pass, engine.onKeyDown(null))
        assertTrue(engine.isAwaitingSecondStroke)

        engine.onKeyDown(ctrlD)
        assertEquals(listOf("table"), recorder.invocations)
    }

    @Test
    fun `modifier-only key-down is null stroke`() {
        // Guards JewelKeyStroke.fromKeyDownOrNull's contract at the engine boundary.
        val engine = engine({ emptyList() })
        assertEquals(DispatchDecision.Pass, engine.onKeyDown(null))
    }

    @Test
    fun `consumed key-down arms typed suppression exactly once`() {
        val recorder = Recorder()
        val engine = engine({ emptyList() }, { listOf(recorder.claim(JewelKeySequence(plainQ), "macro")) })

        engine.onKeyDown(plainQ)
        assertTrue("the typed event following a consumed press must be swallowed", engine.onTypedEvent())
        assertFalse("suppression is one-shot", engine.onTypedEvent())
    }

    @Test
    fun `passed-through key-down does not arm typed suppression`() {
        val engine = engine({ emptyList() })

        engine.onKeyDown(JewelKeyStroke(Key.B))

        assertFalse(engine.onTypedEvent())
    }

    @Test
    fun `chord prefix arms typed suppression and repeated prefix key-downs stay consumed`() {
        val recorder = Recorder()
        val engine = engine({ listOf(recorder.binding(reformat, "table")) })

        engine.onKeyDown(ctrlK)
        assertTrue(engine.onTypedEvent())

        // A repeated first stroke while pending arrives as a "second stroke" and cancels conservatively;
        // it is still consumed, and dispatch recovers.
        val repeat = engine.onKeyDown(ctrlK)
        assertTrue(repeat is DispatchDecision.Consumed)
    }

    @Test
    fun `reset clears pending chord state`() {
        val engine = engine({ listOf(EngineBinding(reformat, true, false, "t") {}) })

        engine.onKeyDown(ctrlK)
        assertTrue(engine.isAwaitingSecondStroke)

        engine.reset()

        assertFalse(engine.isAwaitingSecondStroke)
    }

    @Test
    fun `keymap inheritance hides and replaces bindings correctly`() {
        val parent =
            InMemoryJewelKeymap("parent").apply {
                bind(selectAll, JewelKeySequence(ctrlA))
                bind(reformat, JewelKeySequence(ctrlK, ctrlD))
            }
        val child = InMemoryJewelKeymap("child", parent)

        assertEquals(listOf(JewelKeySequence(ctrlA)), child.shortcutsFor(selectAll))

        child.hideInherited(selectAll, JewelKeySequence(ctrlA))
        assertTrue(child.shortcutsFor(selectAll).isEmpty())

        child.bind(selectAll, JewelKeySequence(ctrlEnter))
        assertEquals(listOf(JewelKeySequence(ctrlEnter)), child.shortcutsFor(selectAll))

        assertEquals(listOf(reformat), child.actionIdsForPrefix(ctrlK))
    }

    @Test
    fun `veto predicate agrees with delivery for enabled claims`() {
        val recorder = Recorder()
        val engine = engine({ emptyList() }, { listOf(recorder.claim(JewelKeySequence(ctrlEnter), "submit")) })

        assertTrue(engine.claimsStroke(ctrlEnter))
        assertTrue(engine.onKeyDown(ctrlEnter) is DispatchDecision.Consumed)
        assertFalse(engine.claimsStroke(plainQ))
    }

    @Test
    fun `veto predicate agrees with delivery when a disabled inner claim blocks an enabled outer one`() {
        val recorder = Recorder()
        val engine =
            engine(
                bindings = { emptyList() },
                claims = {
                    // Innermost claims come last: the enabled outer claim would match, but the disabled
                    // blocking inner claim stops resolution. The veto must see the same outcome, or a host
                    // would suppress its own action for a stroke Jewel then refuses to deliver.
                    listOf(
                        recorder.claim(JewelKeySequence(ctrlEnter), "outer"),
                        recorder.claim(JewelKeySequence(ctrlEnter), "inner", enabled = false, blocksOuter = true),
                    )
                },
            )

        assertFalse(engine.claimsStroke(ctrlEnter))
        assertEquals(DispatchDecision.Pass, engine.onKeyDown(ctrlEnter))
        assertTrue(recorder.invocations.isEmpty())
    }

    @Test
    fun `reset clears the OnceUntilRelease repeat latch`() {
        var invocations = 0
        val binding =
            EngineBinding(
                actionId = selectAll,
                enabled = true,
                blocksOuterBindings = false,
                origin = "table",
                repeatPolicy = ShortcutRepeatPolicy.OnceUntilRelease,
            ) {
                invocations++
            }
        val engine = engine({ listOf(binding) })

        engine.onKeyDown(ctrlA)
        assertEquals(1, invocations)

        // Focus left mid-hold: reset() ran, and the key-up landed in another surface, so this host
        // never sees it. A fresh press of the same stroke must invoke, not be swallowed as a repeat.
        engine.reset()
        val decision = engine.onKeyDown(ctrlA)

        assertTrue(decision is DispatchDecision.Consumed)
        assertEquals(DispatchDecision.Consumed.Route.Keymap, (decision as DispatchDecision.Consumed).route)
        assertEquals(2, invocations)
    }

    @Test
    fun `veto predicate never matches a two-stroke claim delivery cannot invoke`() {
        val recorder = Recorder()
        val engine = engine({ emptyList() }, { listOf(recorder.claim(JewelKeySequence(ctrlK, ctrlD), "chordy")) })

        assertFalse(engine.claimsStroke(ctrlK))
        assertEquals(DispatchDecision.Pass, engine.onKeyDown(ctrlK))
        assertTrue(recorder.invocations.isEmpty())
    }

    @Test
    fun `OnceUntilRelease re-arms when the modifier is released before the key`() {
        var invocations = 0
        val ctrlS = JewelKeyStroke(Key.S, ctrl = true)
        val onceKeymap = InMemoryJewelKeymap("once").apply { bind(selectAll, JewelKeySequence(ctrlS)) }
        val engine =
            ShortcutDispatchEngine(
                { onceKeymap },
                {
                    listOf(
                        EngineBinding(selectAll, true, false, "once", ShortcutRepeatPolicy.OnceUntilRelease) {
                            invocations++
                        }
                    )
                },
                { emptyList() },
            )

        engine.onKeyDown(ctrlS)
        engine.onKeyDown(ctrlS)
        assertEquals(1, invocations)

        // Modifier-first release via onKeyUp(Key): Ctrl goes up first, then S goes up without Ctrl.
        engine.onKeyUp(Key.CtrlLeft)
        engine.onKeyUp(Key.S)

        engine.onKeyDown(ctrlS)
        assertEquals(2, invocations)

        // Modifier-first release via onKeyUp(JewelKeyStroke) delegates identically to onKeyUp(Key).
        engine.onKeyDown(ctrlS)
        assertEquals(2, invocations)
        engine.onKeyUp(JewelKeyStroke(Key.CtrlLeft))
        engine.onKeyDown(ctrlS)
        assertEquals(3, invocations)
    }

    @Test
    fun `OnceUntilRelease re-arms on macOS-style modifier-only release`() {
        var invocations = 0
        val metaS = JewelKeyStroke(Key.S, meta = true)
        val onceKeymap = InMemoryJewelKeymap("once-meta").apply { bind(selectAll, JewelKeySequence(metaS)) }
        val engine =
            ShortcutDispatchEngine(
                { onceKeymap },
                {
                    listOf(
                        EngineBinding(selectAll, true, false, "once", ShortcutRepeatPolicy.OnceUntilRelease) {
                            invocations++
                        }
                    )
                },
                { emptyList() },
            )

        engine.onKeyDown(metaS)
        assertEquals(1, invocations)

        // macOS AWT delivers only Meta key-up when S is released while Command stays held.
        engine.onKeyUp(Key.MetaLeft)

        engine.onKeyDown(metaS)
        assertEquals(2, invocations)
    }

    @Test
    fun `expired pending chord drops silently and evaluates next stroke fresh`() {
        var now = 1_000L
        val recorder = Recorder()
        val pendingTransitions = mutableListOf<JewelKeyStroke?>()
        val engine =
            ShortcutDispatchEngine(
                keymap = { keymap },
                focusedBindings = {
                    listOf(recorder.binding(selectAll, "selectAll"), recorder.binding(reformat, "reformat"))
                },
                focusedClaims = { emptyList() },
                chordTimeoutMillis = 2_000L,
                clockMillis = { now },
                onPendingChordChanged = { pendingTransitions.add(it) },
            )

        assertEquals(
            DispatchDecision.Consumed(null, null, DispatchDecision.Consumed.Route.ChordPrefix),
            engine.onKeyDown(ctrlK),
        )
        assertTrue(engine.isAwaitingSecondStroke)

        // Advance past timeout and press Ctrl+A: should silently drop Ctrl+K and run selectAll (NOT ChordCancelled).
        now += 2_500L
        val decision = engine.onKeyDown(ctrlA)
        assertFalse(engine.isAwaitingSecondStroke)
        assertEquals(
            DispatchDecision.Consumed(selectAll, JewelKeySequence(ctrlA), DispatchDecision.Consumed.Route.Keymap),
            decision,
        )
        assertEquals(listOf("selectAll"), recorder.invocations)
        assertEquals(listOf(ctrlK, null), pendingTransitions)
    }

    @Test
    fun `cancelPendingChord drops armed chord without consuming`() {
        val recorder = Recorder()
        val engine = engine({ listOf(recorder.binding(reformat, "reformat")) })

        assertFalse(engine.cancelPendingChord())
        engine.onKeyDown(ctrlK)
        assertTrue(engine.isAwaitingSecondStroke)
        assertTrue(engine.cancelPendingChord())
        assertFalse(engine.isAwaitingSecondStroke)
        assertFalse(engine.cancelPendingChord())
    }

    @Test
    fun `two different actions sharing a one-stroke shortcut run the innermost focused binding`() {
        val closeDialog = JewelActionId("test.closeDialog")
        val cancelEdit = JewelActionId("test.cancelEdit")
        val escape = JewelKeySequence(JewelKeyStroke(Key.Escape))
        val sharedKeymap =
            InMemoryJewelKeymap("shared").apply {
                // Bind outer action first so keymap order disagrees with nesting depth.
                bind(closeDialog, escape)
                bind(cancelEdit, escape)
            }
        val recorder = Recorder()
        val engine =
            ShortcutDispatchEngine(
                { sharedKeymap },
                { listOf(recorder.binding(closeDialog, "outer dialog"), recorder.binding(cancelEdit, "inner editor")) },
                { emptyList() },
            )

        val decision = engine.onKeyDown(JewelKeyStroke(Key.Escape))

        assertEquals(DispatchDecision.Consumed(cancelEdit, escape, DispatchDecision.Consumed.Route.Keymap), decision)
        assertEquals(listOf("inner editor"), recorder.invocations)
    }

    @Test
    fun `two different actions sharing a chord run the innermost focused binding`() {
        val outerChord = JewelActionId("test.outerChord")
        val innerChord = JewelActionId("test.innerChord")
        val chord = JewelKeySequence(ctrlK, ctrlD)
        val sharedKeymap =
            InMemoryJewelKeymap("shared-chord").apply {
                bind(outerChord, chord)
                bind(innerChord, chord)
            }
        val recorder = Recorder()
        val engine =
            ShortcutDispatchEngine(
                { sharedKeymap },
                { listOf(recorder.binding(outerChord, "outer"), recorder.binding(innerChord, "inner")) },
                { emptyList() },
            )

        assertEquals(
            DispatchDecision.Consumed(null, null, DispatchDecision.Consumed.Route.ChordPrefix),
            engine.onKeyDown(ctrlK),
        )
        val completion = engine.onKeyDown(ctrlD)
        assertEquals(DispatchDecision.Consumed(innerChord, chord, DispatchDecision.Consumed.Route.Keymap), completion)
        assertEquals(listOf("inner"), recorder.invocations)
    }

    @Test
    fun `blocksOuterBindings on one action does not block an unrelated outer action sharing the stroke`() {
        val outerAction = JewelActionId("test.outerAction")
        val innerAction = JewelActionId("test.innerAction")
        val escape = JewelKeySequence(JewelKeyStroke(Key.Escape))
        val sharedKeymap =
            InMemoryJewelKeymap("shared-block").apply {
                bind(innerAction, escape)
                bind(outerAction, escape)
            }
        val recorder = Recorder()
        val engine =
            ShortcutDispatchEngine(
                { sharedKeymap },
                {
                    listOf(
                        recorder.binding(innerAction, "outer-innerAction"),
                        recorder.binding(outerAction, "outer-otherAction"),
                        recorder.binding(innerAction, "disabled-inner", enabled = false, blocksOuter = true),
                    )
                },
                { emptyList() },
            )

        val decision = engine.onKeyDown(JewelKeyStroke(Key.Escape))
        assertEquals(DispatchDecision.Consumed(outerAction, escape, DispatchDecision.Consumed.Route.Keymap), decision)
        assertEquals(listOf("outer-otherAction"), recorder.invocations)
    }

    @Test
    fun `OnceUntilRelease on Enter clears when NumPadEnter is released`() {
        val submit = JewelActionId("test.submit")
        val submitKeymap = InMemoryJewelKeymap("enter").apply { bind(submit, JewelKeySequence(ctrlEnter)) }
        var invocations = 0
        val engine =
            ShortcutDispatchEngine(
                { submitKeymap },
                {
                    listOf(
                        EngineBinding(submit, true, false, "submit", ShortcutRepeatPolicy.OnceUntilRelease) {
                            invocations++
                        }
                    )
                },
                { emptyList() },
            )

        assertEquals(
            DispatchDecision.Consumed(submit, JewelKeySequence(ctrlEnter), DispatchDecision.Consumed.Route.Keymap),
            engine.onKeyDown(ctrlEnter),
        )
        assertEquals(
            DispatchDecision.Consumed(
                submit,
                JewelKeySequence(ctrlEnter),
                DispatchDecision.Consumed.Route.RepeatSuppressed,
            ),
            engine.onKeyDown(ctrlEnter),
        )
        assertEquals(1, invocations)

        engine.onKeyUp(Key.NumPadEnter)

        assertEquals(
            DispatchDecision.Consumed(submit, JewelKeySequence(ctrlEnter), DispatchDecision.Consumed.Route.Keymap),
            engine.onKeyDown(ctrlEnter),
        )
        assertEquals(2, invocations)
    }

    @Test
    fun `overlapping OnceUntilRelease strokes stay latched independently across claims, commands, and bridge helper`() {
        val copyId = JewelActionId("test.overlap.copy")
        val pasteId = JewelActionId("test.overlap.paste")
        val ctrlC = JewelKeyStroke(Key.C, ctrl = true)
        val ctrlV = JewelKeyStroke(Key.V, ctrl = true)
        val overlapKeymap =
            InMemoryJewelKeymap("overlap").apply {
                bind(copyId, JewelKeySequence(ctrlC))
                bind(pasteId, JewelKeySequence(ctrlV))
            }

        // 1) Keymap commands: Ctrl+C down -> Ctrl+V down -> V up -> Ctrl+C repeat stays suppressed.
        var copyRuns = 0
        var pasteRuns = 0
        val cmdEngine =
            ShortcutDispatchEngine(
                { overlapKeymap },
                {
                    listOf(
                        EngineBinding(copyId, true, false, "copy", ShortcutRepeatPolicy.OnceUntilRelease) {
                            copyRuns++
                        },
                        EngineBinding(pasteId, true, false, "paste", ShortcutRepeatPolicy.OnceUntilRelease) {
                            pasteRuns++
                        },
                    )
                },
                { emptyList() },
            )
        assertEquals(
            DispatchDecision.Consumed.Route.Keymap,
            (cmdEngine.onKeyDown(ctrlC) as DispatchDecision.Consumed).route,
        )
        assertEquals(
            DispatchDecision.Consumed.Route.Keymap,
            (cmdEngine.onKeyDown(ctrlV) as DispatchDecision.Consumed).route,
        )
        cmdEngine.onKeyUp(Key.V)
        assertEquals(
            DispatchDecision.Consumed.Route.RepeatSuppressed,
            (cmdEngine.onKeyDown(ctrlC) as DispatchDecision.Consumed).route,
        )
        assertEquals("releasing V must re-arm Ctrl+V", 1, pasteRuns)
        assertEquals(
            DispatchDecision.Consumed.Route.Keymap,
            (cmdEngine.onKeyDown(ctrlV) as DispatchDecision.Consumed).route,
        )
        assertEquals(1, copyRuns)
        assertEquals(2, pasteRuns)
        cmdEngine.onKeyUp(Key.CtrlLeft)
        assertEquals(
            DispatchDecision.Consumed.Route.Keymap,
            (cmdEngine.onKeyDown(ctrlC) as DispatchDecision.Consumed).route,
        )
        assertEquals("releasing Ctrl must re-arm Ctrl+C", 2, copyRuns)

        // 2) Focused claims: overlapping Ctrl+C and Ctrl+V claims keep independent latches.
        var claimC = 0
        var claimV = 0
        val claimEngine =
            ShortcutDispatchEngine(
                { InMemoryJewelKeymap("empty") },
                { emptyList() },
                {
                    listOf(
                        EngineClaim(JewelKeySequence(ctrlC), true, false, ShortcutRepeatPolicy.OnceUntilRelease) {
                            claimC++
                        },
                        EngineClaim(JewelKeySequence(ctrlV), true, false, ShortcutRepeatPolicy.OnceUntilRelease) {
                            claimV++
                        },
                    )
                },
            )
        assertEquals(
            DispatchDecision.Consumed.Route.Claim,
            (claimEngine.onKeyDown(ctrlC) as DispatchDecision.Consumed).route,
        )
        assertEquals(
            DispatchDecision.Consumed.Route.Claim,
            (claimEngine.onKeyDown(ctrlV) as DispatchDecision.Consumed).route,
        )
        claimEngine.onKeyUp(ctrlV)
        assertEquals(
            DispatchDecision.Consumed.Route.RepeatSuppressed,
            (claimEngine.onKeyDown(ctrlC) as DispatchDecision.Consumed).route,
        )
        assertEquals(1, claimC)
        claimEngine.onKeyUp(ctrlC)
        assertEquals(
            DispatchDecision.Consumed.Route.Claim,
            (claimEngine.onKeyDown(ctrlC) as DispatchDecision.Consumed).route,
        )
        assertEquals(2, claimC)
        assertEquals(1, claimV)

        // 3) Bridge consumeIfRepeatSuppressed: overlapping Ctrl+C and Ctrl+V keep independent latches.
        cmdEngine.reset()
        assertFalse(cmdEngine.consumeIfRepeatSuppressed(copyId, ctrlC))
        assertFalse(cmdEngine.consumeIfRepeatSuppressed(pasteId, ctrlV))
        cmdEngine.onKeyUp(Key.V)
        assertTrue("Ctrl+C must stay latched after V releases", cmdEngine.consumeIfRepeatSuppressed(copyId, ctrlC))
        assertFalse("Ctrl+V must re-arm after V releases", cmdEngine.consumeIfRepeatSuppressed(pasteId, ctrlV))
        cmdEngine.onKeyUp(Key.C)
        assertFalse("Ctrl+C must re-arm after C releases", cmdEngine.consumeIfRepeatSuppressed(copyId, ctrlC))
    }

    @Test
    fun `resolveFocusedBindingRejection distinguishes disabled focused bindings from absent bindings`() {
        val disabledEngine =
            engine({ listOf(EngineBinding(selectAll, enabled = false, blocksOuterBindings = false, "t") {}) })
        assertEquals(ActionDispatchRejection.Disabled, disabledEngine.resolveFocusedBindingRejection(selectAll))
        assertEquals(ActionDispatchRejection.NoFocusedBinding, disabledEngine.resolveFocusedBindingRejection(reformat))
    }
}
