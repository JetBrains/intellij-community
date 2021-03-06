// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.ui

import com.intellij.dvcs.repo.Repository
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.dvcs.ui.RepositoryChangesBrowserNode.Companion.getColorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyKey
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ui.BaseChangesGroupingPolicy
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory
import com.intellij.openapi.vcs.changes.ui.StaticFilePath
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder.*
import com.intellij.vcsUtil.VcsUtil
import java.util.*
import javax.swing.tree.DefaultTreeModel

class RepositoryChangesGroupingPolicy(val project: Project, val model: DefaultTreeModel) : BaseChangesGroupingPolicy() {
  private val repositoryManager = VcsRepositoryManager.getInstance(project)

  override fun getParentNodeFor(nodePath: StaticFilePath, subtreeRoot: ChangesBrowserNode<*>): ChangesBrowserNode<*>? {
    val nextPolicyParent = nextPolicy?.getParentNodeFor(nodePath, subtreeRoot)

    val colorManager = getColorManager(project)
    if (!colorManager.hasMultiplePaths()) return nextPolicyParent

    val filePath = nodePath.filePath
    val repository = getRepositoryFor(filePath)
    if (repository == null || repositoryManager.isExternal(repository)) return nextPolicyParent

    val grandParent = nextPolicyParent ?: subtreeRoot
    val cachingRoot = getCachingRoot(grandParent, subtreeRoot)

    REPOSITORY_CACHE.getValue(cachingRoot)[repository]?.let { return it }

    val repoNode = RepositoryChangesBrowserNode(repository, colorManager)
    repoNode.markAsHelperNode()

    model.insertNodeInto(repoNode, grandParent, grandParent.childCount)

    REPOSITORY_CACHE.getValue(cachingRoot)[repository] = repoNode
    IS_CACHING_ROOT.set(repoNode, true)
    DIRECTORY_CACHE.getValue(repoNode)[staticFrom(repository.root).key] = repoNode
    return repoNode
  }

  private fun getRepositoryFor(filePath: FilePath): Repository? {
    val repository = repositoryManager.getRepositoryForFile(filePath, true)

    // assign submodule change to the parent repository
    if (repository != null &&
        !repository.vcs.areDirectoriesVersionedItems() &&
        Objects.equals(repository.root.path, filePath.path)) {
      val parentRepo = repositoryManager.getRepositoryForFile(repository.root.parent, true)
      if (parentRepo != null) return parentRepo
    }

    return repository
  }

  internal class Factory : ChangesGroupingPolicyFactory() {
    override fun createGroupingPolicy(project: Project, model: DefaultTreeModel) = RepositoryChangesGroupingPolicy(project, model)
  }

  companion object {
    val REPOSITORY_CACHE = NotNullLazyKey.create<MutableMap<Repository, ChangesBrowserNode<*>>, ChangesBrowserNode<*>>("ChangesTree.RepositoryCache") { mutableMapOf() }
  }
}