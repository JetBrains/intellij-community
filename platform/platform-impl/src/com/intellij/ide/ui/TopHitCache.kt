// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.diagnostic.PluginException
import com.intellij.ide.ui.OptionsSearchTopHitProvider.ApplicationLevelProvider
import com.intellij.ide.ui.OptionsSearchTopHitProvider.ProjectLevelProvider
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

@Service
private class AppTopHitCache : TopHitCache()

@ApiStatus.Internal
sealed class TopHitCache : Disposable {
  companion object {
    fun getInstance(): TopHitCache = service<AppTopHitCache>()

    suspend fun getInstanceAsync(): TopHitCache = serviceAsync<AppTopHitCache>()

    fun getInstance(project: Project): TopHitCache = project.service<ProjectTopHitCache>()

    suspend fun getInstanceAsync(project: Project): TopHitCache = project.serviceAsync<ProjectTopHitCache>()
  }

  private val map = ConcurrentHashMap<Class<*>, Collection<OptionDescription>>()

  override fun dispose() {
  }

  fun clear() {
    map.clear()
  }

  fun invalidateCachedOptions(providerClass: Class<out OptionsSearchTopHitProvider>) {
    map.remove(providerClass)
  }

  internal fun getCache(): Map<Class<*>, Collection<OptionDescription>> = map

  fun getCachedOptions(
    provider: OptionsSearchTopHitProvider,
    project: Project?,
    pluginDescriptor: PluginDescriptor?,
  ): Collection<OptionDescription> {
    return map.computeIfAbsent(provider.javaClass) { aClass ->
      if (provider is Disposable) {
        val errorMessage = "${aClass.name} must not implement Disposable"
        if (pluginDescriptor == null) {
          logger<TopHitCache>().error(errorMessage)
        }
        else {
          logger<TopHitCache>().error(PluginException(errorMessage, pluginDescriptor.pluginId))
        }
      }

      when (provider) {
        is ProjectLevelProvider -> provider.getOptions(project!!)
        is ApplicationLevelProvider -> provider.options
        else -> emptyList()
      }
    }
  }
}

@Service(Service.Level.PROJECT)
private class ProjectTopHitCache(project: Project) : TopHitCache() {
  init {
    OptionsTopHitProvider.PROJECT_LEVEL_EP.addExtensionPointListener(object : ExtensionPointListener<ProjectLevelProvider> {
      override fun extensionRemoved(extension: ProjectLevelProvider, pluginDescriptor: PluginDescriptor) {
        invalidateCachedOptions(extension.javaClass)
      }
    }, project)
  }
}