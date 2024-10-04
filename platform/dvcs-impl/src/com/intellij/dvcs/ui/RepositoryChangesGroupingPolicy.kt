// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.ui

import com.intellij.dvcs.repo.Repository
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.dvcs.ui.RepositoryChangesBrowserNode.Companion.getColorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory
import com.intellij.openapi.vcs.changes.ui.SimpleChangesGroupingPolicy
import com.intellij.openapi.vcs.changes.ui.StaticFilePath
import org.jetbrains.annotations.ApiStatus
import javax.swing.tree.DefaultTreeModel

@ApiStatus.Internal
class RepositoryChangesGroupingPolicy(val project: Project, model: DefaultTreeModel) : SimpleChangesGroupingPolicy<Repository>(model) {
  private val repositoryManager = VcsRepositoryManager.getInstance(project)
  private val colorManager = getColorManager(project)

  override fun getGroupRootValueFor(nodePath: StaticFilePath, node: ChangesBrowserNode<*>): Repository? {
    if (!colorManager.hasMultiplePaths()) return null

    val filePath = nodePath.filePath
    val repository = getRepositoryFor(filePath) ?: return null
    if (repositoryManager.isExternal(repository)) return null

    return repository
  }

  override fun createGroupRootNode(value: Repository): ChangesBrowserNode<*> {
    val repoNode = RepositoryChangesBrowserNode(value, colorManager)
    repoNode.markAsHelperNode()
    return repoNode
  }

  private fun getRepositoryFor(filePath: FilePath): Repository? {
    val repository = repositoryManager.getRepositoryForFile(filePath, true)

    // Assign submodule change to the parent repository.
    // We can't check for 'FilePath.isDirectory' here, as git4idea is passing 'false' for submodule changes.
    if (repository != null &&
        !repository.vcs.areDirectoriesVersionedItems() &&
        repositoryManager.getRepositoryForRootQuick(filePath) == repository) {
      val parentRepo = repositoryManager.getRepositoryForFile(repository.root.parent, true)
      if (parentRepo != null) return parentRepo
    }

    return repository
  }

  internal class Factory : ChangesGroupingPolicyFactory() {
    override fun createGroupingPolicy(project: Project, model: DefaultTreeModel) = RepositoryChangesGroupingPolicy(project, model)
  }
}