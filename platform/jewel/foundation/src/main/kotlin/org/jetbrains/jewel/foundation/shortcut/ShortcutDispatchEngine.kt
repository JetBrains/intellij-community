// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.shortcut

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode
import java.awt.event.KeyEvent as AwtKeyEvent
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

/** How keyboard auto-repeat key-downs are delivered to a completed one-stroke binding or claim. */
@ApiStatus.Experimental
@ExperimentalJewelApi
public enum class ShortcutRepeatPolicy {
    /** Invoke on the initial key-down and every delivered auto-repeat key-down. */
    RepeatWhileHeld,

    /** Invoke once, then suppress delivered repeats until the key-up of the same stroke. */
    OnceUntilRelease,
}

/** A focused command binding as seen by the engine; innermost bindings come LAST in the list. */
@ApiStatus.Experimental
@ExperimentalJewelApi
public class EngineBinding(
    /** Identifier of the bound action. */
    public val actionId: JewelActionId,
    /** Whether the binding can currently execute. */
    public val enabled: Boolean,
    /** When `true`, prevents outer bindings from running if this binding is disabled. */
    public val blocksOuterBindings: Boolean,
    /** Human-readable origin label used as fallback presentation text. */
    public val origin: String,
    /** Repeat policy for held keys on this binding. */
    public val repeatPolicy: ShortcutRepeatPolicy = ShortcutRepeatPolicy.RepeatWhileHeld,
    /** Per-place presentation overrides contributed by this binding. */
    public val presentationOverride: ActionPresentationOverride = ActionPresentationOverride.Empty,
    /** Handler invoked when the binding executes. */
    public val onInvoke: () -> Unit,
)

/** A focused sequence claim as seen by the engine; innermost claims come LAST in the list. */
@ApiStatus.Experimental
@ExperimentalJewelApi
public class EngineClaim(
    /** Key sequence claimed by the focused node. */
    public val sequence: JewelKeySequence,
    /** Whether the claim is currently enabled. */
    public val enabled: Boolean,
    /** When `true`, prevents outer claims from matching if this claim is disabled. */
    public val blocksOuterClaims: Boolean,
    /** Repeat policy for held keys on this claim. */
    public val repeatPolicy: ShortcutRepeatPolicy = ShortcutRepeatPolicy.RepeatWhileHeld,
    /** Handler invoked when the claimed sequence matches. */
    public val onInvoke: () -> Unit,
)

/** Result of evaluating a keyboard event in [ShortcutDispatchEngine]. */
@ApiStatus.Experimental
@ExperimentalJewelApi
public sealed interface DispatchDecision {
    /** The event was consumed by Jewel dispatch. */
    public data class Consumed(
        /** Action invoked by the event, or `null` for claims and intermediate chord states. */
        public val invokedActionId: JewelActionId?,
        /** Sequence that triggered dispatch, or `null` for chord cancellation or typed suppression. */
        public val invokedSequence: JewelKeySequence?,
        /** Dispatch lane or state transition that consumed the event. */
        public val route: Route,
    ) : DispatchDecision {
        /** Reason a key event was consumed by the dispatch engine. */
        public enum class Route {
            /** Consumed by a focused `Modifier.claimShortcut`. */
            Claim,

            /** Consumed by a focused `Modifier.shortcut` resolved through the keymap. */
            Keymap,

            /** Consumed as the first stroke of an incomplete two-stroke chord. */
            ChordPrefix,

            /** Consumed when a pending two-stroke chord failed to match. */
            ChordCancelled,

            /** Consumed because a trailing `KEY_TYPED` event belonged to an already-claimed press. */
            TypedSuppressed,

            /** Consumed by a `OnceUntilRelease` repeat latch while the key stayed held. */
            RepeatSuppressed,
        }
    }

    /** Not Jewel's event: fall through to ordinary input handling. */
    public data object Pass : DispatchDecision
}

