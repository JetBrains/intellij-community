// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.ui

import com.intellij.dvcs.repo.Repository
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyKey
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicy
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory
import com.intellij.openapi.vcs.changes.ui.StaticFilePath
import javax.swing.tree.DefaultTreeModel

class RepositoryChangesGroupingPolicy(val project: Project, val model: DefaultTreeModel) : ChangesGroupingPolicy {
  private val repositoryManager = VcsRepositoryManager.getInstance(project)

  override fun getParentNodeFor(nodePath: StaticFilePath, subtreeRoot: ChangesBrowserNode<*>): ChangesBrowserNode<*>? {
    val file = generateSequence(nodePath) { it.parent }.mapNotNull { it.resolve() }.firstOrNull()

    file?.let(repositoryManager::getRepositoryForFile)?.let { repository ->
      REPOSITORY_CACHE.getValue(subtreeRoot)[repository]?.let { return it }

      RepositoryChangesBrowserNode(repository).let {
        it.markAsHelperNode()

        model.insertNodeInto(it, subtreeRoot, subtreeRoot.childCount)
        REPOSITORY_CACHE.getValue(subtreeRoot)[repository] = it
        return it
      }
    }

    return null
  }

  class Factory(val project: Project) : ChangesGroupingPolicyFactory() {
    override fun createGroupingPolicy(model: DefaultTreeModel) = RepositoryChangesGroupingPolicy(project, model)
  }

  companion object {
    val REPOSITORY_CACHE = NotNullLazyKey.create<MutableMap<Repository, ChangesBrowserNode<*>>, ChangesBrowserNode<*>>(
      "ChangesTree.RepositoryCache") { _ -> mutableMapOf() }

  }
}