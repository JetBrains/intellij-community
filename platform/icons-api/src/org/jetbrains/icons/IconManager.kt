// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons

import kotlinx.serialization.modules.SerializersModule
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.icons.design.IconDesigner
import org.jetbrains.icons.modifiers.IconModifier
import java.util.ServiceLoader

@ApiStatus.Experimental
interface IconManager {
  /**
   * Creates new Icon "description", this is a cheap operation, to render the Icon, use Icon.createRenderer() function.
   * Use convenience top-level method icon {} instead if possible.
   */
  fun icon(designer: IconDesigner.() -> Unit): Icon

  /**
   * @see org.jetbrains.icons.deferredIcon
   */
  fun deferredIcon(placeholder: Icon?, identifier: String? = null, classLoader: ClassLoader? = null, evaluator: suspend () -> Icon): Icon

  suspend fun forceEvaluation(icon: DeferredIcon): Icon

  /**
   * Converts specific Icon to swing Icon.
   * ! This is an expensive operation and can include image loading, reuse the instance if possible. !
   */
  fun toSwingIcon(icon: Icon): javax.swing.Icon

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
 * use toSwingIcon method.
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
 *
 * @see IconManager.toSwingIcon
 */
fun icon(designer: IconDesigner.() -> Unit): Icon = IconManager.getInstance().icon(designer)

/**
 * Deferred icon allows apis to return an Icon that takes some time to compute;
 * optional placeholder can be included to allow rendering it before the actual icon is ready.
 *
 * To cache such icons and synchronize them over the network, some identifier should be given.
 * Implementations might try to prefix the identifier with the source pluginId/moduleId to avoid clashes.
 *
 * If the identifier is not passed, an automatic one is created, however, this will prevent the result
 * from being cached, as a new one is generated per each deferredIcon() call.
 *
 * @param classLoader This classLoader might be used to prevent id clashes (prefix with pluginId & moduleId if possible for example)
 */
fun deferredIcon(placeholder: Icon?, identifier: String? = null, classLoader: ClassLoader? = null, evaluator: suspend () -> Icon): Icon =
  IconManager.getInstance().deferredIcon(placeholder, identifier, classLoader, evaluator)

fun imageIcon(path: String, classLoader: ClassLoader? = null, modifier: IconModifier = IconModifier): Icon = icon { image(path, classLoader, modifier) }