/**
 * The host-neutral dispatch state machine implementing the standalone/claim contract:
 * 1. focused claims resolve before keymap lookup;
 * 2. an in-flight two-stroke sequence completes or cancels-and-consumes;
 * 3. an exact one-stroke command wins immediately over entering a chord with the same first stroke;
 * 4. a chord prefix is consumed and enters pending state;
 * 5. anything else passes through, and an unbound key is never swallowed.
 *
 * Additional contract points pinned by tests:
 * - modifier-only key-downs never participate in matching (callers pass strokes via
 *   [JewelKeyStroke.fromKeyDownOrNull]);
 * - any consumed key-down arms suppression of the trailing KEY_TYPED event, so claimed printable keys do not leak
 *   characters into focused text fields;
 * - the nearest (innermost) focused enabled binding wins; disabled bindings fall through outward unless an evaluated
 *   entry sets `blocksOuterBindings`.
 *
 * The engine is deliberately free of Compose and AWT types so it can be unit-tested directly and reused by both the
 * standalone window resolver and the IJPL bridge veto.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public class ShortcutDispatchEngine
internal constructor(
    private val keymap: () -> JewelKeymap,
    private val focusedBindings: () -> List<EngineBinding>,
    private val focusedClaims: () -> List<EngineClaim>,
    private val chordTimeoutMillis: Long,
    private val clockMillis: () -> Long,
    private val onPendingChordChanged: ((JewelKeyStroke?) -> Unit)?,
    private val isActionRegistered: (JewelActionId) -> Boolean,
) {
    /** Creates a dispatch engine with all actions considered registered. */
    public constructor(
        keymap: () -> JewelKeymap,
        focusedBindings: () -> List<EngineBinding>,
        focusedClaims: () -> List<EngineClaim>,
        chordTimeoutMillis: Long = DEFAULT_CHORD_TIMEOUT_MILLIS,
        clockMillis: () -> Long = System::currentTimeMillis,
        onPendingChordChanged: ((JewelKeyStroke?) -> Unit)? = null,
    ) : this(keymap, focusedBindings, focusedClaims, chordTimeoutMillis, clockMillis, onPendingChordChanged, { true })

    private var pendingFirstStroke: JewelKeyStroke? = null
    private var pendingFirstStrokeTimestampMillis: Long = 0L
    private var suppressNextTyped = false

    /**
     * Strokes whose OnceUntilRelease invocation already fired; each entry clears when its key or any required modifier
     * releases. A set handles overlapping holds (e.g. `Ctrl+C` down -> `Ctrl+V` down -> `V` up -> `C` auto-repeat).
     */
    private val repeatLatchedStrokes = LinkedHashSet<JewelKeyStroke>()

    /** Whether the engine is currently awaiting the second stroke of a two-stroke chord. */
    public val isAwaitingSecondStroke: Boolean
        get() = pendingFirstStroke != null

    /** Innermost focused enabled binding for [actionId], or null. Used by hosts for presentation. */
    public fun resolveFocusedBinding(actionId: JewelActionId): EngineBinding? = resolveBinding(actionId)

    /**
     * True when [onKeyDown] with [stroke] would consume it through the claim lane. This is the host-veto predicate (the
     * IJPL bridge skips `IdeKeyEventDispatcher` when it holds) and it deliberately shares [resolveClaim] with
     * [onKeyDown]: a veto that diverged from delivery could suppress a host action without Jewel invoking anything.
     */
    public fun claimsStroke(stroke: JewelKeyStroke): Boolean = resolveClaim(stroke) != null

    /** Cancels an in-flight two-stroke chord without consuming a stroke. Returns `true` if a chord was pending. */
    public fun cancelPendingChord(): Boolean {
        if (pendingFirstStroke == null) return false
        setPendingChord(null)
        return true
    }

    /**
     * Clears an expired pending two-stroke chord based on [chordTimeoutMillis] and [clockMillis]. An expired chord
     * drops silently so the next stroke evaluates fresh rather than returning
     * [DispatchDecision.Consumed.Route.ChordCancelled].
     */
    public fun expirePendingChordIfTimedOut(): Boolean {
        if (pendingFirstStroke == null) return false
        if (chordTimeoutMillis <= 0L) return false
        if (clockMillis() - pendingFirstStrokeTimestampMillis < chordTimeoutMillis) return false
        setPendingChord(null)
        return true
    }

    private fun setPendingChord(stroke: JewelKeyStroke?) {
        val changed = pendingFirstStroke != stroke
        pendingFirstStroke = stroke
        pendingFirstStrokeTimestampMillis = if (stroke != null) clockMillis() else 0L
        if (changed) onPendingChordChanged?.invoke(stroke)
    }

    /** Call for every key-up so OnceUntilRelease latches clear when the latched key or required modifier releases. */
    public fun onKeyUp(stroke: JewelKeyStroke?) {
        if (stroke != null) onKeyUp(stroke.key)
    }

    /**
     * Clears any active OnceUntilRelease latches whose key or required modifier matches [key] — handling modifier-first
     * release (`Ctrl+S` -> `Ctrl` up -> `S` up), overlapping key holds, and macOS Command-held release where only the
     * `Meta` key-up is delivered.
     */
    public fun onKeyUp(key: Key) {
        if (repeatLatchedStrokes.isEmpty()) return
        val canonicalReleased = key.canonicalShortcutKey()
        val releasedCode = key.nativeKeyCode
        repeatLatchedStrokes.removeAll { latched ->
            latched.key.canonicalShortcutKey() == canonicalReleased ||
                when (releasedCode) {
                    AwtKeyEvent.VK_CONTROL -> latched.ctrl
                    AwtKeyEvent.VK_SHIFT -> latched.shift
                    AwtKeyEvent.VK_ALT,
                    AwtKeyEvent.VK_ALT_GRAPH -> latched.alt
                    AwtKeyEvent.VK_META,
                    AwtKeyEvent.VK_WINDOWS -> latched.meta
                    else -> false
                }
        }
    }

    /**
     * Applies `OnceUntilRelease` repeat suppression for a host-dispatched command (used by the IJPL bridge, where
     * `IdeKeyEventDispatcher` invokes `AnAction.actionPerformed` on every auto-repeat `KEY_PRESSED`). Returns `true`
     * when the press is an auto-repeat of an already-latched stroke and must be skipped.
     */
    internal fun consumeIfRepeatSuppressed(actionId: JewelActionId, stroke: JewelKeyStroke?): Boolean {
        val binding = resolveBinding(actionId) ?: return false
        if (binding.repeatPolicy != ShortcutRepeatPolicy.OnceUntilRelease || stroke == null) return false
        if (stroke in repeatLatchedStrokes) return true
        repeatLatchedStrokes.add(stroke)
        return false
    }

    /**
     * Index of the winning enabled binding for [actionId] in [focusedBindings] (higher is deeper/innermost), or `-1`
     * when no focused enabled binding resolves for [actionId].
     */
    internal fun resolveFocusedBindingIndex(actionId: JewelActionId): Int {
        val bindings = focusedBindings()
        for (index in bindings.lastIndex downTo 0) {
            val binding = bindings[index]
            if (binding.actionId != actionId) continue
            if (binding.enabled) return index
            if (binding.blocksOuterBindings) return -1
        }
        return -1
    }

    /**
     * Distinguishes whether an unresolved [actionId] has a focused binding that is disabled/blocked
     * ([ActionDispatchRejection.Disabled]) versus no focused binding at all
     * ([ActionDispatchRejection.NoFocusedBinding]).
     */
    internal fun resolveFocusedBindingRejection(actionId: JewelActionId): ActionDispatchRejection =
        if (focusedBindings().any { it.actionId == actionId }) {
            ActionDispatchRejection.Disabled
        } else {
            ActionDispatchRejection.NoFocusedBinding
        }

    /**
     * Arms one-shot suppression of the trailing KEY_TYPED for a key-down a caller consumed outside the engine
     * (menu-scope shortcuts); equivalent to what any engine-consumed key-down does itself.
     */
    public fun armTypedSuppression() {
        suppressNextTyped = true
    }

    /** Call for KEY_TYPED-equivalent events; returns true when the event must be consumed. */
    public fun onTypedEvent(): Boolean {
        if (suppressNextTyped) {
            suppressNextTyped = false
            return true
        }
        return false
    }

    /** Clears state that must not survive focus changes or host disposal. */
    public fun reset() {
        setPendingChord(null)
        suppressNextTyped = false
        // The OnceUntilRelease latches too: if focus left mid-hold, the matching key-up may never reach this host,
        // and a latch that survives the round-trip would swallow the next genuine press of the same stroke.
        repeatLatchedStrokes.clear()
    }

    /**
     * Call for every key-down. [stroke] must be null for modifier-only key-downs, which never match and never cancel a
     * pending chord.
     */
    public fun onKeyDown(stroke: JewelKeyStroke?): DispatchDecision {
        val claimDecision = onClaimKeyDown(stroke)
        if (claimDecision !is DispatchDecision.Pass) return claimDecision
        return onKeymapKeyDown(stroke)
    }

    /** Evaluates focused sequence claims (`Modifier.claimShortcut`) ahead of raw claims and host keymap commands. */
    internal fun onClaimKeyDown(stroke: JewelKeyStroke?): DispatchDecision {
        expirePendingChordIfTimedOut()
        if (stroke == null) return DispatchDecision.Pass

        // A fresh key-down means the previously consumed press produced no typed event after all.
        suppressNextTyped = false

        resolveClaim(stroke)?.let { claim ->
            suppressNextTyped = true
            setPendingChord(null)
            if (claim.repeatPolicy == ShortcutRepeatPolicy.OnceUntilRelease) {
                if (stroke in repeatLatchedStrokes) {
                    return DispatchDecision.Consumed(
                        null,
                        claim.sequence,
                        DispatchDecision.Consumed.Route.RepeatSuppressed,
                    )
                }
                repeatLatchedStrokes.add(stroke)
            }
            claim.onInvoke()
            return DispatchDecision.Consumed(
                invokedActionId = null,
                invokedSequence = claim.sequence,
                route = DispatchDecision.Consumed.Route.Claim,
            )
        }
        return DispatchDecision.Pass
    }

    /**
     * Records consumption by a focused raw key claim (`Modifier.claimKeyEvent`): cancels any chord and suppresses
     * typed.
     */
    internal fun onRawClaimConsumed() {
        setPendingChord(null)
        suppressNextTyped = true
    }

    /** Clears any stale `KEY_TYPED` suppression latch without disturbing a pending chord or repeat latch. */
    internal fun clearTypedSuppression() {
        suppressNextTyped = false
    }

    /** Evaluates pending chords, exact one-stroke keymap commands, and chord prefixes. */
    internal fun onKeymapKeyDown(stroke: JewelKeyStroke?): DispatchDecision {
        expirePendingChordIfTimedOut()
        if (stroke == null) return DispatchDecision.Pass

        // Pending two-stroke state: complete or cancel; either way the stroke is consumed.
        pendingFirstStroke?.let { first ->
            setPendingChord(null)
            suppressNextTyped = true
            val sequence = JewelKeySequence(first, stroke)
            val candidateIds = keymap().actionIdsFor(sequence).filterTo(HashSet(), isActionRegistered)
            val binding = resolveInnermostBinding(candidateIds)
            if (binding != null) {
                binding.onInvoke()
                return DispatchDecision.Consumed(binding.actionId, sequence, DispatchDecision.Consumed.Route.Keymap)
            }
            return DispatchDecision.Consumed(null, null, DispatchDecision.Consumed.Route.ChordCancelled)
        }

        // Exact one-stroke command wins over a chord sharing the first stroke.
        val exactSequence = JewelKeySequence(stroke)
        val exactIds = keymap().actionIdsFor(exactSequence).filterTo(HashSet(), isActionRegistered)
        val exactBinding = resolveInnermostBinding(exactIds)
        if (exactBinding != null) {
            suppressNextTyped = true
            if (exactBinding.repeatPolicy == ShortcutRepeatPolicy.OnceUntilRelease) {
                if (stroke in repeatLatchedStrokes) {
                    return DispatchDecision.Consumed(
                        exactBinding.actionId,
                        exactSequence,
                        DispatchDecision.Consumed.Route.RepeatSuppressed,
                    )
                }
                repeatLatchedStrokes.add(stroke)
            }
            exactBinding.onInvoke()
            return DispatchDecision.Consumed(
                exactBinding.actionId,
                exactSequence,
                DispatchDecision.Consumed.Route.Keymap,
            )
        }

        // Chord prefix with at least one focused enabled target: consume and wait.
        val prefixTargets = keymap().actionIdsForPrefix(stroke)
        if (prefixTargets.any { id -> isActionRegistered(id) && resolveBinding(id) != null }) {
            setPendingChord(stroke)
            suppressNextTyped = true
            return DispatchDecision.Consumed(null, null, DispatchDecision.Consumed.Route.ChordPrefix)
        }

        // Unbound input stays ordinary input.
        return DispatchDecision.Pass
    }

    /**
     * Innermost focused enabled binding across all [candidateIds], walking focused bindings from inside out. Tracks
     * `blocksOuterBindings` per action ID so a disabled inner blocker for one action does not block a different outer
     * action sharing the same shortcut.
     */
    private fun resolveInnermostBinding(candidateIds: Set<JewelActionId>): EngineBinding? {
        if (candidateIds.isEmpty()) return null
        val blockedActionIds = HashSet<JewelActionId>()
        for (binding in focusedBindings().asReversed()) {
            if (binding.actionId !in candidateIds || binding.actionId in blockedActionIds) continue
            if (binding.enabled) return binding
            if (binding.blocksOuterBindings) blockedActionIds.add(binding.actionId)
        }
        return null
    }

    /** Innermost focused enabled binding; disabled entries fall through outward unless they block. */
    private fun resolveBinding(actionId: JewelActionId): EngineBinding? = resolveBindingIn(focusedBindings(), actionId)

    private fun resolveClaim(stroke: JewelKeyStroke): EngineClaim? {
        for (claim in focusedClaims().asReversed()) {
            val matchesFirst = claim.sequence.first == stroke && claim.sequence.second == null
            if (matchesFirst && claim.enabled) return claim
            if (claim.sequence.first == stroke && !claim.enabled && claim.blocksOuterClaims) return null
        }
        return null
    }

    /** Timing defaults for [ShortcutDispatchEngine]. */
    public companion object {
        /**
         * Default timeout before an uncompleted two-stroke chord drops silently, matching IJPL's
         * secondKeystrokeTimeout.
         */
        public const val DEFAULT_CHORD_TIMEOUT_MILLIS: Long = 2_000L
    }
}

/** Innermost enabled binding for [actionId] inside [bindings]; disabled entries fall through unless blocking. */
internal fun resolveBindingIn(bindings: List<EngineBinding>, actionId: JewelActionId): EngineBinding? {
    for (binding in bindings.asReversed()) {
        if (binding.actionId != actionId) continue
        if (binding.enabled) return binding
        if (binding.blocksOuterBindings) return null
    }
    return null
}
