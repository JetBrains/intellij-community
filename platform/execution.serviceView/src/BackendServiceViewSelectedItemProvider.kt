// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView

import com.intellij.execution.services.ServiceViewContributor
import com.intellij.execution.services.ServiceViewProvidingContributor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Used on backend as a replacement for [ServiceViewContributor] (or [ServiceViewProvidingContributor.asService]),
 * when [ServiceViewContributor] instance is frontend-side.
 */
@ApiStatus.Experimental
interface BackendServiceViewSelectedItemProvider {
  companion object {
    private val EP_NAME: ExtensionPointName<BackendServiceViewSelectedItemProvider> =
      ExtensionPointName.create("com.intellij.backendServiceViewSelectedItemProvider")

    fun getSelectedItem(project: Project, contributorId: String, descriptorId: String) : Any? {
      return EP_NAME.findFirstSafe { it.getId() == contributorId }?.getSelectedItem(project, descriptorId)
    }
  }

  /**
   * Should match [ServiceViewDescriptor.getId] value of the 'root' [ServiceViewContributor].
   */
  fun getId(): String

  /**
   * @param descriptorId the value returned by [ServiceViewDescriptor.getUniqueId]
   */
  fun getSelectedItem(project: Project, descriptorId: String) : Any?
}