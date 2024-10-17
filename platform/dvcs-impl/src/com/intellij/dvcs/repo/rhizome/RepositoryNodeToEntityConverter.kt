// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.repo.rhizome

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.ui.RepositoryChangesBrowserNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent.Companion.getCurrentBranch
import com.intellij.platform.kernel.withKernel
import com.intellij.vcs.branch.BranchPresentation
import com.intellij.vcs.impl.backend.shelf.NodeToEntityConverter
import com.intellij.vcs.impl.backend.shelf.ShelfTree
import com.intellij.vcs.impl.shared.rhizome.NodeEntity
import com.intellij.vcs.impl.shared.rhizome.RepositoryNodeEntity
import fleet.kernel.change
import fleet.kernel.shared

internal class RepositoryNodeToEntityConverter : NodeToEntityConverter<RepositoryChangesBrowserNode, RepositoryNodeEntity>(RepositoryChangesBrowserNode::class) {
  override suspend fun convert(node: RepositoryChangesBrowserNode, tree: ShelfTree, orderInParent: Int, project: Project): RepositoryNodeEntity {
    return withKernel {
      change {
        shared {
          RepositoryNodeEntity.new {
            val repository = node.userObject
            val currentBranch = getCurrentBranch(project, repository.root)
            val color = RepositoryChangesBrowserNode.getColorManager(project).getRootColor(repository.root)
            it[RepositoryNodeEntity.Name] = DvcsUtil.getShortRepositoryName(repository)
            if (currentBranch != null) {
              it[RepositoryNodeEntity.BranchName] = BranchPresentation.getPresentableText(currentBranch)
              it[RepositoryNodeEntity.ToolTip] = BranchPresentation.getSingleTooltip(currentBranch)
            }
            it[RepositoryNodeEntity.ColorRed] = color.red
            it[RepositoryNodeEntity.ColorGreen] = color.green
            it[RepositoryNodeEntity.ColorBlue] = color.blue
            it[NodeEntity.Order] = orderInParent
          }
        }
      }
    }
  }
}