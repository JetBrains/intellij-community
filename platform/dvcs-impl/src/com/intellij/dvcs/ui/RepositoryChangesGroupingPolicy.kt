// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.ui

import com.intellij.dvcs.repo.Repository
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyKey
import com.intellij.openapi.vcs.changes.ui.BaseChangesGroupingPolicy
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory
import com.intellij.openapi.vcs.changes.ui.StaticFilePath
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder.*
import javax.swing.tree.DefaultTreeModel

class RepositoryChangesGroupingPolicy(val project: Project, val model: DefaultTreeModel) : BaseChangesGroupingPolicy() {
  private val repositoryManager = VcsRepositoryManager.getInstance(project)

  override fun getParentNodeFor(nodePath: StaticFilePath, subtreeRoot: ChangesBrowserNode<*>): ChangesBrowserNode<*>? {
    val file = resolveVirtualFile(nodePath)
    val nextPolicyParent = nextPolicy?.getParentNodeFor(nodePath, subtreeRoot)

    file?.let { repositoryManager.getRepositoryForFile(it, true) }?.let { repository ->
      val grandParent = nextPolicyParent ?: subtreeRoot
      val cachingRoot = getCachingRoot(grandParent, subtreeRoot)

      REPOSITORY_CACHE.getValue(cachingRoot)[repository]?.let { return it }

      RepositoryChangesBrowserNode(repository).let {
        it.markAsHelperNode()

        model.insertNodeInto(it, grandParent, grandParent.childCount)

        REPOSITORY_CACHE.getValue(cachingRoot)[repository] = it
        IS_CACHING_ROOT.set(it, true)
        DIRECTORY_CACHE.getValue(it)[staticFrom(repository.root).key] = it
        return it
      }
    }

    return nextPolicyParent
  }

  class Factory(val project: Project) : ChangesGroupingPolicyFactory() {
    override fun createGroupingPolicy(model: DefaultTreeModel): RepositoryChangesGroupingPolicy = RepositoryChangesGroupingPolicy(project, model)
  }

  companion object {
    val REPOSITORY_CACHE: NotNullLazyKey<MutableMap<Repository, ChangesBrowserNode<*>>, ChangesBrowserNode<*>> = NotNullLazyKey.create<MutableMap<Repository, ChangesBrowserNode<*>>, ChangesBrowserNode<*>>(
      "ChangesTree.RepositoryCache") { _ -> mutableMapOf() }

  }
}