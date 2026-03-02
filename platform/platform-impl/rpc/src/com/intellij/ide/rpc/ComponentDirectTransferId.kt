// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.rpc

import com.intellij.openapi.Disposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import fleet.util.openmap.SerializedValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus
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
 * IMPORTANT. The provided `disposable` should be disposed on EDT.
 */
@ApiStatus.Internal
@RequiresEdt
fun JComponent.setupTransfer(disposable: Disposable): ComponentDirectTransferId {
  val serializedValue = serializeToRpc(ComponentWithDisposable(this, disposable))
  return ComponentDirectTransferId(serializedValue, this)
}

/**
 * Creates a 'mirror' component on the frontend, which represents a component created on the backend side.
 *
 * In monolith mode this just returns the original source component.
 *
 * @see JComponent.setupTransfer
 */
@ApiStatus.Internal
@RequiresEdt
fun ComponentDirectTransferId.getComponent(): JComponent? {
  return localComponent ?: deserializeFromRpc(serializedValue)
}

/**
 * Id of the component that is created on the backend, but needs to be displayed on the frontend side.
 *
 * @see JComponent.setupTransfer
 * @see ComponentDirectTransferId.getComponent
 */
@ApiStatus.Internal
@Serializable
data class ComponentDirectTransferId(val serializedValue: SerializedValue?, @Transient val localComponent: JComponent? = null)

@ApiStatus.Internal
class ComponentWithDisposable(val component: JComponent, val disposable: Disposable)