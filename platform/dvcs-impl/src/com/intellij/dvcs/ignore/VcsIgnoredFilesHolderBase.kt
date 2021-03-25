// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.ignore

import com.intellij.dvcs.repo.AbstractRepositoryManager
import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.VcsIgnoredFilesHolder
import com.intellij.openapi.vcs.changes.VcsModifiableDirtyScope

abstract class VcsIgnoredFilesHolderBase<REPOSITORY : Repository>(
  private val repositoryManager: AbstractRepositoryManager<REPOSITORY>
) : VcsIgnoredFilesHolder {

  private val allHolders get() = repositoryManager.repositories.asSequence().map { getHolder(it) }

  protected abstract fun getHolder(repository: REPOSITORY): VcsRepositoryIgnoredFilesHolder

  override fun isInUpdatingMode() = allHolders.any(VcsRepositoryIgnoredFilesHolder::isInUpdateMode)

  override fun cleanAll() = Unit
  override fun cleanAndAdjustScope(scope: VcsModifiableDirtyScope) = Unit

  override fun addFile(file: FilePath) {
    LOG.warn("Attempt to populate vcs-managed ignored files holder with $file", Throwable())
  }

  override fun containsFile(file: FilePath) = findIgnoreHolderByFile(file)?.containsFile(file) ?: false

  override fun values() = allHolders.flatMap { it.ignoredFilePaths }.toList()

  override fun startRescan() {
    allHolders.forEach { it.startRescan() }
  }

  private fun findIgnoreHolderByFile(file: FilePath): VcsRepositoryIgnoredFilesHolder? {
    val repository = repositoryManager.getRepositoryForFileQuick(file) ?: return null
    return getHolder(repository)
  }

  companion object {
    private val LOG = logger<VcsIgnoredFilesHolderBase<*>>()
  }
}