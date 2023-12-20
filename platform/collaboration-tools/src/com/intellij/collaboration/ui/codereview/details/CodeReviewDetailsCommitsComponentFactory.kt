// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangesViewModel
import com.intellij.collaboration.ui.util.bindDisabledIn
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.collaboration.ui.util.bindVisibilityIn
import com.intellij.collaboration.ui.util.popup.ChooserPopupUtil
import com.intellij.collaboration.ui.util.popup.PopupConfig
import com.intellij.icons.AllIcons
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.InlineIconButton
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBFont
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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

  fun <T> create(scope: CoroutineScope, changesVm: CodeReviewChangesViewModel<T>,
                 commitPresentation: (commit: T) -> CommitPresentation): JComponent {
    val commitsPopupTitle = JLabel().apply {
      font = JBFont.regular().asBold()
      bindTextIn(scope, changesVm.reviewCommits.map { commits ->
        CollaborationToolsBundle.message("review.details.commits.title.text", commits.size)
      })
    }
    val commitsPopup = scope.createCommitChooserActionLink(changesVm, commitPresentation)
    val nextPrevVisibilityFlow = combine(changesVm.selectedCommitIndex, changesVm.reviewCommits) { selectedCommitIndex, commits ->
      commits.size > 1 && selectedCommitIndex >= 0
    }
    val nextCommitIcon = InlineIconButton(AllIcons.Chooser.Bottom).apply {
      withBackgroundHover = true
      actionListener = ActionListener { changesVm.selectNextCommit() }
      isVisible = false
      bindVisibilityIn(scope, nextPrevVisibilityFlow)
      bindDisabledIn(scope, combine(changesVm.selectedCommitIndex, changesVm.reviewCommits) { selectedCommitIndex, commits ->
        selectedCommitIndex == commits.size - 1
      })
    }
    val previousCommitIcon = InlineIconButton(AllIcons.Chooser.Top).apply {
      withBackgroundHover = true
      actionListener = ActionListener { changesVm.selectPreviousCommit() }
      isVisible = false
      bindVisibilityIn(scope, nextPrevVisibilityFlow)
      bindDisabledIn(scope, changesVm.selectedCommitIndex.map { it == 0 })
    }

    return HorizontalListPanel(COMPONENTS_GAP).apply {
      add(commitsPopupTitle)
      add(commitsPopup)
      add(nextCommitIcon)
      add(previousCommitIcon)
    }
  }

  private fun <T> CoroutineScope.createCommitChooserActionLink(
    changesVm: CodeReviewChangesViewModel<T>,
    commitPresentation: (commit: T) -> CommitPresentation
  ): JComponent {
    val scope = this
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
      addActionListener(scope.createCommitPopupAction(changesVm, commitPresentation))
    }
  }

  private fun <T> CoroutineScope.createCommitPopupAction(
    changesVm: CodeReviewChangesViewModel<T>,
    commitPresentation: (commit: T) -> CommitPresentation
  ): ActionListener {
    return ActionListener { event ->
      val parentComponent = event.source as? JComponent ?: return@ActionListener
      val point = RelativePoint.getSouthWestOf(parentComponent)
      launch {
        val commits = changesVm.reviewCommits.first()
        val commitsCount = commits.size
        val selectedCommit = changesVm.selectedCommit.stateIn(this).value
        val popupItems: List<T?> = mutableListOf<T?>(null).apply {
          addAll(commits)
        }
        val chosenCommit = ChooserPopupUtil.showChooserPopup(
          point,
          popupItems,
          filteringMapper = { commit: T? ->
            if (commit == null) {
              CollaborationToolsBundle.message("review.details.commits.popup.all", commitsCount)
            }
            else {
              commitPresentation(commit).titleHtml
            }
          },
          renderer = CommitRenderer.createCommitRenderer(commitsCount) { commit: T? ->
            SelectableWrapper(commit?.let(commitPresentation), commit == selectedCommit)
          },
          popupConfig = PopupConfig(
            searchTextPlaceHolder = CollaborationToolsBundle.message("review.details.commits.search.placeholder")
          )
        )
        val index = chosenCommit?.let(commits::indexOf) ?: -1
        changesVm.selectCommit(index)
      }
    }
  }

  private fun <T> calculateCommitHashWidth(metrics: FontMetrics, commits: List<T>, commitHashConverter: (T) -> String): Int {
    require(commits.isNotEmpty())
    val longestCommitHash = commits.maxOf { commit -> metrics.stringWidth(commitHashConverter(commit)) }
    return longestCommitHash + AllIcons.General.LinkDropTriangle.iconWidth + COMMIT_HASH_OFFSET
  }
}