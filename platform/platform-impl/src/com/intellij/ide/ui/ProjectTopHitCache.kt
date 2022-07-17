// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.ide.ui.OptionsSearchTopHitProvider.ProjectLevelProvider
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class ProjectTopHitCache(project: Project) : TopHitCache() {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): TopHitCache = project.service<ProjectTopHitCache>()
  }

  init {
    OptionsTopHitProvider.PROJECT_LEVEL_EP.addExtensionPointListener(object : ExtensionPointListener<ProjectLevelProvider?> {
      override fun extensionRemoved(extension: ProjectLevelProvider, pluginDescriptor: PluginDescriptor) {
        invalidateCachedOptions(extension.javaClass)
      }
    }, project)
  }
}