package com.intellij.platform.execution.dashboard.backend

import com.intellij.execution.dashboard.RunDashboardService
import com.intellij.execution.dashboard.RunDashboardServiceId
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.platform.execution.dashboard.RunDashboardManagerImpl
import com.intellij.platform.execution.serviceView.BackendServiceViewSelectedItemProvider
import com.intellij.platform.ide.productMode.IdeProductMode.Companion.isMonolith

internal class BackendRunDashboardSelectedItemProvider : BackendServiceViewSelectedItemProvider {
  override fun getId(): String = "Run Dashboard"

  override fun getSelectedItem(project: Project, descriptorId: String): RunDashboardService? {
    if (isMonolith) return null

    val serviceId = deserializeDescriptorId(descriptorId) ?: return null
    return RunDashboardManagerImpl.getInstance(project).findServiceById(serviceId)
  }

  private fun deserializeDescriptorId(descriptorId: String): RunDashboardServiceId? {
    val backendServiceId = descriptorId.toIntOrNull()
    if (backendServiceId == null) {
      thisLogger().warn("Cannot parse descriptorId $descriptorId")
      return null
    }
    return RunDashboardServiceId(backendServiceId)
  }
}