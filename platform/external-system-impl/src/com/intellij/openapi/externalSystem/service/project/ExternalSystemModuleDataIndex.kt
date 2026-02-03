// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project

import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsDataStorage
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.annotations.ApiStatus

object ExternalSystemModuleDataIndex {

  private val MODULE_NODE_KEY = Key.create<CachedValue<Map<String, DataNode<out ModuleData>>>>("ExternalSystemModuleDataIndex")

  @JvmStatic
  fun findModuleNode(module: Module): DataNode<out ModuleData>? {
    ExternalSystemApiUtil.getExternalRootProjectPath(module) ?: return null
    ExternalSystemApiUtil.getExternalProjectId(module) ?: return null

    val externalProjectPath = ExternalSystemApiUtil.getExternalProjectPath(module) ?: return null
    return findModuleNode(module.project, externalProjectPath)
  }

  @JvmStatic
  fun findModuleNode(project: Project, modulePath: String): DataNode<out ModuleData>? {
    val cache = getDataStorageCachedValue(project, project, MODULE_NODE_KEY, ::collectAllModuleNodes)
    return cache[modulePath]
  }

  private fun collectAllModuleNodes(project: Project): Map<String, DataNode<out ModuleData>> {
    val moduleNodes = ArrayList<DataNode<out ModuleData>>()
    val projectDataStorage = ExternalProjectsDataStorage.getInstance(project)
    ExternalSystemManager.EP_NAME.forEachExtensionSafe { manager ->
      projectDataStorage.list(manager.systemId)
        .mapNotNull { it.externalProjectStructure }
        .flatMapTo(moduleNodes) { ExternalSystemApiUtil.getChildren(it, ProjectKeys.MODULE) }
    }
    return moduleNodes.associateBy { it.data.linkedExternalProjectPath }
  }

  @ApiStatus.Internal
  fun <H : UserDataHolder, T> getDataStorageCachedValue(project: Project, dataHolder: H, key: Key<CachedValue<T>>, createValue: (H) -> T): T {
    return CachedValuesManager.getManager(project).getCachedValue(dataHolder, key, {
      CachedValueProvider.Result.create(createValue(dataHolder), ExternalProjectsDataStorage.getInstance(project))
    }, false)
  }
}