// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.artifacts.workspacemodel

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectServiceContainerCustomizer
import com.intellij.packaging.artifacts.ArtifactManager
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.workspaceModel.ide.WorkspaceModel
import org.jetbrains.annotations.ApiStatus

/**
 * This class should be removed after enabling new [ArtifactManagerBridge] for the whole users without flag.
 * Alongside with this Java plugin should be removed from the approved for usage of `projectServiceContainerCustomizer`
 * at `projectLoader.kt`.  Dependency to `intellij.platform.serviceContainer` should also be removed from the module.
 */
@ApiStatus.Internal
class LegacyBridgeArtifactManagerRedefiner : ProjectServiceContainerCustomizer {
  companion object {
    private val LOG = logger<LegacyBridgeArtifactManagerRedefiner>()
  }

  override fun serviceRegistered(project: Project) {
    LOG.info("Using workspace model to open project")

    val pluginDescriptor = PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID)
                           ?: error("Could not find plugin by id: ${PluginManagerCore.CORE_ID}")

    if (WorkspaceModel.enabledForArtifacts) {
      val container = project as ComponentManagerImpl
      container.registerService(ArtifactManager::class.java, ArtifactManagerBridge::class.java, pluginDescriptor, override = true,
                                preloadMode = ServiceDescriptor.PreloadMode.AWAIT)
    }
  }
}
