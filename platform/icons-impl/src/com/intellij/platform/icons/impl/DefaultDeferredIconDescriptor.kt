// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl

import com.intellij.platform.icons.DeferredIconDescriptor
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.IconIdentifier
import java.lang.ref.WeakReference
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.jetbrains.annotations.ApiStatus

@Serializable
open class DefaultDeferredIconDescriptor(
    override val id: IconIdentifier,
    /**
     * Placeholder that is renderer when the icon is not resolved yet. Keep in mind that implementation might change
     * this to null when the icon is resolved to reduce memory footprint when serialized.
     */
    override var placeholder: IconDescriptor?,
) : DeferredIconDescriptor {
    @Transient private val listeners = mutableListOf<WeakReference<DeferredIconEventHandler>>()

    @ApiStatus.Internal
    fun addDoneListener(listener: DeferredIconEventHandler) {
        listeners.add(WeakReference(listener))
    }

    @ApiStatus.Internal
    fun markDone(resolvedIconDescriptor: IconDescriptor) {
        this.placeholder = null
        for (listener in listeners) {
            listener.get()?.whenDone(this, resolvedIconDescriptor)
        }
        listeners.clear()
    }
}

class DefaultDeferredIconSerializer(private val manager: DefaultIconManager) : KSerializer<DefaultDeferredIconDescriptor> {
    private val delegate = DefaultDeferredIconDescriptor.serializer()

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: DefaultDeferredIconDescriptor) {
        delegate.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): DefaultDeferredIconDescriptor {
        val result = delegate.deserialize(decoder)
        return manager.registerDeserializedDeferredIcon(result)
    }
}

interface DeferredIconEventHandler {
    fun whenDone(deferredIcon: DeferredIconDescriptor, resolvedIconDescriptor: IconDescriptor)
}

/** Responsible for resolving deferred icons, and also synchronization between instances and backend/frontend. */
interface DeferredIconResolver {
    val id: IconIdentifier
    val deferredIcon: WeakReference<DefaultDeferredIconDescriptor>

    suspend fun resolve(): IconDescriptor
}
