// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsProjectModelSynchronizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * An extended version of [StoreReloadManagerImpl] which also reloads the JPS model
 */
internal class ProjectWithModulesStoreReloadManagerImpl(project: Project, coroutineScope: CoroutineScope)
  : StoreReloadManagerImpl(project, coroutineScope) {
  override suspend fun doReloadChangedStorages(): Set<Project> {
    val projectsToReload = super.doReloadChangedStorages()

    val synchronizer = JpsProjectModelSynchronizer.getInstance(project)
    if (synchronizer.needToReloadProjectEntities()) {
      withContext(Dispatchers.IO) {
        withBackgroundProgress(project, ConfigurationStoreBundle.message("progress.title.reloading.project.configuration")) {
          synchronizer.reloadProjectEntities()
        }
      }
    }

    return projectsToReload
  }
}