// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.rpc

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.concurrency.annotations.RequiresEdt
import fleet.openmap.SerializedValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus
import java.awt.KeyboardFocusManager
import java.awt.Window
import javax.swing.JComponent

/**
 * Prepares the transmission of the graphical representation of the component over the network to the frontend with input events
 * transferred in the opposite direction. The generated id object can be passed over RPC, and used with
 * [ComponentDirectTransferId.getComponent] method on the frontend to realize the 'mirroring' component. The binding is only valid during
 * the lifetime of the passed `disposable` parameter. During this time the source component is extracted from the original component
 * hierarchy, and it shouldn't be put to any other hierarchy, or the binding will be broken. In particular, calling this method while the
 * previous binding is still in effect isn't allowed.
 *
 * When used in monolith mode, the component returned by [ComponentDirectTransferId.getComponent] is just the original source component,
 * so no bridging of painting and input events is performed.
 *
 * The `owner` is the backend window that owns the transferred component. Pass the frame of the target project when the component is not
 * in a component hierarchy yet. Without it, the first visible frame is used, and the frame can belong to a different project. When that
 * project closes, the frame disposes the transferred component, and the frontend shows an empty panel for the still opened project.
 *
 * IMPORTANT. The provided `disposable` should be disposed on EDT.
 */
@ApiStatus.Internal
@RequiresEdt
fun JComponent.setupTransfer(disposable: Disposable, owner: Window? = null): ComponentDirectTransferId {
  val localValue = ComponentWithDisposable(this, disposable, owner)
  return ComponentDirectTransferId(serializeToRpc(localValue), localValue)
}

/**
 * Creates a 'mirror' component on the frontend, which represents a component created on the backend side.
 *
 * In monolith mode this just returns the original source component.
 *
 * @see JComponent.setupTransfer
 * @see getFocusTargetForTransferredComponent
 */
@ApiStatus.Internal
@RequiresEdt
fun ComponentDirectTransferId.getComponent(): JComponent? {
  return localValue?.let {
    Wrapper().apply {
      if (Disposer.tryRegister(it.disposable) { setContent(null) }) {
        setContent(it.component)
      }
    }
  } ?: deserializeFromRpc(serializedValue)
}

/**
 * Can be used to request focus into the component returned by [ComponentDirectTransferId.getComponent]
 * (working consistently both on the frontend side and in monolith).
 * Will return the 'default' component as per the IDE default focus traversal policy.
 */
@ApiStatus.Internal
@RequiresEdt
fun getFocusTargetForTransferredComponent(component: JComponent): JComponent {
  val focusTraversalPolicy = KeyboardFocusManager.getCurrentKeyboardFocusManager().defaultFocusTraversalPolicy
  return focusTraversalPolicy.getDefaultComponent(component) as? JComponent ?: component
}

/**
 * An id of the component that is created on the backend but needs to be displayed on the frontend side.
 *
 * @see JComponent.setupTransfer
 * @see ComponentDirectTransferId.getComponent
 */
@ApiStatus.Internal
@Serializable
data class ComponentDirectTransferId(val serializedValue: SerializedValue?, @Transient val localValue: ComponentWithDisposable? = null)

@ApiStatus.Internal
class ComponentWithDisposable(val component: JComponent, val disposable: Disposable, val owner: Window? = null)
