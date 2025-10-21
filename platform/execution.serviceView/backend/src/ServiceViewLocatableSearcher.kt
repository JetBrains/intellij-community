// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView.backend

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ServiceViewLocatableSearcher {
  /**
   * Searches for and returns a list of identifiers (IDs) of services contributed by a specific `ServiceViewContributor`.
   * These services are associated with the specified file in the given project.
   *
   * The returned IDs correspond to those defined in the `ServiceViewDescriptor` of the respective services.
   *
   * @param project The IntelliJ project in which the search is performed.
   * @param virtualFile The file for which the related service identifiers are retrieved.
   *
   * @return A list of service IDs associated with the given file in the corresponding project.
   */
  fun find(project: Project, virtualFile: VirtualFile): List<String>
}