// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.backend

import com.intellij.execution.ExecutionManager
import com.intellij.execution.Executor
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.RunContentDescriptorIdImpl
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.dashboard.RunDashboardService
import com.intellij.execution.dashboard.RunDashboardServiceId
import com.intellij.execution.dashboard.actions.ExecutorAction
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.services.ServiceViewDnDDescriptor
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.platform.execution.dashboard.RunDashboardManagerImpl
import com.intellij.platform.execution.dashboard.backend.BackendRunDashboardServiceUtils.getServiceNavigationTarget
import com.intellij.platform.execution.dashboard.splitApi.RunDashboardManagerRpc
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.util.PsiNavigateUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class RunDashboardManagerRpcImpl : RunDashboardManagerRpc {
  override suspend fun reorderConfigurations(projectId: ProjectId, targetId: RunDashboardServiceId, dropId: RunDashboardServiceId, position: String) {
    val project = projectId.findProjectOrNull() ?: return
    val target = RunDashboardManagerImpl.getInstance(project).findServiceById(targetId) ?: return
    val drop = RunDashboardManagerImpl.getInstance(project).findServiceById(dropId) ?: return
    val effectivePosition = ServiceViewDnDDescriptor.Position.valueOf(position)
    BackendRunDashboardServiceUtils.reorderConfigurations(project, target, drop, effectivePosition)
  }

  override suspend fun tryNavigate(projectId: ProjectId, serviceId: RunDashboardServiceId, requestFocus: Boolean) {
    val project = projectId.findProjectOrNull() ?: return
    val backendService = RunDashboardManagerImpl.getInstance(project).findServiceById(serviceId) ?: return

    val targetElement = readAction {
      getServiceNavigationTarget(backendService)
    } ?: return
    withContext(Dispatchers.EDT) {
      PsiNavigateUtil.navigate(targetElement, requestFocus);
    }
  }

  override suspend fun removeService(projectId: ProjectId, serviceId: RunDashboardServiceId) {
    val project = projectId.findProjectOrNull() ?: return
    val backendService = RunDashboardManagerImpl.getInstance(project).findServiceById(serviceId) ?: return

    if (RunManager.getInstance(project).hasSettings(backendService.configurationSettings)) {
      RunManager.getInstance(project).removeConfiguration(backendService.configurationSettings)
    }
  }

  override suspend fun removeFolderGroup(projectId: ProjectId, folderGroupName: String) {
    val project = projectId.findProjectOrNull() ?: return

    val runDashboardManagerImpl = RunDashboardManagerImpl.getInstance(project)
    val services = runDashboardManagerImpl.getRunConfigurations()

    val runManager = RunManagerImpl.getInstanceImpl(project)
    runManager.fireBeginUpdate()
    try {
      for (service in services) {
        val settings = service.getConfigurationSettings()
        if (folderGroupName == settings.getFolderName()) {
          settings.setFolderName(null)
        }
      }
    }
    finally {
      runManager.fireEndUpdate()
    }
  }

  override suspend fun rerunConfiguration(projectId: ProjectId, serviceId: RunDashboardServiceId) {
    val project = projectId.findProjectOrNull() ?: return
    val backendService = RunDashboardManagerImpl.getInstance(project).findServiceById(serviceId) ?: return

    val executor = getExecutor(project, backendService) ?: return

    readAction {
      if (ExecutorAction.canRun(backendService.configurationSettings, null, DumbService.isDumb(project), executor)) {
        // todo split: IMPORTANT check whether its ok to have empty context in here!!
        ExecutorAction.run(backendService.configurationSettings, backendService.descriptor, DataContext.EMPTY_CONTEXT, executor)
      }
    }
  }

  override suspend fun addServiceToFolder(projectId: ProjectId, dropId: RunDashboardServiceId, targetFolderName: String) {
    val project = projectId.findProjectOrNull() ?: return
    val backendService = RunDashboardManagerImpl.getInstance(project).findServiceById(dropId) ?: return

    val runManager = RunManagerImpl.getInstanceImpl(project);
    runManager.fireBeginUpdate();
    try {
      backendService.configurationSettings.setFolderName(targetFolderName);
    }
    finally {
      runManager.fireEndUpdate();
    }
  }

  override suspend fun tryNavigateLink(projectId: ProjectId, link: String, serviceId: RunDashboardServiceId) {
    val project = projectId.findProjectOrNull() ?: return
    RunDashboardManagerImpl.getInstance(project).runCallbackForLink(link, serviceId)
  }

  private fun getExecutor(project: Project, backendService: RunDashboardService): Executor? {
    val descriptor: RunContentDescriptor? = backendService.getDescriptor()
    if (descriptor != null) {
      val executors = ExecutionManager.getInstance(project).getExecutors(descriptor)
      if (executors.isNotEmpty()) return executors.first()
    }
    val configuration: RunConfiguration = backendService.configurationSettings.getConfiguration()
    val runExecutor = DefaultRunExecutor.getRunExecutorInstance()

    val runner: ProgramRunner<*>? = ProgramRunner.getRunner(runExecutor.getId(), configuration)
    if (runner != null) {
      return runExecutor
    }

    val debugExecutor = ExecutorRegistry.getInstance().getExecutorById(ToolWindowId.DEBUG)
    if (debugExecutor != null &&
        ProgramRunner.getRunner(ToolWindowId.DEBUG, configuration) != null) {
      return debugExecutor
    }

    return null
  }

  override suspend fun attachRunContentDescriptorId(projectId: ProjectId, oldDescriptorId: RunContentDescriptorIdImpl?, newDescriptorId: RunContentDescriptorIdImpl) {
    val project = projectId.findProjectOrNull() ?: return
    if (oldDescriptorId != null) {
      RunDashboardManagerImpl.getInstance(project).updateServiceRunContentDescriptor(oldDescriptorId, newDescriptorId)
    }
    else {
      RunDashboardManagerImpl.getInstance(project).attachServiceRunContentDescriptor(newDescriptorId)
    }
  }

  override suspend fun detachRunContentDescriptorId(projectId: ProjectId, descriptorId: RunContentDescriptorIdImpl) {
    val project = projectId.findProjectOrNull() ?: return
    RunDashboardManagerImpl.getInstance(project).detachServiceRunContentDescriptor(descriptorId);
  }

  override suspend fun setConfigurationTypes(projectId: ProjectId, configurationTypes: Set<String>) {
    val project = projectId.findProjectOrNull() ?: return
    RunDashboardManagerImpl.getInstance(project).types = configurationTypes
  }
}