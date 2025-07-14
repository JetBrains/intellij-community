// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.repo.rhizome

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.ui.RepositoryChangesBrowserNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent.Companion.getCurrentBranch
import com.intellij.platform.vcs.impl.shared.rhizome.NodeEntity
import com.intellij.platform.vcs.impl.shared.rhizome.RepositoryNodeEntity
import com.intellij.vcs.branch.BranchPresentation
import com.intellij.vcs.shelf.NodeToEntityConverter
import fleet.kernel.SharedChangeScope

internal class RepositoryNodeToEntityConverter : NodeToEntityConverter<RepositoryChangesBrowserNode>(RepositoryChangesBrowserNode::class) {
  override fun SharedChangeScope.convert(node: RepositoryChangesBrowserNode, orderInParent: Int, project: Project): NodeEntity {
    return RepositoryNodeEntity.new {
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