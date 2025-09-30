// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import org.jetbrains.annotations.ApiStatus

/**
 * Used on backend as a replacement for [ServiceViewDescriptor.getNavigatable], when [ServiceViewContributor] instance is frontend-side.
 */
@ApiStatus.Experimental
interface BackendServiceViewNavigatableProvider {
  companion object {
    private val EP_NAME: ExtensionPointName<BackendServiceViewNavigatableProvider> =
      ExtensionPointName.create("com.intellij.backendServiceViewNavigatableProvider")

    fun getNavigatable(project: Project, contributorId: String, descriptorId: String) : Navigatable? {
      return EP_NAME.findFirstSafe { it.getId() == contributorId }?.getNavigatable(project, descriptorId)
    }
  }

  /**
   * Should match [ServiceViewDescriptor.getId] value of the 'root' [ServiceViewContributor].
   */
  fun getId(): String

  /**
   * @param descriptorId the value returned by [ServiceViewDescriptor.getUniqueId]
   */
  fun getNavigatable(project: Project, descriptorId: String) : Navigatable?
}