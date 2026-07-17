// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

/**
 * Allows plugins to reorder and optionally filter candidate files before they are processed concurrently.
 *
 * The returned [Sequence] is consumed lazily. Each yielded list is processed as one sequential batch, while files
 * inside a batch are still processed concurrently by the platform search pipeline.
 *
 * Returning `null` keeps the default platform ordering and candidate set.
 */
@ApiStatus.Internal
interface SearchCandidateBatcher {
  fun batchCandidateFiles(project: Project, queryFiles: List<VirtualFile>, candidateFiles: List<VirtualFile>): Sequence<List<VirtualFile>>?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<SearchCandidateBatcher> = ExtensionPointName.create("com.intellij.searchCandidateBatcher")
  }
}
