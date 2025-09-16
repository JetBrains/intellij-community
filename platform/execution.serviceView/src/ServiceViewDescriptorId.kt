// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView

import com.intellij.ide.CustomDataContextSerializer
import com.intellij.openapi.actionSystem.DataKey
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
internal data class ServiceViewDescriptorId(val contributorId: String, val descriptorId: String)

private class ServiceViewDescriptorIdsSerializer : CustomDataContextSerializer<List<ServiceViewDescriptorId>> {
  override val key: DataKey<List<ServiceViewDescriptorId>> = ServiceViewActionProvider.SERVICES_SELECTED_DESCRIPTOR_IDS
  override val serializer: KSerializer<List<ServiceViewDescriptorId>> = ListSerializer(ServiceViewDescriptorId.serializer())
}