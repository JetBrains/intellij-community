// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.codereview.Avatar
import com.intellij.collaboration.ui.codereview.details.data.CodeReviewCIJob
import com.intellij.collaboration.ui.codereview.details.data.CodeReviewCIJobState
import com.intellij.collaboration.ui.codereview.details.data.ReviewState
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewStatusViewModel
import com.intellij.collaboration.ui.util.*
import com.intellij.icons.AllIcons
import com.intellij.icons.ExpUiIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.*
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JLabelUtil
import com.intellij.vcsUtil.showAbove
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.Point
import javax.swing.*

object CodeReviewDetailsStatusComponentFactory {
  private const val STATUS_COMPONENT_BORDER = 5
  private const val STATUS_REVIEWER_BORDER = 3
  private const val STATUS_REVIEWER_COMPONENT_GAP = 8

  private const val CI_COMPONENTS_GAP = 8
  private const val CI_COMPONENT_BORDER_TOP_BOTTOM = 4
  private const val CI_COMPONENT_BORDER_LEFT = 8
  private const val CI_COMPONENT_BORDER_RIGHT = 20

  @Suppress("FunctionName")
  fun ReviewDetailsStatusLabel(componentName: String): JLabel =
    JLabel().apply {
      name = componentName
      isOpaque = false
      JLabelUtil.setTrimOverflow(this, trim = true)
    }

  fun createConflictsComponent(scope: CoroutineScope, hasConflicts: Flow<Boolean>): JComponent {
    return ReviewDetailsStatusLabel("Code review status: review has conflicts").apply {
      border = JBUI.Borders.empty(STATUS_COMPONENT_BORDER, 0)
      icon = IconStatus.failed
      text = CollaborationToolsBundle.message("review.details.status.conflicts")
      bindVisibilityIn(scope, hasConflicts)
    }
  }

  fun <T> createNeedReviewerComponent(scope: CoroutineScope, reviewersReview: Flow<Map<T, ReviewState>>): JComponent {
    return ReviewDetailsStatusLabel("Code review status: need reviewer").apply {
      border = JBUI.Borders.empty(STATUS_COMPONENT_BORDER, 0)
      icon = IconStatus.warning
      text = CollaborationToolsBundle.message("review.details.status.reviewer.missing")
      bindVisibilityIn(scope, reviewersReview.map { it.isEmpty() })
    }
  }

  fun createRequiredReviewsComponent(scope: CoroutineScope, requiredApprovingReviewsCount: Flow<Int>, isDraft: Flow<Boolean>): JComponent {
    return ReviewDetailsStatusLabel("Code review status: required reviews").apply {
      border = JBUI.Borders.empty(STATUS_COMPONENT_BORDER, 0)
      icon = IconStatus.failed
      bindVisibilityIn(scope, combine(requiredApprovingReviewsCount, isDraft) { requiredApprovingReviewsCount, isDraft ->
        requiredApprovingReviewsCount > 0 && !isDraft
      })
      bindTextIn(scope, requiredApprovingReviewsCount.map { requiredApprovingReviewsCount ->
        CollaborationToolsBundle.message("review.details.status.reviewer.required", requiredApprovingReviewsCount)
      })
    }
  }

  fun createRequiredResolveConversationsComponent(scope: CoroutineScope, requiredConversationsResolved: Flow<Boolean>): JComponent {
    return ReviewDetailsStatusLabel("Code review status: required conversations resolved").apply {
      border = JBUI.Borders.empty(STATUS_COMPONENT_BORDER, 0)
      icon = IconStatus.failed
      text = CollaborationToolsBundle.message("review.details.status.conversations")
      bindVisibilityIn(scope, requiredConversationsResolved)
    }
  }

  fun createRestrictionComponent(scope: CoroutineScope, isRestricted: Flow<Boolean>, isDraft: Flow<Boolean>): JComponent {
    return ReviewDetailsStatusLabel("Code review status: restricted rights").apply {
      border = JBUI.Borders.empty(STATUS_COMPONENT_BORDER, 0)
      icon = IconStatus.failed
      text = CollaborationToolsBundle.message("review.details.status.not.authorized.to.merge")
      bindVisibilityIn(scope, combine(isRestricted, isDraft) { isRestricted, isDraft ->
        isRestricted && !isDraft
      })
    }
  }

