// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.shortcut

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.invalidateSemantics
import androidx.compose.ui.node.traverseAncestors
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.InternalJewelApi

/**
 * Binds a previously registered action ID to this UI node. The binding participates in dispatch only while this node's
 * subtree has focus; the nearest focused enabled binding for an action wins, disabled bindings fall through outward,
 * and `blocksOuterBindings = true` stops that fall-through.
 *
 * Like `Modifier.provideData`, the node observes focus of nodes attached AFTER it in the modifier chain (and of
 * descendants), so it must come before `focusable()` in the chain.
 *
 * **Threading:** [onInvoke] is called synchronously on the surface's UI thread (the AWT event dispatch thread in
 * production) while a key event is being processed. Keep it fast and non-blocking — launch a coroutine for real work; a
 * slow handler delays every subsequent keystroke.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Modifier.shortcut(
    action: JewelAction,
    enabled: Boolean = true,
    blocksOuterBindings: Boolean = false,
    repeatPolicy: ShortcutRepeatPolicy = ShortcutRepeatPolicy.RepeatWhileHeld,
    presentation: ActionPresentationOverride = ActionPresentationOverride.Empty,
    onInvoke: () -> Unit,
): Modifier =
    this then ShortcutBindingElement(action, enabled, blocksOuterBindings, repeatPolicy, presentation, onInvoke)

/**
 * Claims a one-stroke physical shortcut before host keymap lookup while this node's subtree has focus. The deliberate,
 * review-visible escape hatch for editor-like components; in the IJPL bridge it also makes the host skip
 * `IdeKeyEventDispatcher` for the claimed stroke.
 *
 * **Threading:** [onInvoke] is called synchronously on the surface's UI thread (the AWT event dispatch thread in
 * production) while a key event is being processed. Keep it fast and non-blocking.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Modifier.claimShortcut(
    sequence: JewelKeySequence,
    enabled: Boolean = true,
    blocksOuterClaims: Boolean = false,
    repeatPolicy: ShortcutRepeatPolicy = ShortcutRepeatPolicy.RepeatWhileHeld,
    onInvoke: () -> Unit,
): Modifier {
    // Two-stroke claims are not part of this contract: claims resolve (and host vetoes evaluate) on a single
    // key-down, so a chord claim could never be invoked yet would shadow the stroke. Fail fast instead.
    require(sequence.second == null) {
        "claimShortcut only supports one-stroke sequences, got '${sequence.displayText()}'. " +
            "Bind a command through the keymap for two-stroke chords."
    }
    return this then ShortcutClaimElement(sequence, enabled, blocksOuterClaims, repeatPolicy, onInvoke)
}

/**
 * Low-level single-event ownership for focused input that is not a shortcut sequence. A matching enabled claim owns and
 * consumes the event; it never falls through to the host keymap.
 *
 * **Threading:** [matcher] and [onKeyEvent] are called synchronously on the surface's UI thread (the AWT event dispatch
 * thread in production) while a key event is being processed. Keep them fast and non-blocking.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Modifier.claimKeyEvent(
    matcher: (KeyEvent) -> Boolean,
    enabled: Boolean = true,
    onKeyEvent: (KeyEvent) -> Unit,
): Modifier = this then RawKeyClaimElement(matcher, enabled, onKeyEvent)

/**
 * Shared focus-registration lifecycle for the shortcut participant nodes. On the focus-gain transition a node finds its
 * nearest [ShortcutResolverRootNode] ancestor (which binds it to the correct dispatch scope, including nested hosts)
 * and registers with it; on focus-loss or detach it deregisters. Dispatch then reads the root's registered set instead
 * of traversing the subtree on every keystroke.
 */
@InternalJewelApi
@ApiStatus.Internal
public abstract class ShortcutRegistrarNode : Modifier.Node(), FocusEventModifierNode, TraversableNode {
    public var hasFocus: Boolean = false
        private set

