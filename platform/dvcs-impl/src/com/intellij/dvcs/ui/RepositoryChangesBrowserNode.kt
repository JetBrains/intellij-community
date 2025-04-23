// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.ui

import com.intellij.dvcs.DvcsUtil.getShortRepositoryName
import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent.Companion.TEXT_COLOR
import com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent.Companion.getBranchPresentationBackground
import com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent.Companion.getCurrentBranch
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES
import com.intellij.ui.SimpleTextAttributes.STYLE_OPAQUE
import com.intellij.util.FontUtil.spaceAndThinSpace
import com.intellij.util.ui.CheckboxIcon
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.JBUI.insets
import com.intellij.util.ui.UIUtil.getTreeBackground
import com.intellij.vcs.branch.BranchPresentation.getPresentableText
import com.intellij.vcs.branch.BranchPresentation.getSingleTooltip
import com.intellij.vcs.log.impl.VcsLogManager.findLogProviders
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.ui.VcsLogColorManager
import com.intellij.vcs.log.ui.VcsLogColorManagerFactory
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Color

private val BRANCH_BACKGROUND_INSETS = insets(1, 0)

private fun getBranchLabelAttributes(background: Color) =
  SimpleTextAttributes(getBranchPresentationBackground(background), TEXT_COLOR, null, STYLE_OPAQUE)

open class RepositoryChangesBrowserNode(repository: Repository,
                                        private val colorManager: VcsLogColorManager = getColorManager(repository.project))
  : ChangesBrowserNode<Repository>(repository), ChangesBrowserNode.NodeWithFilePath {

  override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    renderer.icon = getRepositoryIcon(getUserObject(), colorManager)
    renderer.append(" $textPresentation", REGULAR_ATTRIBUTES)
    appendCount(renderer)

    if (renderer.isShowingLocalChanges) {
      appendCurrentBranch(renderer)
    }
  }

  private fun appendCurrentBranch(renderer: ChangesBrowserNodeRenderer) {
    val repository = getUserObject()
    val branch = getCurrentBranch(repository.project, repository.root)

    if (branch != null) {
      renderer.append(spaceAndThinSpace())
      renderer.append(" ${getPresentableText(branch)} ", getBranchLabelAttributes(renderer.background ?: getTreeBackground()))
      renderer.setBackgroundInsets(BRANCH_BACKGROUND_INSETS)
      renderer.toolTipText = getSingleTooltip(branch)
    }
  }

  override fun getSortWeight(): Int = REPOSITORY_SORT_WEIGHT

  override fun compareUserObjects(o2: Repository): Int =
    compareFileNames(getShortRepositoryName(getUserObject()), getShortRepositoryName(o2))

  override fun getTextPresentation(): String = getShortRepositoryName(getUserObject())

  override fun getNodeFilePath(): FilePath = VcsUtil.getFilePath(getUserObject().root)

  companion object {
    fun getColorManager(project: Project): VcsLogColorManager {
      val colorManager = VcsProjectLog.getInstance(project).logManager?.colorManager
      if (colorManager != null) return colorManager

      val roots = findLogProviders(ProjectLevelVcsManager.getInstance(project).allVcsRoots.asList(), project).keys
      return VcsLogColorManagerFactory.create(roots)
    }

    fun getRepositoryIcon(repository: Repository, colorManager: VcsLogColorManager = getColorManager(repository.project)) =
      getRepositoryIcon(colorManager, repository.root)

    @ApiStatus.Internal
    fun getRepositoryIcon(colorManager: VcsLogColorManager, root: VirtualFile): ColorIcon =
      CheckboxIcon.createAndScale(colorManager.getRootColor(root))
  }
}
