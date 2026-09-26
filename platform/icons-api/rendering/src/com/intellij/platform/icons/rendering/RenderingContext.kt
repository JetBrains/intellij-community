// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.rendering

import com.intellij.platform.icons.ImageResourceLocation

/** Rendering context affects the behavior of the actual renderers and can be used to pass update callbacks. */
interface RenderingContext {
    val updateFlow: MutableIconUpdateFlow
    val defaultImageModifiers: ImageModifiers?
    val theme: ThemeContext

    fun imageResource(loader: ImageResourceLocation, imageModifiers: ImageModifiers? = null): ImageResource

    companion object {
        val Empty: RenderingContext =
            IconRendererManager.createRenderingContext(IconRendererManager.createUpdateFlow(null))
    }
}