    private var registeredRoot: ShortcutResolverRootNode? = null

    override fun onFocusEvent(focusState: FocusState) {
        val nowFocused = focusState.hasFocus
        if (nowFocused == hasFocus) return
        hasFocus = nowFocused
        if (nowFocused) {
            var root: ShortcutResolverRootNode? = null
            traverseAncestors(ShortcutResolverRootNode.TraverseKey) { node ->
                root = node as? ShortcutResolverRootNode
                false
            }
            registeredRoot = root?.also { it.register(this) }
        } else {
            registeredRoot?.deregister(this)
            registeredRoot = null
        }
    }

    override fun onDetach() {
        registeredRoot?.deregister(this)
        registeredRoot = null
        hasFocus = false
    }

    /**
     * Re-samples presentations for the surface this node is registered with. Call when a parameter that feeds
     * presentation changes (enablement, the override) without the focused set itself changing: sampling is cached and
     * demand-driven, so nothing else would notice.
     */
    protected fun invalidatePresentations() {
        registeredRoot?.state?.presentations?.invalidate()
    }

    /**
     * The number of same-key traversable ancestors, used to order registered nodes outermost-first (the engine treats
     * the last entries as innermost). All simultaneously focused nodes lie on one focus chain, so ancestor counts give
     * a total order; ancestors above the dispatch root add the same constant to every node under it and cannot change
     * relative order.
     */
    internal fun nestingDepth(): Int {
        var depth = 0
        traverseAncestors(traverseKey) {
            depth++
            true
        }
        return depth
    }
}

@InternalJewelApi
@ApiStatus.Internal
public class ShortcutBindingNode(
    @Volatile public var action: JewelAction,
    @Volatile public var enabled: Boolean,
    @Volatile public var blocksOuterBindings: Boolean,
    @Volatile public var repeatPolicy: ShortcutRepeatPolicy,
    @Volatile public var presentationOverride: ActionPresentationOverride,
    @Volatile public var onInvoke: () -> Unit,
) : ShortcutRegistrarNode(), SemanticsModifierNode {
    override fun SemanticsPropertyReceiver.applySemantics() {
        jewelShortcutActions(listOf(action.id.value))
    }

    internal fun onActionChanged() {
        invalidateSemantics()
    }

    internal fun onBindingChanged() {
        invalidatePresentations()
    }

    override val traverseKey: TraverseKey = TraverseKey

    public companion object TraverseKey
}

private class ShortcutBindingElement(
    private val action: JewelAction,
    private val enabled: Boolean,
    private val blocksOuterBindings: Boolean,
    private val repeatPolicy: ShortcutRepeatPolicy,
    private val presentationOverride: ActionPresentationOverride,
    private val onInvoke: () -> Unit,
) : ModifierNodeElement<ShortcutBindingNode>() {
    override fun create() =
        ShortcutBindingNode(action, enabled, blocksOuterBindings, repeatPolicy, presentationOverride, onInvoke)

    override fun update(node: ShortcutBindingNode) {
        val actionChanged = node.action != action
        node.action = action
        node.enabled = enabled
        node.blocksOuterBindings = blocksOuterBindings
        node.repeatPolicy = repeatPolicy
        node.presentationOverride = presentationOverride
        node.onInvoke = onInvoke
        if (actionChanged) node.onActionChanged()
        // enabled / blocksOuterBindings / presentation feed the sampled presentation of every control bound to
        // this action, and none of them change the focused set — invalidate explicitly.
        node.onBindingChanged()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "shortcut"
        properties["action"] = action.id.value
        properties["enabled"] = enabled
        properties["repeatPolicy"] = repeatPolicy
    }

    override fun equals(other: Any?): Boolean =
        other is ShortcutBindingElement &&
            other.action == action &&
            other.enabled == enabled &&
            other.blocksOuterBindings == blocksOuterBindings &&
            other.repeatPolicy == repeatPolicy &&
            other.presentationOverride == presentationOverride &&
            other.onInvoke === onInvoke

    override fun hashCode(): Int {
        var result = action.hashCode()
        result = 31 * result + enabled.hashCode()
        result = 31 * result + blocksOuterBindings.hashCode()
        result = 31 * result + repeatPolicy.hashCode()
        result = 31 * result + presentationOverride.hashCode()
        return result
    }
}

