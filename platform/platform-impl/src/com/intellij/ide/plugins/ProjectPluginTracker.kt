// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener

@Service
class ProjectPluginTracker {
  companion object {
    val LOG = Logger.getInstance(ProjectPluginTracker::class.java)

    @JvmStatic
    fun getInstance(): ProjectPluginTracker = ServiceManager.getService(ProjectPluginTracker::class.java)
  }

  init {
    ApplicationManager.getApplication().messageBus.connect().subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
      override fun projectClosing(project: Project) {
        val pluginsToUnload = projectPluginReferences.remove(project) ?: return
        val pluginDescriptorsToUnload = pluginsToUnload.mapNotNull { PluginManagerCore.getPlugin(it) }
        LOG.info("Disabling plugins on project unload: " + pluginDescriptorsToUnload.joinToString { it.pluginId.toString() })
        PluginEnabler.enablePlugins(project, pluginDescriptorsToUnload, false)
      }
    })
  }

  private val projectPluginReferences = mutableMapOf<Project, MutableSet<PluginId>>()

  fun registerProjectPlugin(project: Project, plugin: IdeaPluginDescriptor) {
    projectPluginReferences.getOrPut(project) { mutableSetOf() }.add(plugin.pluginId)
  }
}
