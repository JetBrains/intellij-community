// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.aqua.dialog

import com.intellij.feedback.aqua.bundle.AquaFeedbackBundle
import com.intellij.feedback.aqua.state.AquaNewUserFeedbackService
import com.intellij.feedback.common.*
import com.intellij.feedback.common.dialog.BaseFeedbackDialog
import com.intellij.feedback.common.dialog.COMMON_FEEDBACK_SYSTEM_INFO_VERSION
import com.intellij.feedback.common.dialog.CommonFeedbackSystemInfoData
import com.intellij.feedback.common.dialog.showFeedbackSystemInfoDialog
import com.intellij.feedback.common.dialog.uiBlocks.*
import com.intellij.feedback.common.notification.ThanksForFeedbackNotification
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.ui.LicensingFacade
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import kotlinx.serialization.json.*
import javax.swing.Action
import javax.swing.JComponent

class AquaNewUserFeedbackDialog(
  myProject: Project?,
  private val forTest: Boolean
) : BaseFeedbackDialog(myProject) {

  /** Increase the additional number when feedback format is changed */
  override val feedbackJsonVersion = COMMON_FEEDBACK_SYSTEM_INFO_VERSION

  private val TICKET_TITLE_ZENDESK = "Aqua in-IDE Feedback"
  private val FEEDBACK_TYPE_ZENDESK = "Aqua New User in-IDE Feedback"
  override val feedbackReportId = "aqua_new_user_feedback"

  private val systemInfoData: Lazy<CommonFeedbackSystemInfoData> = lazy {
    CommonFeedbackSystemInfoData.getCurrentData()
  }

  private val propertyGraph = PropertyGraph()
  private val dailyTasks: List<ObservableMutableProperty<Boolean>> = List(6) {
    propertyGraph.property(false)
  }
  private val otherDailyTask: ObservableMutableProperty<String> = propertyGraph.property("")
  private val projectTeamSize = propertyGraph.property("")
  private val primaryTestingTargets: List<ObservableMutableProperty<Boolean>> = List(3) {
    propertyGraph.property(false)
  }
  private val otherPrimaryTestingTarget: ObservableMutableProperty<String> = propertyGraph.property("")
  private val textFieldEmailProperty = propertyGraph.lazyProperty { LicensingFacade.INSTANCE?.getLicenseeEmail().orEmpty() }

  private val dailyTasksLabels: List<String> = List(6) {
    AquaFeedbackBundle.message("new.user.dialog.primary.tasks.${it}.label")
  }
  private val projectTeamSizeOptionLabels: List<String> = List(5) {
    AquaFeedbackBundle.message("new.user.dialog.team.size.${it}.label")
  }
  private val primaryTestingTargetLabels: List<String> = List(3) {
    AquaFeedbackBundle.message("new.user.dialog.primary.testing.targets.${it}.label")
  }

  private val jsonConverter = Json { prettyPrint = true }

  private val blocks: List<BaseFeedbackBlock> = listOf(
    TopLabelBlock(AquaFeedbackBundle.message("new.user.dialog.title")),
    DescriptionBlock(AquaFeedbackBundle.message("new.user.dialog.description")),
    CheckBoxGroupBlock(AquaFeedbackBundle.message("new.user.dialog.primary.tasks.group.label"),
                       dailyTasks, dailyTasksLabels, otherDailyTask),
    ComboBoxBlock(projectTeamSize, AquaFeedbackBundle.message("new.user.dialog.team.size.label"),
                  projectTeamSizeOptionLabels,
                  AquaFeedbackBundle.message("new.user.dialog.team.size.bottom.label.left"), myColumnSize = 15),
    CheckBoxGroupBlock(AquaFeedbackBundle.message("new.user.dialog.primary.testing.targets.group.label"),
                       primaryTestingTargets, primaryTestingTargetLabels, otherPrimaryTestingTarget),
    EmailBlock(textFieldEmailProperty, myProject) { showFeedbackSystemInfoDialog(myProject, systemInfoData.value) }
  )

  init {
    init()
    title = AquaFeedbackBundle.message("new.user.dialog.top.title")
  }

  override fun doOKAction() {
    super.doOKAction()
    AquaNewUserFeedbackService.getInstance().state.feedbackSent = true
    val feedbackData = FeedbackRequestDataWithDetailedAnswer(textFieldEmailProperty.get(),
                                                             TICKET_TITLE_ZENDESK,
                                                             createRequestDescription(),
                                                             DEFAULT_FEEDBACK_CONSENT_ID,
                                                             FEEDBACK_TYPE_ZENDESK,
                                                             createCollectedDataJsonString())
    submitFeedback(myProject, feedbackData,
                   { }, { },
                   if (forTest) FeedbackRequestType.TEST_REQUEST else FeedbackRequestType.PRODUCTION_REQUEST,
                   ThanksForFeedbackNotification(description = AquaFeedbackBundle.message(
                     "new.user.notification.thanks.feedback.content")))
  }


  private fun createRequestDescription(): String {
    return buildString {
      appendLine(AquaFeedbackBundle.message("new.user.dialog.title"))
      appendLine(AquaFeedbackBundle.message("new.user.dialog.description"))
      appendLine()
      appendLine(AquaFeedbackBundle.message("new.user.dialog.primary.tasks.group.label"))
      for (i in dailyTasks.indices) {
        appendLine(" ${dailyTasksLabels[i]} - ${dailyTasks[i].get()}")
      }
      appendLine(" Other: ${otherDailyTask.get()}")
      appendLine()
      appendLine(AquaFeedbackBundle.message("new.user.dialog.team.size.label"))
      appendLine(" ${projectTeamSize.get()}")
      appendLine()
      appendLine(AquaFeedbackBundle.message("new.user.dialog.primary.testing.targets.group.label"))
      for (i in primaryTestingTargets.indices) {
        appendLine(" ${primaryTestingTargetLabels[i]} - ${primaryTestingTargets[i].get()}")
      }
      appendLine(" Other: ${otherPrimaryTestingTarget.get()}")
      appendLine()
      appendLine()
      appendLine(systemInfoData.value.toString())
    }
  }

  private fun createCollectedDataJsonString(): JsonObject {
    val collectedData = buildJsonObject {
      put(FEEDBACK_REPORT_ID_KEY, feedbackReportId)
      put("format_version", feedbackJsonVersion)
      put("primary_daily_tasks", buildJsonObject {
        put("automated_test_development_ui", dailyTasks[0].get())
        put("automated_test_development_non_ui", dailyTasks[1].get())
        put("ui_test_development", dailyTasks[2].get())
        put("api_test_development", dailyTasks[3].get())
        put("test_case_design_or_test_management", dailyTasks[4].get())
        put("manual_testing", dailyTasks[5].get())
        put("other", otherDailyTask.get())
      })
      put("project_team_size", projectTeamSize.get())
      put("primary_testing_targets", buildJsonObject {
        put("web_applications", primaryTestingTargets[0].get())
        put("mobile_applications", primaryTestingTargets[1].get())
        put("desktop_applications", primaryTestingTargets[2].get())
        put("other", otherPrimaryTestingTarget.get())
      })
      put("system_info", jsonConverter.encodeToJsonElement(systemInfoData.value))
    }
    return collectedData
  }


  override fun createCenterPanel(): JComponent {
    val mainPanel = panel {
      for (block in blocks) {
        block.addToPanel(this)
      }
    }.also { dialog ->
      dialog.border = JBEmptyBorder(JBUI.scale(15), JBUI.scale(10), JBUI.scale(0), JBUI.scale(10))
    }
    return mainPanel
  }

  override fun getOKAction(): Action {
    val okAction = super.getOKAction()
    okAction.putValue(OkAction.NAME, AquaFeedbackBundle.message("new.user.dialog.ok.label"))
    return okAction
  }

  override fun getCancelAction(): Action {
    val cancelAction = super.getCancelAction()
    cancelAction.putValue(Action.NAME, AquaFeedbackBundle.message("new.user.dialog.cancel.label"))
    return cancelAction
  }

}