// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView.backend

import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.dashboard.RunDashboardManagerProxy
import com.intellij.ide.ui.icons.rpcId
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.readAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.platform.execution.serviceView.setServiceViewImplementationForNextIdeRun
import com.intellij.platform.execution.serviceView.splitApi.ServiceViewConfigurationType
import com.intellij.platform.execution.serviceView.splitApi.ServiceViewConfigurationTypeSettings
import com.intellij.platform.execution.serviceView.splitApi.ServiceViewRpc
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull

internal class ServiceViewRpcImpl : ServiceViewRpc {
  companion object {
    private val EP_NAME: ExtensionPointName<ServiceViewLocatableSearcher> =
      ExtensionPointName.create("com.intellij.serviceViewLocatableSearcher")
  }

  override suspend fun findServices(fileId: VirtualFileId, projectId: ProjectId): List<String> {
    val virtualFile = fileId.virtualFile() ?: return emptyList()
    val project = projectId.findProjectOrNull() ?: return emptyList()

    return readAction {
      val result = mutableSetOf<String>()
      for (searcher in EP_NAME.extensions) {
        result.addAll(searcher.find(project, virtualFile))
      }
      return@readAction result.toList()
    }
  }

  override suspend fun loadConfigurationTypes(projectId: ProjectId): ServiceViewConfigurationTypeSettings? {
    val project = projectId.findProjectOrNull() ?: return null

    val configuredTypes = collectTypes(project)
    return ServiceViewConfigurationTypeSettings(
      configuredTypes.first.map { ServiceViewConfigurationType(it.id, it.displayName, it.icon.rpcId()) },
      configuredTypes.second.map { ServiceViewConfigurationType(it.id, it.displayName, it.icon.rpcId()) },
    )
  }

  private fun collectTypes(project: Project): Pair<List<ConfigurationType>, List<ConfigurationType>> {
    val includedTypes = ArrayList<ConfigurationType>()
    val excludedTypes = ArrayList<ConfigurationType>()
    val types = RunDashboardManagerProxy.getInstance(project).types
    for (type in ConfigurationType.CONFIGURATION_TYPE_EP.extensionList) {
      if (types.contains(type.id)) {
        includedTypes.add(type)
      }
      else {
        excludedTypes.add(type)
      }
    }
    return Pair(includedTypes, excludedTypes)
  }

  override suspend fun saveConfigurationTypes(projectId: ProjectId, includedTypes: Set<String>) {
    val project = projectId.findProjectOrNull() ?: return
    RunDashboardManagerProxy.getInstance(project).types = includedTypes
  }

  override suspend fun changeServiceViewImplementationForNextIdeRunAndRestart(shouldEnableSplitImplementation: Boolean) {
    setServiceViewImplementationForNextIdeRun(shouldEnableSplitImplementation)
    ApplicationManagerEx.getApplicationEx().restart(true)
  }
}