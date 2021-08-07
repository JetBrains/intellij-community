// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.ignore

import com.intellij.dvcs.repo.AbstractRepositoryManager
import com.intellij.dvcs.repo.Repository
import com.intellij.dvcs.repo.VcsManagedFilesHolderBase
import com.intellij.openapi.vcs.FilePath

abstract class VcsIgnoredFilesHolderBase<REPOSITORY : Repository>(
  private val repositoryManager: AbstractRepositoryManager<REPOSITORY>
) : VcsManagedFilesHolderBase() {

  private val allHolders get() = repositoryManager.repositories.asSequence().map { getHolder(it) }

  protected abstract fun getHolder(repository: REPOSITORY): VcsRepositoryIgnoredFilesHolder

  override fun isInUpdatingMode() = allHolders.any(VcsRepositoryIgnoredFilesHolder::isInUpdateMode)

  override fun containsFile(file: FilePath): Boolean {
    val repository = repositoryManager.getRepositoryForFileQuick(file) ?: return false
    return getHolder(repository).containsFile(file)
  }

  override fun values() = allHolders.flatMap { it.ignoredFilePaths }.toList()
}