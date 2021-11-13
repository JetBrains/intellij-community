// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.util

import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.module.Module

/**
 * Provides source root paths for applicable build systems.
 * Used to create correct directory paths for nonexistent source root paths.
 */
interface ExternalSystemContentRootContributor {
  fun isApplicable(systemId: String): Boolean
  fun findContentRoots(module: Module, sourceTypes: Collection<ExternalSystemSourceType>): Collection<ExternalContentRoot>

  /**
   * @param path absolute or relative path to a directory
   * @param rootType source type of [path]
   */
  data class ExternalContentRoot(val path: String, val rootType: ExternalSystemSourceType)
}