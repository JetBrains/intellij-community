// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.diagnostic.PluginException
import com.intellij.diagnostic.RemoteSerializedThrowable
import com.intellij.ide.plugins.PluginManagerCore.CORE_ID
import com.intellij.ide.plugins.PluginManagerCore.isPlatformClass
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.io.sanitizeFileName
import com.intellij.util.lang.UrlClassLoader
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

@ApiStatus.Internal
object PluginUtils {
  private val LOG = logger<PluginUtils>()

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
  fun Iterable<PluginId>.joinedPluginIds(state: String): String =
    joinToString(prefix = "Marking plugins '$state': [", postfix = "]") { it.idString }

  /** don't expose user home in error messages */
  @JvmStatic
  fun pluginPathToUserString(file: Path): String =
    file.toString().replace("${System.getProperty("user.home")}${File.separatorChar}", "~${File.separatorChar}")

  private val findLoadedClassHandle: MethodHandle by lazy(LazyThreadSafetyMode.NONE) {
    val method = ClassLoader::class.java.getDeclaredMethod("findLoadedClass", String::class.java)
    method.isAccessible = true
    MethodHandles.lookup().unreflect(method)
  }

  /** Use only if [Class] is not available. */
  @JvmStatic
  fun getPluginByClassNameAsNoAccessToClass(className: String, pluginSet: PluginSet): IdeaPluginDescriptorImpl? {
    val plugin = getPluginDescriptorOrPlatformByClassName(className, pluginSet)
    return if (plugin == null || plugin.pluginId == CORE_ID) null else plugin
  }

  @JvmStatic
  fun getPluginDescriptorOrPlatformByClassName(className: String): PluginDescriptor? {
    val pluginSet = PluginManagerCore.getPluginSetOrNull()
    return if (pluginSet == null) null else getPluginDescriptorOrPlatformByClassName(className, pluginSet)
  }

  @JvmStatic
  fun getPluginDescriptorOrPlatformByClassName(className: String, pluginSet: PluginSet): IdeaPluginDescriptorImpl? {
    if (isPlatformClass(className) || !className.contains('.')) {
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
      if (!descriptor.useIdeaClassLoader) {
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

  fun PluginId.asSanitizedPathElement(): String = sanitizeFileName(idString)

  @JvmStatic
  fun PluginSet.findEnabledOrInstalledPlugin(id: PluginId): IdeaPluginDescriptorImpl? {
    return findEnabledPlugin(id) ?: findInstalledPlugin(id)
  }

  /** Attributes the throwable to the owning plugin by its type, stack trace, message, and causes; null for platform code. */
  @JvmStatic
  fun findPlugin(t: Throwable, pluginSet: PluginSet?): Pair<PluginId, IdeaPluginDescriptorImpl?>? {
    when (t) {
      is PluginException -> return t.pluginId?.let { it to pluginSet?.findEnabledOrInstalledPlugin(it) }
      is RemoteSerializedThrowable -> return t.pluginId?.let { it to pluginSet?.findEnabledOrInstalledPlugin(it) }
    }

    var bundledPlugin: IdeaPluginDescriptorImpl? = null
    if (pluginSet != null) {
      val visitedClassNames = HashSet<String>()
      for (element in t.stackTrace) {
        val className = element?.className ?: continue
        if (!visitedClassNames.add(className)) {
          continue
        }
        val descriptor = getPluginDescriptorOrPlatformByClassName(className, pluginSet) ?: continue
        if (CORE_ID == descriptor.pluginId) {
          continue
        }
        if (descriptor.isBundled) {
          if (bundledPlugin == null) {
            bundledPlugin = descriptor
            logPluginDetection(className, descriptor)
          }
        }
        else {
          logPluginDetection(className, descriptor)
          return descriptor.pluginId to descriptor
        }
      }

      getClassFromKnownMessages(t)?.let { classFromMessage ->
        getPluginByClassNameAsNoAccessToClass(classFromMessage, pluginSet)?.let {
          return it.pluginId to it
        }
      }
    }

    return t.cause?.let { findPlugin(it, pluginSet) }
           ?: bundledPlugin?.let { it.pluginId to it }
  }

  fun getClassFromKnownMessages(t: Throwable): String? {
    fun classNameOfMethodReference(methodReference: String): String? =
      methodReference.substringBeforeLast('.', "").replace('/', '.').takeIf { it.isNotEmpty() }

    val message = t.message ?: return null
    return when (t) {
      is ClassNotFoundException -> message.substringBefore(' ')
      is NoSuchMethodException -> classNameOfMethodReference(message.substringBefore('('))
      is NoClassDefFoundError -> message.substringAfterLast(' ').replace('/', '.')
      is AbstractMethodError -> when {
        message.startsWith("Method ") -> classNameOfMethodReference(message.removePrefix("Method ").substringBefore('('))
        message.startsWith("Receiver class ") -> message.removePrefix("Receiver class ").substringBefore(' ')
        message.startsWith("Missing implementation ") -> message.substringAfterLast(' ').removeSuffix(".")
        else -> classNameOfMethodReference(message.substringBefore('('))
      }
      else -> null
    }
  }

  private fun logPluginDetection(className: String, descriptor: PluginDescriptor) {
    if (!LOG.isDebugEnabled) {
      return
    }
    LOG.debug(buildString {
      append("Detected a plugin ").append(descriptor.pluginId).append(" by class ").append(className)
      val loader = descriptor.pluginClassLoader
      if (loader != null) {
        append("; loader=").append(loader).append('/').append(loader.javaClass)
        if (loader is PluginClassLoader) {
          append("; loaded class: ").append(loader.hasLoadedClass(className))
        }
      }
    })
  }
}
