// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.ignore

import com.intellij.dvcs.repo.AbstractRepositoryManager
import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.VcsIgnoredFilesHolder
import com.intellij.openapi.vcs.changes.VcsModifiableDirtyScope

abstract class VcsIgnoredFilesHolderBase<REPOSITORY : Repository>(
  private val repositoryManager: AbstractRepositoryManager<REPOSITORY>
) : VcsIgnoredFilesHolder {

  private val vcsIgnoredHolderMap =
    repositoryManager.repositories.associateTo(
      hashMapOf<REPOSITORY, VcsRepositoryIgnoredFilesHolder>()) { it to getHolder(it) }

  protected abstract fun getHolder(repository: REPOSITORY): VcsRepositoryIgnoredFilesHolder

  override fun isInUpdatingMode() = vcsIgnoredHolderMap.values.any(VcsRepositoryIgnoredFilesHolder::isInUpdateMode)

  override fun cleanAndAdjustScope(scope: VcsModifiableDirtyScope) {}

  override fun addFile(file: FilePath) {
    findIgnoreHolderByFile(file)?.addFile(file)
  }

  override fun containsFile(file: FilePath) = findIgnoreHolderByFile(file)?.containsFile(file) ?: false

  override fun values() = vcsIgnoredHolderMap.flatMap { it.value.ignoredFilePaths }

  override fun startRescan() {
    vcsIgnoredHolderMap.values.forEach { it.startRescan() }
  }

  override fun cleanAll() {
    vcsIgnoredHolderMap.clear()
  }

  private fun findIgnoreHolderByFile(file: FilePath): VcsRepositoryIgnoredFilesHolder? =
    repositoryManager.getRepositoryForFileQuick(file)?.let { repositoryForFile ->
      vcsIgnoredHolderMap[repositoryForFile]
    }
}