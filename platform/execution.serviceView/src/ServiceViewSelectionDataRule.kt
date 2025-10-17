// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.platform.execution.serviceView.ServiceViewActionProvider.SERVICES_SELECTED_DESCRIPTOR_IDS
import com.intellij.pom.Navigatable

class ServiceViewSelectionDataRule : UiDataRule {
  override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
    val project = snapshot[CommonDataKeys.PROJECT] ?: return
    val ids = snapshot[SERVICES_SELECTED_DESCRIPTOR_IDS] ?: return
    val navigatables = ids.mapNotNull { it.getNavigatable(project) }.toTypedArray()
    sink.lazy(CommonDataKeys.NAVIGATABLE) { navigatables.firstOrNull() }
    sink.lazy(CommonDataKeys.NAVIGATABLE_ARRAY) { navigatables }
    sink[PlatformCoreDataKeys.SELECTED_ITEMS] = ids.mapNotNull { it.getSelectedItem(project) }.toTypedArray()
  }
}

private fun ServiceViewDescriptorId.getNavigatable(project: Project) : Navigatable? {
  val providerValue = if (contributorId != null && descriptorId != null) {
    BackendServiceViewNavigatableProvider.getNavigatable(project, contributorId, descriptorId)
  }
  else {
    null
  }
  return providerValue ?: localValue?.viewDescriptor?.navigatable
}

private fun ServiceViewDescriptorId.getSelectedItem(project: Project) : Any? {
  val providerValue = if (contributorId != null && descriptorId != null) {
    BackendServiceViewSelectedItemProvider.getSelectedItem(project, contributorId, descriptorId)
  }
  else {
    null
  }
  return providerValue ?: localValue?.value
}