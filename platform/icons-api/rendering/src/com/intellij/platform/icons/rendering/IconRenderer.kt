// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.rendering

import com.intellij.platform.icons.Icon
import org.jetbrains.annotations.ApiStatus

interface IconRenderer {
    val icon: Icon

    @ApiStatus.Internal fun render(paintingContext: LayerPaintingContext)

    @ApiStatus.Internal fun calculateUsedDimensions(scaling: ScalingContext): Dimensions
}
