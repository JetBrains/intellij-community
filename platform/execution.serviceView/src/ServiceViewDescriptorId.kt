// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("EXPOSED_PACKAGE_PRIVATE_TYPE_FROM_INTERNAL_WARNING")

package com.intellij.platform.execution.serviceView

import com.intellij.openapi.project.Project
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
internal data class ServiceViewDescriptorId(
  @Transient val localValue: ServiceModel.ServiceViewItem? = null,
  val contributorId: String?,
  val descriptorId: String?,
)

internal fun ServiceModel.ServiceViewItem.toId(project: Project)
  = ServiceViewDescriptorId(this, this.rootContributor.getViewDescriptor(project).id, getViewDescriptor().uniqueId)