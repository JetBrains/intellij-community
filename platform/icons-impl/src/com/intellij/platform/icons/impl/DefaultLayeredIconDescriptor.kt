// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl

import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.layers.IconLayer
import kotlinx.serialization.Serializable

@Serializable
class DefaultLayeredIconDescriptor(val layers: List<IconLayer>) : IconDescriptor {
    override fun toString(): String = "DefaultIcon(layers=$layers)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DefaultLayeredIconDescriptor

        return layers == other.layers
    }

    override fun hashCode(): Int = layers.hashCode()
}
