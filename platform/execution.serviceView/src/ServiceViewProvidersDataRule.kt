// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView

import com.intellij.execution.services.*
import com.intellij.openapi.actionSystem.*
import com.intellij.util.containers.ContainerUtil


class ServiceViewProvidersDataRule : UiDataRule {
  override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
    val project = snapshot[PlatformDataKeys.PROJECT] ?: return
    val serviceView = snapshot[ServiceViewActionProvider.SERVICE_VIEW] ?: return
    val selection = snapshot[ServiceViewActionProvider.SERVICES_SELECTED_ITEMS] ?: mutableListOf()
    val onlyItem = ContainerUtil.getOnlyItem(selection)

    val descriptor = if (onlyItem == null || onlyItem.isRemoved) null else onlyItem.getViewDescriptor()
    if (descriptor is UiDataProvider) {
      sink.uiDataSnapshot(descriptor)
    }
    else {
      DataSink.uiDataSnapshot(sink, descriptor?.getDataProvider())
    }

    val contributor = ServiceViewDragHelper.getTheOnlyRootContributor(selection)
    val contributorDescriptor = contributor?.getViewDescriptor(project)
    if (contributorDescriptor is UiDataProvider) {
      sink.uiDataSnapshot(contributorDescriptor)
    }
    else {
      DataSink.uiDataSnapshot(sink, contributorDescriptor?.getDataProvider())
    }

    sink[PlatformDataKeys.COPY_PROVIDER] = ServiceViewCopyProvider(serviceView)
    sink[PlatformDataKeys.DELETE_ELEMENT_PROVIDER] = ServiceViewDefaultDeleteProvider.getInstance()
  }
}