  fun createCiComponent(scope: CoroutineScope, statusVm: CodeReviewStatusViewModel): JComponent {
    val ciJobs = statusVm.ciJobs

    val title = JLabel().apply {
      bindIconIn(scope, ciJobs.map { jobs -> ciJobIcon(jobs) })
      bindTextIn(scope, ciJobs.map { jobs -> ciJobText(jobs) })
    }

    val detailsLink = ActionLink(CollaborationToolsBundle.message("review.details.status.ci.link.details")) {
      statusVm.showJobsDetails()

    }.apply {
      bindVisibilityIn(scope, ciJobs.map { jobs -> !jobs.all { it.status == CodeReviewCIJobState.SUCCESS } })
    }

    scope.launchNow {
      statusVm.showJobsDetailsRequests.collectLatest { jobs ->
        val listModel = CollectionListModel(jobs)
        val jobsList = createJobsList(listModel)
        val scrollPane = ScrollPaneFactory.createScrollPane(jobsList, true).apply {
          horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
          verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
          isOpaque = false
          viewport.isOpaque = false
        }
        JBPopupFactory.getInstance()
          .createComponentPopupBuilder(scrollPane, null)
          .setResizable(true)
          .createPopup()
          .showAbove(detailsLink)
      }
    }

    return HorizontalListPanel(CI_COMPONENTS_GAP).apply {
      name = "Code review status: CI"
      border = JBUI.Borders.empty(STATUS_COMPONENT_BORDER, 0)
      bindVisibilityIn(scope, ciJobs.map { it.isNotEmpty() })

      add(title)
      add(detailsLink)
    }
  }

  fun <Reviewer, IconKey> createReviewersReviewStateComponent(
    scope: CoroutineScope,
    reviewersReview: Flow<Map<Reviewer, ReviewState>>,
    reviewerActionProvider: (Reviewer) -> ActionGroup,
    reviewerNameProvider: (Reviewer) -> String,
    avatarKeyProvider: (Reviewer) -> IconKey,
    iconProvider: (iconKey: IconKey, iconSize: Int) -> Icon,
    statusIconsEnabled: Boolean = true
  ): JComponent {
    val panel = VerticalListPanel().apply {
      name = "Code review status: reviewers"
      bindVisibilityIn(scope, reviewersReview.map { it.isNotEmpty() })
    }

    scope.launch {
      reviewersReview.collect { reviewersReview ->
        panel.removeAll()
        reviewersReview.forEach { (reviewer, reviewState) ->
          panel.add(createReviewerReviewStatus(reviewer, reviewState, reviewerActionProvider, reviewerNameProvider, avatarKeyProvider,
                                               iconProvider, statusIconsEnabled))
        }
        panel.revalidate()
        panel.repaint()
      }
    }

    return panel
  }

  private fun <Reviewer, IconKey> createReviewerReviewStatus(
    reviewer: Reviewer,
    reviewState: ReviewState,
    reviewerActionProvider: (Reviewer) -> ActionGroup,
    reviewerNameProvider: (Reviewer) -> String,
    avatarKeyProvider: (Reviewer) -> IconKey,
    iconProvider: (iconKey: IconKey, iconSize: Int) -> Icon,
    statusIconsEnabled: Boolean
  ): JComponent {
    return HorizontalListPanel(STATUS_REVIEWER_COMPONENT_GAP).apply {
      border = JBUI.Borders.empty(STATUS_REVIEWER_BORDER, 0)
      val reviewerLabel = ReviewDetailsStatusLabel("Code review status: reviewer").apply {
        iconTextGap = STATUS_REVIEWER_COMPONENT_GAP
        icon = iconProvider(avatarKeyProvider(reviewer), Avatar.Sizes.BASE)
        text = ReviewDetailsUIUtil.getReviewStateText(reviewState, reviewerNameProvider(reviewer))
      }

      if (statusIconsEnabled) {
        val reviewStatusIconLabel = JLabel().apply {
          icon = ReviewDetailsUIUtil.getReviewStateIcon(reviewState)
        }
        add(reviewStatusIconLabel)
      }

      add(reviewerLabel)

      if (reviewState != ReviewState.ACCEPTED) {
        PopupHandler.installPopupMenu(this, reviewerActionProvider(reviewer), "CodeReviewReviewerStatus")
      }
    }
  }

