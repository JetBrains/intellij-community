// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.rendering

import com.intellij.platform.icons.Icon
import java.util.ServiceLoader
import kotlinx.coroutines.CoroutineScope

/** Manager for Icon renderers, use convenience methods instead (like Icon.createRenderer()) */
interface IconRendererManager {
    /**
     * This method will create renderer for specific icon, keep in mind that this might be an expensive operation. Use
     * of Icon.createRenderer() is recommended
     *
     * @param context General render context, this can be used to watch for Icon updates, or set defaults for color
     *   filters etc.
     */
    fun createRenderer(icon: Icon, context: RenderingContext): IconRenderer

    fun createUpdateFlow(scope: CoroutineScope?, onUpdate: (suspend (Int) -> Unit)? = null): MutableIconUpdateFlow

    fun createRenderingContext(
        updateFlow: MutableIconUpdateFlow,
        defaultImageModifiers: ImageModifiers? = null,
    ): RenderingContext

    companion object {
        @Volatile private var instance: IconRendererManager? = null

        @JvmStatic fun getInstance(): IconRendererManager = instance ?: loadFromSPI()

        private fun loadFromSPI(): IconRendererManager =
            ServiceLoader.load(IconRendererManager::class.java).firstOrNull()
                ?: error("IconRendererManager instance is not set and there is no SPI service on classpath.")

        fun createUpdateFlow(scope: CoroutineScope?, onUpdate: (suspend (Int) -> Unit)? = null): MutableIconUpdateFlow =
            getInstance().createUpdateFlow(scope, onUpdate)

        fun activate(manager: IconRendererManager) {
            instance = manager
        }

        fun createRenderingContext(
            updateFlow: MutableIconUpdateFlow = createUpdateFlow(null),
            defaultImageModifiers: ImageModifiers? = null,
        ): RenderingContext = getInstance().createRenderingContext(updateFlow, defaultImageModifiers)
    }
}

/**
 * This method will create renderer for specific icon, keep in mind that this might be an expensive operation. Use of
 * Icon.createRenderer() is recommended over getting the IconRendererManager directly
 *
 * @param context General render context, this can be used to watch for Icon updates, or set defaults for color filters
 *   etc.
 */
fun Icon.createRenderer(context: RenderingContext): IconRenderer =
    IconRendererManager.getInstance().createRenderer(this, context)
