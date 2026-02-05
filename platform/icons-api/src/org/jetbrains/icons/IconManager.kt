// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons

import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import org.jetbrains.icons.design.IconDesigner
import org.jetbrains.icons.modifiers.IconModifier
import java.util.ServiceLoader

@ExperimentalIconsApi
interface IconManager {
  /**
   * Creates new Icon "description", this is a cheap operation, to render the Icon, use Icon.createRenderer() function.
   * Use convenience top-level method icon {} instead if possible.
   */
  fun icon(designer: IconDesigner.() -> Unit): Icon

  /**
   * Dynamic icon allows dynamic icon swapping after the Icon model was created, the renderers created from such
   * icon should be able to listen to these changes and swap the actual rendered icon on-the-fly.
   */
  fun dynamicIcon(icon: Icon): DynamicIcon

  fun getSerializersModule(): SerializersModule

  companion object {
    @Volatile
    private var instance: IconManager? = null

    @JvmStatic
    fun getInstance(): IconManager = instance ?: loadFromSPI()

    private fun loadFromSPI(): IconManager =
      ServiceLoader.load(IconManager::class.java).firstOrNull()
      ?: error("IconManager instance is not set and there is no SPI service on classpath.")


    fun activate(manager: IconManager) {
      instance = manager
    }
  }
}

/**
 * Creates new Icon
 * The result should be serializable and contains description of what the Icon should look like.
 *
 * To render the Icon, renderer has to be obtained first using createRenderer(), however this should be only done
 * from inside components. (check intellij.platform.icons.api.rendering module), for usage inside swing,
 * check intellij.platform.icons.api.legacyIconSupport module.
 *
 * Usage:
 * '''
 * icon {
 *    image("icons/icon.svg", MyClass::class.java.classLoader)
 * }
 * '''
 *
 * Check the designer interface for layer options. Also check intellij.platform.icons.api.legacyIconSupport module
 * to find out how to convert old icons and new icons.
 */
fun icon(designer: IconDesigner.() -> Unit): Icon = IconManager.getInstance().icon(designer)

fun dynamicIcon(designer: IconDesigner.() -> Unit): DynamicIcon = IconManager.getInstance().dynamicIcon(icon(designer))

fun dynamicIcon(icon: Icon): DynamicIcon = IconManager.getInstance().dynamicIcon(icon)

fun imageIcon(path: String, classLoader: ClassLoader? = null, modifier: IconModifier = IconModifier): Icon = icon { image(path, classLoader, modifier) }