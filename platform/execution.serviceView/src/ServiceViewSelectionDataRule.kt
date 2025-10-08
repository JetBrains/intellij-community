// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView

import com.intellij.execution.services.ServiceViewUIUtils
import com.intellij.openapi.actionSystem.*

class ServiceViewSelectionDataRule : UiDataRule {
  override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
    val project = snapshot[CommonDataKeys.PROJECT] ?: return
    val ids = snapshot[ServiceViewUIUtils.SERVICES_SELECTED_DESCRIPTOR_IDS] ?: return
    sink[CommonDataKeys.NAVIGATABLE_ARRAY] = ids.mapNotNull {
      BackendServiceViewNavigatableProvider.getNavigatable(project, it.contributorId, it.descriptorId)
    }.toTypedArray()
    sink[PlatformCoreDataKeys.SELECTED_ITEMS] = ids.mapNotNull {
      BackendServiceViewSelectedItemProvider.getSelectedItem(project, it.contributorId, it.descriptorId)
    }.toTypedArray()
  }
}