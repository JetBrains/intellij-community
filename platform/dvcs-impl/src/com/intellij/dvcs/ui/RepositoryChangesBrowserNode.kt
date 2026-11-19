// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.ui

import com.intellij.dvcs.DvcsUtil.getShortRepositoryName
import com.intellij.dvcs.repo.Repository
import com.intellij.dvcs.repo.repositoryId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent.Companion.getCurrentBranch
import com.intellij.openapi.vcs.changes.ui.RepositoryChangesBrowserNodeBase
import com.intellij.vcs.branch.BranchData
import com.intellij.vcs.branch.BranchPresentation.getPresentableText
import com.intellij.vcs.branch.BranchPresentation.getSingleTooltip
import com.intellij.vcs.log.impl.VcsLogManager
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.ui.VcsLogColorManager
import com.intellij.vcs.log.ui.VcsLogColorManagerFactory
import com.intellij.vcsUtil.VcsUtil
import javax.swing.Icon

open class RepositoryChangesBrowserNode(repository: Repository) : RepositoryChangesBrowserNodeBase<Repository, BranchData>(repository) {
  private val iconsProvider = VcsRepositoryIconsProvider.getInstance(repository.project)

  @Deprecated("The node takes its color from VcsRepositoryIconsProvider", ReplaceWith("RepositoryChangesBrowserNode(repository)"))
  constructor(repository: Repository, @Suppress("unused") colorManager: VcsLogColorManager) : this(repository)

  override fun getIcon(): Icon = iconsProvider.getIcon(getUserObject().repositoryId())

  final override fun getCurrentBranch(repository: Repository): BranchData? = getCurrentBranch(repository.project, repository.root)

  final override fun getBranchText(branch: BranchData): String = getPresentableText(branch)

  final override fun getBranchTooltipText(branch: BranchData): String = getSingleTooltip(branch).orEmpty()

  override fun compareUserObjects(o2: Repository): Int =
    compareFileNames(getShortRepositoryName(getUserObject()), getShortRepositoryName(o2))

  override fun getTextPresentation(): String = getShortRepositoryName(getUserObject())

  override fun getNodeFilePath(): FilePath = VcsUtil.getFilePath(getUserObject().root)

  companion object {
    fun getColorManager(project: Project): VcsLogColorManager {
      val colorManager = VcsProjectLog.getInstance(project).logManager?.colorManager
      if (colorManager != null) return colorManager

      val roots = VcsLogManager.findLogProviders(ProjectLevelVcsManager.getInstance(project).getAllVcsRoots().asList(), project).keys
      return VcsLogColorManagerFactory.create(roots)
    }
  }
}
