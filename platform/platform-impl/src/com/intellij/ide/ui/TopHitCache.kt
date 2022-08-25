// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.diagnostic.ActivityCategory
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.ui.OptionsSearchTopHitProvider.ApplicationLevelProvider
import com.intellij.ide.ui.OptionsSearchTopHitProvider.ProjectLevelProvider
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import java.util.concurrent.ConcurrentHashMap

open class TopHitCache : Disposable {
  companion object {
    fun getInstance(): TopHitCache = service()

    private fun dispose(options: Collection<OptionDescription>?) {
      options?.forEach { option: OptionDescription -> dispose(option) }
    }

    private fun dispose(option: OptionDescription) {
      if (option is Disposable) {
        Disposer.dispose((option as Disposable))
      }
    }
  }

  @JvmField
  protected val map = ConcurrentHashMap<Class<*>, Collection<OptionDescription>>()

  override fun dispose() {
    clear()
  }

  fun clear() {
    map.values.forEach(::dispose)
    map.clear()
  }

  fun invalidateCachedOptions(providerClass: Class<out OptionsSearchTopHitProvider>) {
    map.remove(providerClass)?.let(::dispose)
  }

  fun getCachedOptions(provider: OptionsSearchTopHitProvider,
                       project: Project?,
                       pluginDescriptor: PluginDescriptor?): Collection<OptionDescription> {
    return map.computeIfAbsent(provider.javaClass) { aClass ->
      val startTime = StartUpMeasurer.getCurrentTime()
      val result = when (provider) {
        is ProjectLevelProvider -> provider.getOptions(project!!)
        is ApplicationLevelProvider -> provider.options
        else -> return@computeIfAbsent emptyList()
      }

      val category = if (project == null) ActivityCategory.APP_OPTIONS_TOP_HIT_PROVIDER else ActivityCategory.PROJECT_OPTIONS_TOP_HIT_PROVIDER
      StartUpMeasurer.addCompletedActivity(startTime, aClass, category, pluginDescriptor?.pluginId?.idString)
      result
    }
  }
}

@Service(Service.Level.PROJECT)
class ProjectTopHitCache(project: Project) : TopHitCache() {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): TopHitCache = project.service<ProjectTopHitCache>()
  }

  init {
    OptionsTopHitProvider.PROJECT_LEVEL_EP.addExtensionPointListener(object : ExtensionPointListener<ProjectLevelProvider> {
      override fun extensionRemoved(extension: ProjectLevelProvider, pluginDescriptor: PluginDescriptor) {
        invalidateCachedOptions(extension.javaClass)
      }
    }, project)
  }
}