  private fun createJobsList(listModel: ListModel<CodeReviewCIJob>): JBList<CodeReviewCIJob> {
    return JBList(listModel).apply {
      val renderer = CodeReviewCIJobRenderer()
      LinkMouseListener(renderer).installOn(this)

      border = JBUI.Borders.empty(CI_COMPONENT_BORDER_TOP_BOTTOM, CI_COMPONENT_BORDER_LEFT,
                                  CI_COMPONENT_BORDER_TOP_BOTTOM, CI_COMPONENT_BORDER_RIGHT)
      visibleRowCount = 7
      cellRenderer = renderer
    }
  }

  private fun ciJobIcon(jobs: List<CodeReviewCIJob>): Icon {
    val failed = jobs.count { it.status == CodeReviewCIJobState.FAILED }
    val pending = jobs.count { it.status == CodeReviewCIJobState.PENDING }
    return when {
      jobs.filter { it.isRequired }.all { it.status == CodeReviewCIJobState.SUCCESS } -> IconStatus.success
      pending != 0 && failed != 0 -> IconStatus.failedInProgress
      pending != 0 -> IconStatus.pending
      else -> IconStatus.failed
    }
  }

  private fun ciJobText(jobs: List<CodeReviewCIJob>): @Nls String {
    val failed = jobs.count { it.status == CodeReviewCIJobState.FAILED }
    val pending = jobs.count { it.status == CodeReviewCIJobState.PENDING }
    return when {
      jobs.filter { it.isRequired }.all { it.status == CodeReviewCIJobState.SUCCESS } -> CollaborationToolsBundle.message("review.details.status.ci.passed")
      pending != 0 && failed != 0 -> CollaborationToolsBundle.message("review.details.status.ci.progress.and.failed")
      pending != 0 -> CollaborationToolsBundle.message("review.details.status.ci.progress")
      else -> CollaborationToolsBundle.message("review.details.status.ci.failed")
    }
  }

  private class CodeReviewCIJobRenderer : ClickableCellRenderer<CodeReviewCIJob> {
    private val component = SimpleColoredComponent().apply {
      iconTextGap = JBUI.scale(4)
      border = JBUI.Borders.empty(CI_COMPONENT_BORDER_TOP_BOTTOM, 0)
    }

    override fun getListCellRendererComponent(list: JList<out CodeReviewCIJob>?,
                                              value: CodeReviewCIJob,
                                              index: Int,
                                              isSelected: Boolean,
                                              cellHasFocus: Boolean): Component {
      component.apply {
        clear()

        if (value.detailsUrl != null) {
          append(value.name, SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, SimpleColoredComponent.BrowserLauncherTag(value.detailsUrl))
        }
        else {
          append(value.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        }
      }
      component.icon = when (value.status) {
        CodeReviewCIJobState.SUCCESS -> IconStatus.success
        CodeReviewCIJobState.PENDING -> IconStatus.pending
        CodeReviewCIJobState.FAILED -> IconStatus.failed
      }

      return component
    }

    override fun getTagAt(point: Point): Any? = component.getFragmentTagAt(point.x)
  }

  private object IconStatus {
    val failed: Icon
      get() = AllIcons.RunConfigurations.TestError

    val failedInProgress: Icon
      get() = AllIcons.Status.FailedInProgress

    val pending: Icon
      get() = AllIcons.RunConfigurations.TestNotRan

    val success: Icon
      get() = if (ExperimentalUI.isNewUI()) ExpUiIcons.Status.Success else AllIcons.RunConfigurations.TestPassed

    val warning: Icon
      get() = AllIcons.General.Warning
  }
}