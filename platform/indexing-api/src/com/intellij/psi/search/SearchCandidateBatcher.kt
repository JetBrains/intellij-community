// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

/**
 * Allows plugins to split candidate files into sequential batches before each batch is processed concurrently.
 *
 * Returning `null` keeps the default platform ordering.
 */
@ApiStatus.Internal
interface SearchCandidateBatcher {
  fun batchCandidateFiles(project: Project, queryFiles: List<VirtualFile>, candidateFiles: List<VirtualFile>): Sequence<List<VirtualFile>>?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<SearchCandidateBatcher> = ExtensionPointName.create("com.intellij.searchCandidateBatcher")
  }
}
