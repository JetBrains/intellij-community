// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.modifiers

import com.intellij.platform.icons.impl.rendering.layers.LayerLayout
import com.intellij.platform.icons.modifiers.SizeVariantPolicy
import kotlinx.serialization.Serializable

/**
 * Sets the [SizeVariantPolicy] of the image layers it applies to; resolved into `DefaultImageModifiers.sizeVariantPolicy` by
 * `generateImageModifiers` and inherited by nested layers. It does not change the layout.
 */
@Serializable
class SizeVariantModifier(val policy: SizeVariantPolicy) : ApplyableIconModifier {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SizeVariantModifier

        return policy == other.policy
    }

    override fun hashCode(): Int = policy.hashCode()

    override fun toString(): String = "SizeVariantModifier(policy=$policy)"

    override fun applyTo(layout: LayerLayout): LayerLayout = layout
}
