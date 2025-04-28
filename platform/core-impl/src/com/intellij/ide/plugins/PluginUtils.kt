// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.PluginManagerCore.CORE_ID
import com.intellij.ide.plugins.PluginManagerCore.isPlatformClass
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.lang.UrlClassLoader
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

@ApiStatus.Internal
object PluginUtils {
  @JvmStatic
  fun Iterable<IdeaPluginDescriptor>.toPluginIdSet(): Set<PluginId> = mapTo(LinkedHashSet()) { it.pluginId }

  @JvmStatic
  fun Iterable<String>.parseAsPluginIdSet(): Set<PluginId> = asSequence()
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .mapTo(LinkedHashSet(), PluginId::getId)

  @JvmStatic
  fun Iterable<PluginId>.toPluginDescriptors(): List<IdeaPluginDescriptorImpl> {
    val pluginIdMap = PluginManagerCore.buildPluginIdMap()
    return mapNotNull { pluginIdMap[it] }
  }

  @JvmStatic
  fun Iterable<PluginId>.joinedPluginIds(operation: String): String =
    joinToString(prefix = "Plugins to $operation: [", postfix = "]") { it.idString }

  /** don't expose user home in error messages */
  @JvmStatic
  fun pluginPathToUserString(file: Path): String =
    file.toString().replace("${System.getProperty("user.home")}${File.separatorChar}", "~${File.separatorChar}")

  private val findLoadedClassHandle: MethodHandle by lazy(LazyThreadSafetyMode.NONE) {
    val method = ClassLoader::class.java.getDeclaredMethod("findLoadedClass", String::class.java)
    method.isAccessible = true
    MethodHandles.lookup().unreflect(method)
  }

  @ApiStatus.Internal
  @JvmStatic
  fun getPluginDescriptorOrPlatformByClassName(className: String): PluginDescriptor? {
    val pluginSet = PluginManagerCore.getPluginSetOrNull()
    if (pluginSet == null || isPlatformClass(className) || !className.contains('.')) {
      return null
    }

    var result: IdeaPluginDescriptorImpl? = null
    for (descriptor in pluginSet.getEnabledModules()) {
      val classLoader = descriptor.getPluginClassLoader()
      if (classLoader is UrlClassLoader) {
        if (classLoader.hasLoadedClass(className)) {
          result = descriptor
          break
        }
      }
      else if (classLoader != null && findLoadedClassHandle.invoke(classLoader, className) != null) {
        result = descriptor
        break
      }
    }

    if (result == null) {
      return null
    }

    // return if the found plugin is not `core`, or the package is unambiguously "core"
    if (CORE_ID != result.getPluginId() ||
        className.startsWith("com.jetbrains.") || className.startsWith("org.jetbrains.") ||
        className.startsWith("com.intellij.") || className.startsWith("org.intellij.") ||
        className.startsWith("com.android.") ||
        className.startsWith("git4idea.") || className.startsWith("org.angularjs.")) {
      return result
    }
    else {
      return findClassInPluginThatUsesCoreClassloader(className, pluginSet)
    }

    // otherwise, we need to check plugins with use-idea-classloader="true"
  }

  @ApiStatus.Internal
  fun getPluginDescriptorIfIdeaClassLoaderIsUsed(aClass: Class<*>): PluginDescriptor? {
    val className = aClass.getName()
    val pluginSet = PluginManagerCore.getPluginSetOrNull()
    if (pluginSet == null || isPlatformClass(className) || !className.contains('.')) {
      return null
    }
    else {
      return findClassInPluginThatUsesCoreClassloader(className, pluginSet)
    }
  }

  private fun findClassInPluginThatUsesCoreClassloader(className: String, pluginSet: PluginSet): IdeaPluginDescriptorImpl? {
    var root: String? = null
    for (descriptor in pluginSet.enabledPlugins) {
      if (!descriptor.isUseIdeaClassLoader) {
        continue
      }

      if (root == null) {
        root = PathManager.getResourceRoot(descriptor.getClassLoader(), className.replace('.', '/') + ".class")
        if (root == null) {
          return null
        }
      }
      val path = descriptor.getPluginPath()
      if (root.startsWith(path.invariantSeparatorsPathString)) {
        return descriptor
      }
    }
    return null
  }
}
