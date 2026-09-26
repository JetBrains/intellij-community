// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.rendering.layers

import com.intellij.platform.icons.design.dp
import com.intellij.platform.icons.impl.modifiers.applyTo
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.rendering.LayerPaintingContext

interface IconLayerRenderer {
    /**
     * Renders the icon layer using the provided painting API. The renderer is required to set
     * paintingContext.usedDimensions to the actual dimensions of the rendered content. Parent layers will use this
     * information to calculate the layout of the next layer.
     */
    fun render(paintingContext: LayerPaintingContext)

    val layout: LayerLayout

    fun applyLayout(modifier: IconModifier, width: CompoundSize, height: CompoundSize): LayerLayout {
        val layout =
            LayerLayout(
                0.dp.compoundSize(),
                0.dp.compoundSize(),
                0.dp.compoundSize(),
                0.dp.compoundSize(),
                width,
                height,
            )
        return modifier.applyTo(layout)
    }
}
