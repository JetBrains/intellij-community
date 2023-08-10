// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.hints

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.GlobalIndexFilter
import com.intellij.util.indexing.IndexId
import org.jetbrains.annotations.ApiStatus

/**
 * This API is internal, because [com.intellij.util.indexing.GlobalIndexFilter] is internal
 *
 * [BaseGlobalFileTypeInputFilter] accepts all the directories
 */
@ApiStatus.Internal
abstract class BaseGlobalFileTypeInputFilter(val acceptsDirectories: Boolean = true) : GlobalIndexSpecificIndexingHint, GlobalIndexFilter {
  final override fun isExcludedFromIndex(virtualFile: VirtualFile, indexId: IndexId<*, *>, project: Project?): Boolean {
    Logger.getInstance(javaClass).error("Should not be invoked. Please use globalFileTypeHintForIndex instead")
    return false
  }

  final override fun isExcludedFromIndex(virtualFile: VirtualFile, indexId: IndexId<*, *>): Boolean {
    Logger.getInstance(javaClass).error("Project must be provided")
    return false
  }

  final override fun globalInputFilterForIndex(indexId: IndexId<*, *>): FileBasedIndex.InputFilter {
    return if (affectsIndex(indexId)) getFileTypeHintForAffectedIndex(indexId) else AcceptAllRegularFilesIndexingHint
  }

  protected abstract fun getFileTypeHintForAffectedIndex(indexId: IndexId<*, *>): BaseFileTypeInputFilter
}