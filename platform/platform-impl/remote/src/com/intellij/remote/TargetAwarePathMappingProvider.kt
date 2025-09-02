// Copyright 2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote

import com.intellij.execution.target.TargetBasedSdkAdditionalData
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.util.PathMappingSettings
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Unmodifiable


/**
 * Provides path mapping settings for targets-based SDKs.
 *
 * This abstract class is intended to be extended by implementations that provide
 * path mappings from various sources relevant to the SDK or the project in question.
 */
@ApiStatus.Internal
abstract class TargetAwarePathMappingProvider {
  /**
   * Allows a provider to determine whether it is applicable for the provided SDK additional data.
   *
   * @param data the instance of TargetBasedSdkAdditionalData to be checked.
   * @return true if the provider is applicable, false otherwise
   */
  abstract fun accepts(data: TargetBasedSdkAdditionalData): Boolean

  /**
   * Retrieves the path mapping settings for a given project and target-based SDK additional data.
   *
   * This method is intended to be implemented in a subclass to provide specific path mapping
   * settings based on the provided project and SDK additional data.
   *
   * @param project the project for which the path mapping settings are requested
   * @param data the target-based SDK additional data that contains information required for computing path mappings
   * @return the path mapping settings for the given project and SDK additional data
   */
  abstract fun getPathMappingSettings(project: Project, data: TargetBasedSdkAdditionalData): PathMappingSettings

  companion object {
    val EP_NAME: ExtensionPointName<TargetAwarePathMappingProvider> = ExtensionPointName.create<TargetAwarePathMappingProvider>("com.intellij.remote.targetAwarePathMappingProvider")

    /**
     * Fetches a list of mapping providers that are suitable based on the provided target-based SDK additional data.
     *
     * Filters the available path mapping providers and returns only those that are applicable
     * to the given target environment configuration.
     *
     * @param data the target-based SDK additional data used to determine the applicable mapping providers
     * @return a list of mapping providers that accept the specified target-based SDK additional data
     */
    fun getSuitableMappingProviders(data: TargetBasedSdkAdditionalData): @Unmodifiable List<TargetAwarePathMappingProvider> {
      return EP_NAME.extensionList.filter { it.accepts(data) }
    }
  }
}
