// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView

import com.intellij.ide.CustomDataContextSerializer
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.platform.execution.serviceView.ServiceViewActionProvider.SERVICES_SELECTED_DESCRIPTOR_IDS
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer

internal class ServiceViewDescriptorIdsSerializer() : CustomDataContextSerializer<List<ServiceViewDescriptorId>> {
  override val key: DataKey<List<ServiceViewDescriptorId>> = SERVICES_SELECTED_DESCRIPTOR_IDS
  override val serializer: KSerializer<List<ServiceViewDescriptorId>> = ListSerializer(ServiceViewDescriptorId.serializer())
}