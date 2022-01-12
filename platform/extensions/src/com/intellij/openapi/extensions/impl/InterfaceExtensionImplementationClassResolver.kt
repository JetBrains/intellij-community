// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions.impl

import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation

internal fun interface ImplementationClassResolver {
  fun resolveImplementationClass(componentManager: ComponentManager, adapter: ExtensionComponentAdapter): Class<*>
}

internal class InterfaceExtensionImplementationClassResolver private constructor() : ImplementationClassResolver {
  companion object {
    @JvmField
    val INSTANCE = InterfaceExtensionImplementationClassResolver()
  }

  override fun resolveImplementationClass(componentManager: ComponentManager, adapter: ExtensionComponentAdapter): Class<*> {
    val className = adapter.implementationClassOrName
    if (className !is String) {
      return className as Class<*>
    }

    val pluginDescriptor = adapter.getPluginDescriptor()
    val result = componentManager.loadClass<Any>(className, pluginDescriptor)
    @Suppress("SpellCheckingInspection")
    if (result.classLoader !== pluginDescriptor.pluginClassLoader && pluginDescriptor.pluginClassLoader != null &&
        !className.startsWith("com.intellij.webcore.resourceRoots.") &&
        !className.startsWith("com.intellij.tasks.impl.") &&
        !result.isAnnotationPresent(InternalIgnoreDependencyViolation::class.java)) {
      val idString = pluginDescriptor.pluginId.idString
      if (idString != "com.intellij.java" &&
          idString != "com.intellij.java.ide" &&
          idString != "org.jetbrains.android" &&
          idString != "com.intellij.kotlinNative.platformDeps" &&
          idString != "com.jetbrains.rider.android") {
        ExtensionPointImpl.LOG.error(componentManager.createError("""Created extension classloader is not equal to plugin's one.
See https://youtrack.jetbrains.com/articles/IDEA-A-65/Plugin-Model#internalignoredependencyviolation
(
  className=$className,
  extensionInstanceClassloader=${result.classLoader},
  pluginClassloader=${pluginDescriptor.pluginClassLoader}
)""", pluginDescriptor.pluginId))
      }
    }
    adapter.implementationClassOrName = result
    return result
  }
}