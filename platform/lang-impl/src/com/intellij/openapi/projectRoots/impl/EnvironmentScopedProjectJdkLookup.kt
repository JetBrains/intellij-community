// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.eel.EelDescriptor
import org.jetbrains.annotations.ApiStatus

/**
 * Provides environment-aware SDK lookup operations for optimized SDK resolution across isolated environments.
 *
 * This interface enables efficient SDK lookups that are scoped to specific environments (such as local, WSL,
 * Docker containers, etc.) without requiring retrieval and filtering of all available SDKs. Implementations
 * should leverage environment-specific SDK storage to avoid unnecessary cross-environment operations.
 *
 * The primary benefit over using [com.intellij.openapi.projectRoots.ProjectJdkTable.getAllJdks] and manual
 * filtering is performance optimization: instead of retrieving all SDKs from all environments and then
 * filtering by environment, this interface allows direct lookup within the target environment's SDK
 * namespace.
 *
 * @see com.intellij.openapi.projectRoots.ProjectJdkTable
 * @see EelDescriptor
 */
@ApiStatus.Internal
interface EnvironmentScopedProjectJdkLookup {
  fun findJdk(name: String, eelDescriptor: EelDescriptor): Sdk?

  fun findJdk(name: String, type: String, eelDescriptor: EelDescriptor): Sdk?
}