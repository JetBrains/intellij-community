// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.ui

import com.intellij.dvcs.DvcsUtil.getShortRepositoryName
import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent.Companion.TEXT_COLOR
import com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent.Companion.getBranchPresentationBackground
import com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent.Companion.getCurrentBranch
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES
import com.intellij.ui.SimpleTextAttributes.STYLE_OPAQUE
import com.intellij.util.FontUtil.spaceAndThinSpace
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.JBUI.insets
import com.intellij.util.ui.JBUI.scale
import com.intellij.util.ui.UIUtil.getTreeBackground
import com.intellij.vcs.branch.BranchPresentation.getPresentableText
import com.intellij.vcs.branch.BranchPresentation.getSingleTooltip
import com.intellij.vcs.log.impl.VcsLogManager.findLogProviders
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.ui.VcsLogColorManager
import com.intellij.vcs.log.ui.VcsLogColorManagerImpl
import java.awt.Color

private val ICON_SIZE = scale(14)
private val BRANCH_BACKGROUND_INSETS = insets(1, 0)

private fun getBranchLabelAttributes(background: Color) =
  SimpleTextAttributes(getBranchPresentationBackground(background), TEXT_COLOR, null, STYLE_OPAQUE)

open class RepositoryChangesBrowserNode(repository: Repository,
                                        private val colorManager: VcsLogColorManager = getColorManager(repository.project))
  : ChangesBrowserNode<Repository>(repository) {

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

  companion object {
    fun getColorManager(project: Project): VcsLogColorManagerImpl = VcsProjectLog.getInstance(project).logManager?.colorManager ?: VcsLogColorManagerImpl(
      findLogProviders(ProjectLevelVcsManager.getInstance(project).allVcsRoots.asList(), project).keys)

    fun getRepositoryIcon(repository: Repository, colorManager: VcsLogColorManager = getColorManager(repository.project)) =
      ColorIcon(ICON_SIZE, VcsLogColorManagerImpl.getBackgroundColor(colorManager.getRootColor(repository.root)))
  }
}
