// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.rendering.layers

import com.intellij.platform.icons.design.dp
import com.intellij.platform.icons.impl.layers.SpacerIconLayer
import com.intellij.platform.icons.rendering.LayerPaintingContext

class SpacerIconLayerRenderer(layer: SpacerIconLayer) : IconLayerRenderer {
    override val layout: LayerLayout = applyLayout(layer.modifier, 1.dp.compoundSize(), 1.dp.compoundSize())

    override fun render(paintingContext: LayerPaintingContext) {
        // Render nothing
    }
}
