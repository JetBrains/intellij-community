// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.icons.DeferredIcon
import org.jetbrains.icons.Icon
import org.jetbrains.icons.IconIdentifier
import java.lang.ref.WeakReference

@Serializable
open class DefaultDeferredIcon(
  override val id: IconIdentifier,
  /**
   * Placeholder that is renderer when the icon is not resolved yet.
   * Keep in mind that implementation might change this to null when
   * the icon is resolved to reduce memory footprint when serialized.
   */
  override var placeholder: Icon?
): DeferredIcon {
  @Transient
  private val listeners = mutableListOf<WeakReference<DeferredIconEventHandler>>()

  @ApiStatus.Internal
  fun addDoneListener(listener: DeferredIconEventHandler) {
    listeners.add(WeakReference(listener))
  }

  @ApiStatus.Internal
  fun markDone(resolvedIcon: Icon) {
    this.placeholder = null
    for (listener in listeners) {
      listener.get()?.whenDone(this, resolvedIcon)
    }
    listeners.clear()
  }

}

class DefaultDeferredIconSerializer(
  private val manager: DefaultIconManager
) : KSerializer<DefaultDeferredIcon> {
  private val delegate = DefaultDeferredIcon.serializer()

  override val descriptor: SerialDescriptor = delegate.descriptor

  override fun serialize(encoder: Encoder, value: DefaultDeferredIcon) {
    delegate.serialize(encoder, value)
  }

  override fun deserialize(decoder: Decoder): DefaultDeferredIcon {
    val result = delegate.deserialize(decoder)
    return manager.registerDeserializedDeferredIcon(result)
  }
}

interface DeferredIconEventHandler {
  fun whenDone(deferredIcon: DeferredIcon, resolvedIcon: Icon)
}

/**
 * Responsible for resolving deferred icons,
 * and also synchronization between instances and backend/frontend.
 */
interface DeferredIconResolver {
  val id: IconIdentifier
  val deferredIcon: WeakReference<DefaultDeferredIcon>
  suspend fun resolve(): Icon
}