@InternalJewelApi
@ApiStatus.Internal
public class ShortcutClaimNode(
    public var sequence: JewelKeySequence,
    public var enabled: Boolean,
    public var blocksOuterClaims: Boolean,
    public var repeatPolicy: ShortcutRepeatPolicy,
    public var onInvoke: () -> Unit,
) : ShortcutRegistrarNode(), SemanticsModifierNode {
    override fun SemanticsPropertyReceiver.applySemantics() {
        jewelClaimedShortcuts(listOf(sequence.displayText()))
    }

    internal fun onSequenceChanged() {
        invalidateSemantics()
    }

    override val traverseKey: TraverseKey = TraverseKey

    public companion object TraverseKey
}

private class ShortcutClaimElement(
    private val sequence: JewelKeySequence,
    private val enabled: Boolean,
    private val blocksOuterClaims: Boolean,
    private val repeatPolicy: ShortcutRepeatPolicy,
    private val onInvoke: () -> Unit,
) : ModifierNodeElement<ShortcutClaimNode>() {
    override fun create() = ShortcutClaimNode(sequence, enabled, blocksOuterClaims, repeatPolicy, onInvoke)

    override fun update(node: ShortcutClaimNode) {
        val sequenceChanged = node.sequence != sequence
        node.sequence = sequence
        node.enabled = enabled
        node.blocksOuterClaims = blocksOuterClaims
        node.repeatPolicy = repeatPolicy
        node.onInvoke = onInvoke
        if (sequenceChanged) node.onSequenceChanged()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "claimShortcut"
        properties["sequence"] = sequence.displayText()
        properties["enabled"] = enabled
        properties["repeatPolicy"] = repeatPolicy
    }

    override fun equals(other: Any?): Boolean =
        other is ShortcutClaimElement &&
            other.sequence == sequence &&
            other.enabled == enabled &&
            other.blocksOuterClaims == blocksOuterClaims &&
            other.repeatPolicy == repeatPolicy &&
            other.onInvoke === onInvoke

    override fun hashCode(): Int {
        var result = sequence.hashCode()
        result = 31 * result + enabled.hashCode()
        result = 31 * result + blocksOuterClaims.hashCode()
        result = 31 * result + repeatPolicy.hashCode()
        return result
    }
}

@InternalJewelApi
@ApiStatus.Internal
public class RawKeyClaimNode(
    public var matcher: (KeyEvent) -> Boolean,
    public var enabled: Boolean,
    public var onKeyEvent: (KeyEvent) -> Unit,
) : ShortcutRegistrarNode() {
    override val traverseKey: TraverseKey = TraverseKey

    public companion object TraverseKey
}

private class RawKeyClaimElement(
    private val matcher: (KeyEvent) -> Boolean,
    private val enabled: Boolean,
    private val onKeyEvent: (KeyEvent) -> Unit,
) : ModifierNodeElement<RawKeyClaimNode>() {
    override fun create() = RawKeyClaimNode(matcher, enabled, onKeyEvent)

    override fun update(node: RawKeyClaimNode) {
        node.matcher = matcher
        node.enabled = enabled
        node.onKeyEvent = onKeyEvent
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "claimKeyEvent"
        properties["enabled"] = enabled
    }

    override fun equals(other: Any?): Boolean =
        other is RawKeyClaimElement &&
            other.matcher === matcher &&
            other.enabled == enabled &&
            other.onKeyEvent === onKeyEvent

    override fun hashCode(): Int = 31 * matcher.hashCode() + enabled.hashCode()
}
