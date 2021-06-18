// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectServiceContainerCustomizer
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.workspaceModel.ide.WorkspaceModel
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class LegacyBridgeProjectLifecycleListener : ProjectServiceContainerCustomizer {
  companion object {
    private val LOG = logger<LegacyBridgeProjectLifecycleListener>()
  }

  override fun serviceRegistered(project: Project) {
    LOG.info("Using workspace model to open project")

    val pluginDescriptor = PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID)
                           ?: error("Could not find plugin by id: ${PluginManagerCore.CORE_ID}")

    if (WorkspaceModel.enabledForArtifacts) {
      val container = project as ComponentManagerImpl
      registerArtifactManager(container, pluginDescriptor)
    }
  }

  private fun registerArtifactManager(container: ComponentManagerImpl, pluginDescriptor: IdeaPluginDescriptor) {
    try { //todo improve
      val apiClass = Class.forName("com.intellij.packaging.artifacts.ArtifactManager", true, javaClass.classLoader)
      val implClass = Class.forName("com.intellij.packaging.impl.artifacts.workspacemodel.ArtifactManagerBridge", true,
                                    javaClass.classLoader)
      container.registerService(apiClass, implClass, pluginDescriptor, override = true, preloadMode = ServiceDescriptor.PreloadMode.AWAIT)
    }
    catch (ignored: Throwable) {
    }
  }
}
