// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.rendering

import com.intellij.platform.icons.ImageResourceLocation
import com.intellij.platform.icons.impl.rendering.layers.generateImageModifiers
import com.intellij.platform.icons.layers.IconLayer
import com.intellij.platform.icons.rendering.ImageModifiers
import com.intellij.platform.icons.rendering.ImageResource
import com.intellij.platform.icons.rendering.ImageResourceProvider
import com.intellij.platform.icons.rendering.MutableIconUpdateFlow
import com.intellij.platform.icons.rendering.RenderingContext
import com.intellij.platform.icons.rendering.ThemeContext

class DefaultRenderingContext(
    override val updateFlow: MutableIconUpdateFlow,
    override val defaultImageModifiers: DefaultImageModifiers?,
    override val theme: ThemeContext,
    val imageResourceProvider: ImageResourceProvider,
) : RenderingContext {
    fun adjustTo(iconLayer: IconLayer): DefaultRenderingContext =
        DefaultRenderingContext(updateFlow, iconLayer.generateImageModifiers(this), theme, imageResourceProvider)

    override fun imageResource(loader: ImageResourceLocation, imageModifiers: ImageModifiers?): ImageResource =
        imageResourceProvider.loadImage(loader, imageModifiers)
}
