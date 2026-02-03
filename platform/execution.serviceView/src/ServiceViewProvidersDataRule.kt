// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView

import com.intellij.execution.services.ServiceViewDefaultDeleteProvider
import com.intellij.openapi.actionSystem.*

class ServiceViewProvidersDataRule : UiDataRule {
  override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
    val serviceView = snapshot[ServiceViewActionProvider.SERVICE_VIEW] ?: return
    sink[PlatformDataKeys.COPY_PROVIDER] = ServiceViewCopyProvider(serviceView)
    sink[PlatformDataKeys.DELETE_ELEMENT_PROVIDER] = ServiceViewDefaultDeleteProvider.getInstance()
  }
}