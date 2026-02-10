// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.rendering

import kotlinx.coroutines.CoroutineScope
import org.jetbrains.icons.ExperimentalIconsApi
import org.jetbrains.icons.Icon
import java.util.ServiceLoader

/**
 * Manager for Icon renderers, use convenience methods instead (like Icon.createRenderer())
 */
@ExperimentalIconsApi
interface IconRendererManager {
  /**
   * This method will create renderer for specific icon, keep in mind that this might be an expensive operation.
   * Use of Icon.createRenderer() is recommended
   * @param context General render context, this can be used to watch for Icon updates, or set defaults for color filters etc.
   * @param loadingStrategy Dictates how the Icon loading should be performed, like block thread, show placeholder, or render blank area
   */
  @ExperimentalIconsApi
  fun createRenderer(icon: Icon, context: RenderingContext, loadingStrategy: LoadingStrategy = LoadingStrategy.BlockThread): IconRenderer

  fun createUpdateFlow(scope: CoroutineScope?, updateCallback: (Int) -> Unit): MutableIconUpdateFlow

  fun createRenderingContext(updateFlow: MutableIconUpdateFlow, defaultImageModifiers: ImageModifiers? = null): RenderingContext

  companion object {
    @Volatile
    private var instance: IconRendererManager? = null

    @JvmStatic
    fun getInstance(): IconRendererManager = instance ?: loadFromSPI()

    private fun loadFromSPI(): IconRendererManager =
      ServiceLoader.load(IconRendererManager::class.java).firstOrNull()
      ?: error("IconRendererManager instance is not set and there is no SPI service on classpath.")

    fun createUpdateFlow(scope: CoroutineScope?, updateCallback: (Int) -> Unit): MutableIconUpdateFlow = getInstance().createUpdateFlow(scope, updateCallback)

    fun activate(manager: IconRendererManager) {
      instance = manager
    }

    fun createRenderingContext(updateFlow: MutableIconUpdateFlow, defaultImageModifiers: ImageModifiers? = null): RenderingContext {
      return getInstance().createRenderingContext(updateFlow, defaultImageModifiers)
    }
  }
}

/**
 * This method will create renderer for specific icon, keep in mind that this might be an expensive operation.
 * Use of Icon.createRenderer() is recommended over getting the IconRendererManager directly
 * @param context General render context, this can be used to watch for Icon updates, or set defaults for color filters etc.
 * @param loadingStrategy Dictates how the Icon loading should be performed, like block thread, show placeholder, or render blank area
 */
@ExperimentalIconsApi
fun Icon.createRenderer(context: RenderingContext, loadingStrategy: LoadingStrategy = LoadingStrategy.BlockThread): IconRenderer {
  return IconRendererManager.getInstance().createRenderer(this, context, loadingStrategy)
}
