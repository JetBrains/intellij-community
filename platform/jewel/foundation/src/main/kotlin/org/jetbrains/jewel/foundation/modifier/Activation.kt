package org.jetbrains.jewel.foundation.modifier

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.modifier.ModifierLocalModifierNode
import androidx.compose.ui.modifier.ProvidableModifierLocal
import androidx.compose.ui.modifier.modifierLocalMapOf
import androidx.compose.ui.modifier.modifierLocalOf
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.platform.InspectorInfo
import java.awt.Component
import java.awt.Window
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

/**
 * Tracks the activation state of the provided AWT [Window].
 *
 * This modifier listens to the window's "activated" and "deactivated" events. When the window is active, it provides
 * `true` to the [ModifierLocalActivated] local, allowing child modifiers (like [onActivated]) to react to the window's
 * state.
 *
 * @param window The AWT Window to observe.
 */
@Stable
public fun Modifier.trackWindowActivation(window: Window): Modifier = this then TrackWindowActivationModifier(window)

/**
 * Tracks the focus/activation state of a native AWT [Component].
 *
 * It listens to the component's focus events and provides the activation state to the Compose hierarchy.
 *
 * @param awtParent The parent AWT Component to observe for focus events.
 */
@Stable
public fun Modifier.trackComponentActivation(awtParent: Component): Modifier =
    this then TrackComponentActivationModifier(awtParent)

/**
 * Tracks activation based on the focus state of this modifier's children.
 *
 * This modifier applies a [focusGroup] to its content. It considers itself "activated" if the parent is activated AND
 * any child within this focus group currently holds focus.
 */
@Stable public fun Modifier.trackActivation(): Modifier = this.focusGroup().then(TrackActivationModifier)

/**
 * A callback modifier that triggers whenever the activation state changes.
 *
 * This modifier consumes the value provided by [ModifierLocalActivated] (set by [trackWindowActivation],
 * [trackActivation] or [trackWindowActivation]). When that value changes, the [onChanged] lambda is invoked.
 *
 * @param enabled Whether this callback is active. If `false`, the modifier is effectively a no-op.
 * @param onChanged A lambda called with the new activation state (`true` for active, `false` for inactive).
 */
public fun Modifier.onActivated(enabled: Boolean = true, onChanged: (Boolean) -> Unit): Modifier =
    if (enabled) {
        this then ActivateChangedModifier(onChanged)
    } else {
        this
    }

@Immutable
private data class TrackWindowActivationModifier(val window: Window) :
    ModifierNodeElement<TrackWindowActivationNode>() {
    override fun create() = TrackWindowActivationNode(window)

    override fun update(node: TrackWindowActivationNode) {
        node.update(window)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "trackWindowActivation"
        properties["window"] = window
    }
}

private class TrackWindowActivationNode(var window: Window) : Modifier.Node(), ModifierLocalModifierNode {
    override val providedValues = modifierLocalMapOf(ModifierLocalActivated to false)

    private val listener =
        object : WindowAdapter() {
            override fun windowActivated(e: WindowEvent?) {
                provide(ModifierLocalActivated, true)
            }

            override fun windowDeactivated(e: WindowEvent?) {
                provide(ModifierLocalActivated, false)
            }
        }

    override fun onAttach() {
        super.onAttach()
        window.addWindowListener(listener)
        provide(ModifierLocalActivated, window.isActive)
    }

    override fun onDetach() {
        super.onDetach()
        window.removeWindowListener(listener)
    }

    fun update(newWindow: Window) {
        if (window != newWindow) {
            window.removeWindowListener(listener)
            window = newWindow
            window.addWindowListener(listener)
            provide(ModifierLocalActivated, window.isActive)
        }
    }
}

@Immutable
private data object TrackActivationModifier : ModifierNodeElement<TrackActivationNode>() {
    override fun create() = TrackActivationNode()

    override fun update(node: TrackActivationNode) {
        // no-op
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "trackActivation"
    }
}

private class TrackActivationNode :
    Modifier.Node(), FocusEventModifierNode, ModifierLocalModifierNode, ObserverModifierNode {
    override val providedValues = modifierLocalMapOf(ModifierLocalActivated to false)

    private var parentActivated = false
    private var isFocused = false

    override fun onAttach() {
        super.onAttach()
        observeReads { fetchParentActivation() }
    }

    override fun onObservedReadsChanged() {
        observeReads { fetchParentActivation() }
    }

    override fun onFocusEvent(focusState: FocusState) {
        if (isFocused != focusState.isFocused) {
            isFocused = focusState.isFocused
            updateProvidedValue()
        }
    }

    private fun fetchParentActivation() {
        parentActivated = ModifierLocalActivated.current
        updateProvidedValue()
    }

    private fun updateProvidedValue() {
        provide(ModifierLocalActivated, parentActivated && isFocused)
    }
}

@Immutable
private data class TrackComponentActivationModifier(val awtParent: Component) :
    ModifierNodeElement<TrackComponentActivationNode>() {
    override fun create() = TrackComponentActivationNode(awtParent)

    override fun update(node: TrackComponentActivationNode) {
        node.update(awtParent)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "trackComponentActivation"
        properties["awtParent"] = awtParent
    }
}

private class TrackComponentActivationNode(var awtParent: Component) : Modifier.Node(), ModifierLocalModifierNode {
    override val providedValues = modifierLocalMapOf(ModifierLocalActivated to false)

    val listener =
        object : FocusListener {
            override fun focusGained(e: FocusEvent?) {
                provide(ModifierLocalActivated, true)
            }

            override fun focusLost(e: FocusEvent?) {
                provide(ModifierLocalActivated, false)
            }
        }

    override fun onAttach() {
        super.onAttach()
        awtParent.addFocusListener(listener)
        provide(ModifierLocalActivated, awtParent.hasFocus())
    }

    override fun onDetach() {
        super.onDetach()
        awtParent.removeFocusListener(listener)
    }

    fun update(newAwtParent: Component) {
        if (awtParent != newAwtParent) {
            awtParent.removeFocusListener(listener)
            awtParent = newAwtParent
            awtParent.addFocusListener(listener)
            provide(ModifierLocalActivated, awtParent.hasFocus())
        }
    }
}

@Immutable
private data class ActivateChangedModifier(val onChanged: (Boolean) -> Unit) :
    ModifierNodeElement<ActivateChangedNode>() {
    override fun create() = ActivateChangedNode(onChanged)

    override fun update(node: ActivateChangedNode) {
        node.onChanged = onChanged
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "onActivated"
        properties["onChanged"] = onChanged
    }
}

private class ActivateChangedNode(var onChanged: (Boolean) -> Unit) :
    Modifier.Node(), ModifierLocalModifierNode, ObserverModifierNode {
    private var currentActivated = false

    override fun onAttach() {
        super.onAttach()
        fetchActivatedValue()
    }

    override fun onObservedReadsChanged() {
        fetchActivatedValue()
    }

    private fun fetchActivatedValue() {
        observeReads {
            val activated = ModifierLocalActivated.current
            if (activated != currentActivated) {
                currentActivated = activated
                onChanged(activated)
            }
        }
    }
}

public val ModifierLocalActivated: ProvidableModifierLocal<Boolean> = modifierLocalOf { false }
