// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangesViewModel
import com.intellij.collaboration.ui.codereview.list.search.ChooserPopupUtil
import com.intellij.collaboration.ui.codereview.list.search.PopupConfig
import com.intellij.collaboration.ui.util.bindDisabledIn
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.collaboration.ui.util.bindVisibilityIn
import com.intellij.icons.AllIcons
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.InlineIconButton
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBFont
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.awt.FontMetrics
import java.awt.event.ActionListener
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingConstants

object CodeReviewDetailsCommitsComponentFactory {
  private const val COMPONENTS_GAP = 4
  private const val COMMIT_HASH_OFFSET = 8

  fun <T> create(scope: CoroutineScope, changesVm: CodeReviewChangesViewModel<T>, commitPresenter: (T?) -> CommitPresenter): JComponent {
    val commitsPopupTitle = JLabel().apply {
      font = JBFont.regular().asBold()
      bindTextIn(scope, changesVm.reviewCommits.map { commits ->
        CollaborationToolsBundle.message("review.details.commits.title.text", commits.size)
      })
    }
    val commitsPopup = createCommitChooserActionLink(scope, changesVm, commitPresenter)
    val nextCommitIcon = InlineIconButton(AllIcons.Chooser.Bottom).apply {
      withBackgroundHover = true
      actionListener = ActionListener { changesVm.selectNextCommit() }
      bindVisibilityIn(scope, changesVm.selectedCommit.map { it != null })
      bindDisabledIn(scope, combine(changesVm.selectedCommitIndex, changesVm.reviewCommits) { selectedCommitIndex, commits ->
        selectedCommitIndex == commits.size - 1
      })
    }
    val previousCommitIcon = InlineIconButton(AllIcons.Chooser.Top).apply {
      withBackgroundHover = true
      actionListener = ActionListener { changesVm.selectPreviousCommit() }
      bindVisibilityIn(scope, changesVm.selectedCommit.map { it != null })
      bindDisabledIn(scope, changesVm.selectedCommitIndex.map { it == 0 })
    }

    return HorizontalListPanel(COMPONENTS_GAP).apply {
      add(commitsPopupTitle)
      add(commitsPopup)
      add(nextCommitIcon)
      add(previousCommitIcon)
    }
  }

  private fun <T> createCommitChooserActionLink(
    scope: CoroutineScope,
    changesVm: CodeReviewChangesViewModel<T>,
    commitPresenter: (T?) -> CommitPresenter
  ): JComponent {
    return ActionLink().apply {
      horizontalAlignment = SwingConstants.RIGHT
      setDropDownLinkIcon()
      bindTextIn(scope, combine(changesVm.selectedCommit, changesVm.reviewCommits) { selectedCommit, commits ->
        if (selectedCommit != null) {
          val metrics = getFontMetrics(font)
          val commitHashWidth = calculateCommitHashWidth(metrics, commits, changesVm::commitHash)
          preferredSize = JBDimension(commitHashWidth, preferredSize.height, true)
          return@combine changesVm.commitHash(selectedCommit)
        }
        else {
          preferredSize = null
          return@combine CollaborationToolsBundle.message("review.details.commits.popup.text", commits.size)
        }
      })
      bindDisabledIn(scope, changesVm.reviewCommits.map { commits ->
        commits.size <= 1
      })
      addActionListener(createCommitPopupAction(scope, changesVm, commitPresenter))
    }
  }

  private fun <T> createCommitPopupAction(
    scope: CoroutineScope,
    changesVm: CodeReviewChangesViewModel<T>,
    commitPresenter: (T?) -> CommitPresenter
  ): ActionListener {
    return ActionListener { event ->
      val parentComponent = event.source as? JComponent ?: return@ActionListener
      val point = RelativePoint.getSouthWestOf(parentComponent)
      scope.launch {
        val commits = changesVm.reviewCommits.value
        val selectedCommit = changesVm.selectedCommit.stateIn(this).value
        val popupItems: List<T?> = mutableListOf<T?>(null).apply {
          addAll(commits)
        }
        val chosenCommit = ChooserPopupUtil.showChooserPopup(
          point,
          popupItems,
          filteringMapper = { commit: T? ->
            when (val presentation = commitPresenter(commit)) {
              is CommitPresenter.SingleCommit -> presentation.title
              is CommitPresenter.AllCommits -> CollaborationToolsBundle.message("review.details.commits.popup.all", commits.size)
            }
          },
          renderer = CommitRenderer.createCommitRenderer { commit: T? ->
            SelectableWrapper(commitPresenter(commit), commit == selectedCommit)
          },
          popupConfig = PopupConfig(
            searchTextPlaceHolder = CollaborationToolsBundle.message("review.details.commits.search.placeholder")
          )
        )

        if (chosenCommit == null) changesVm.selectAllCommits() else changesVm.selectCommit(chosenCommit)
      }
    }
  }

  private fun <T> calculateCommitHashWidth(metrics: FontMetrics, commits: List<T>, commitHashConverter: (T) -> String): Int {
    require(commits.isNotEmpty())
    val longestCommitHash = commits.maxOf { commit -> metrics.stringWidth(commitHashConverter(commit)) }
    return longestCommitHash + AllIcons.General.LinkDropTriangle.iconWidth + COMMIT_HASH_OFFSET
  }
}