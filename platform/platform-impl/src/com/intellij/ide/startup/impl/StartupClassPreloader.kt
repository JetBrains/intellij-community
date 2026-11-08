// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.impl

import com.intellij.ide.plugins.PluginSet
import com.intellij.idea.AppMode
import com.intellij.openapi.editor.impl.warmup.EDITOR_CLASSES
import com.intellij.openapi.editor.impl.warmup.WarmUpClassList
import com.intellij.openapi.editor.impl.warmup.warmUpClasses
import com.intellij.util.lang.UrlClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

private object StartupClassPreloader

/** The warm-up class list of the startup UI. It leaves out the classes of [EDITOR_CLASSES]. */
internal const val STARTUP_UI_CLASSES_PATH: String = "com/intellij/ide/startup/impl/startup-ui-classes.txt"

/**
 * Tells whether the application start arms [com.intellij.util.StartupUiGate],
 * JetBrains Light does
 */
@ApiStatus.Internal
fun isStartupUiGateEnabled(): Boolean = !AppMode.isHeadless() && System.getProperty("ide.startup.ui.gate").toBoolean()

/**
 * Loads and links the classes that the project frame and the first editor need, so the EDT does not do it.
 */
@ApiStatus.Internal
suspend fun preloadStartupUiClasses(awaitPluginSet: suspend () -> PluginSet) {
  val platformLoader = StartupClassPreloader::class.java.classLoader
  val concurrency = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 8)
  withContext(Dispatchers.Default) {
    launch {
      warmUpClasses(EDITOR_CLASSES.initialize + EDITOR_CLASSES.loadOnly, initialize = false) { platformLoader }
    }
    val list = WarmUpClassList.read(platformLoader, STARTUP_UI_CLASSES_PATH)
    warmUpClasses(list.initialize + list.loadOnly, initialize = false, concurrency = concurrency) { platformLoader }
    if (list.plugin.isEmpty()) {
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
    warmUpClasses(list.plugin, initialize = false, concurrency = concurrency) { name ->
      byPrefix.firstOrNull { name.startsWith(it.first) }?.second
      ?: byPackage.computeIfAbsent(name.substringBeforeLast('.', "")) { _ ->
        val resource = name.replace('.', '/') + ".class"
        pluginLoaders.firstOrNull { it.classPath.findResource(resource) != null } ?: platformLoader
      }
    }
  }
}
