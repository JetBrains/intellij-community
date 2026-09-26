// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.layers

import com.intellij.platform.icons.layers.IconLayer
import com.intellij.platform.icons.modifiers.IconModifier
import kotlinx.serialization.Serializable

@Serializable
class SpacerIconLayer(override val modifier: IconModifier) : IconLayer {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SpacerIconLayer

        return modifier == other.modifier
    }

    override fun hashCode(): Int = modifier.hashCode()

    override fun toString(): String = "SpacerIconLayer(modifier=$modifier)"
}
