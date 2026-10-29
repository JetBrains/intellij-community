// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.impl

import com.intellij.ide.plugins.PluginSet
import com.intellij.platform.util.coroutines.forEachConcurrent
import com.intellij.util.lang.UrlClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

private object StartupClassPreloader

private const val PLUGIN_CLASSES_MARKER = "# Plugin classes"

/**
 * Loads and links the classes that the project frame and the first editor need, so the EDT does not do it.
 * The list is recorded from a startup class trace; its `# Plugin classes` block waits for [awaitPluginSet].
 */
@ApiStatus.Internal
suspend fun preloadStartupUiClasses(awaitPluginSet: suspend () -> PluginSet) {
  val platformLoader = StartupClassPreloader::class.java.classLoader
  val resource = StartupClassPreloader::class.java.getResourceAsStream("startup-ui-classes.txt") ?: return
  val lines = resource.bufferedReader().use { it.readLines() }
  val marker = lines.indexOfFirst { it.startsWith(PLUGIN_CLASSES_MARKER) }
  val platformNames = (if (marker < 0) lines else lines.subList(0, marker)).filter { it.isNotBlank() && !it.startsWith('#') }
  val pluginNames = if (marker < 0) emptyList() else lines.subList(marker + 1, lines.size).filter { it.isNotBlank() && !it.startsWith('#') }
  val concurrency = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 8)
  withContext(Dispatchers.Default) {
    platformNames.forEachConcurrent(concurrency) { name ->
      load(name, platformLoader)
    }
    if (pluginNames.isEmpty()) {
      return@withContext
    }
    val pluginSet = awaitPluginSet()
    val modules = pluginSet.getEnabledModules()
    val byPrefix = modules
      .mapNotNull { module ->
        val prefix = module.packagePrefix ?: return@mapNotNull null
        val loader = module.pluginClassLoader ?: return@mapNotNull null
        (if (prefix.endsWith('.')) prefix else "$prefix.") to loader
      }
      .sortedByDescending { it.first.length }
    val pluginLoaders = modules.mapNotNull { it.pluginClassLoader as? UrlClassLoader }.distinct()
    val byPackage = ConcurrentHashMap<String, ClassLoader>()
    pluginNames.forEachConcurrent(concurrency) { name ->
      val loader = byPrefix.firstOrNull { name.startsWith(it.first) }?.second
                   ?: byPackage.computeIfAbsent(name.substringBeforeLast('.', "")) { _ ->
                     val resource = name.replace('.', '/') + ".class"
                     pluginLoaders.firstOrNull { it.classPath.findResource(resource) != null } ?: platformLoader
                   }
      load(name, loader)
    }
  }
}

private suspend fun load(name: String, loader: ClassLoader) {
  try {
    Class.forName(name, false, loader).declaredConstructors
  }
  catch (_: Throwable) {
    currentCoroutineContext().ensureActive()
  }
}
