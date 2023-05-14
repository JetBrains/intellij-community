// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.aqua.dialog

import com.intellij.feedback.aqua.bundle.AquaFeedbackBundle
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

class AquaOldUserFeedbackDialog(
  myProject: Project?,
  private val forTest: Boolean
) : BaseFeedbackDialog(myProject) {

  /** Increase the additional number when feedback format is changed */
  override val feedbackJsonVersion = COMMON_FEEDBACK_SYSTEM_INFO_VERSION

  private val TICKET_TITLE_ZENDESK = "Aqua in-IDE Feedback"
  private val FEEDBACK_TYPE_ZENDESK = "Aqua Old User in-IDE Feedback"
  override val feedbackReportId = "aqua_old_user_feedback"

  private val systemInfoData: Lazy<CommonFeedbackSystemInfoData> = lazy {
    CommonFeedbackSystemInfoData.getCurrentData()
  }

  private val propertyGraph = PropertyGraph()
  private val satisfactionRating: ObservableMutableProperty<Int> = propertyGraph.property(0)
  private val likeMost = propertyGraph.property("")
  private val problemsOrMissingFeatures = propertyGraph.property("")
  private val textFieldEmailProperty = propertyGraph.lazyProperty { LicensingFacade.INSTANCE?.getLicenseeEmail().orEmpty() }

  private val jsonConverter = Json { prettyPrint = true }

  private val blocks: List<FeedbackBlock> = listOf(
    TopLabelBlock(AquaFeedbackBundle.message("old.user.dialog.title")),
    DescriptionBlock(AquaFeedbackBundle.message("old.user.dialog.description")),
    RatingBlock(AquaFeedbackBundle.message("old.user.dialog.satisfaction.label"), "satisfaction"),
    TextAreaBlock(AquaFeedbackBundle.message("old.user.dialog.like_most.label"), "like_most"),
    TextAreaBlock(AquaFeedbackBundle.message("old.user.dialog.problems.label"), "problems_or_missing_features"),
    EmailBlock(myProject) { showFeedbackSystemInfoDialog(myProject, systemInfoData.value) }
  )

  init {
    init()
    title = AquaFeedbackBundle.message("old.user.dialog.top.title")
  }

  override fun doOKAction() {
    super.doOKAction()
    val feedbackData = FeedbackRequestDataWithDetailedAnswer(textFieldEmailProperty.get(),
                                                             TICKET_TITLE_ZENDESK,
                                                             createRequestDescription(),
                                                             DEFAULT_FEEDBACK_CONSENT_ID,
                                                             FEEDBACK_TYPE_ZENDESK,
                                                             createCollectedDataJsonString())
    submitFeedback(feedbackData,
                   { }, { },
                   if (forTest) FeedbackRequestType.TEST_REQUEST else FeedbackRequestType.PRODUCTION_REQUEST)
    ThanksForFeedbackNotification(description = AquaFeedbackBundle.message(
      "old.user.notification.thanks.feedback.content")).notify(myProject)
  }


  private fun createRequestDescription(): String {
    return buildString {
      appendLine(AquaFeedbackBundle.message("old.user.dialog.title"))
      appendLine(AquaFeedbackBundle.message("old.user.dialog.description"))
      appendLine()
      appendLine(AquaFeedbackBundle.message("old.user.dialog.satisfaction.label"))
      appendLine(" ${satisfactionRating.get()}")
      appendLine()
      appendLine(AquaFeedbackBundle.message("old.user.dialog.like_most.label"))
      appendLine(" ${likeMost.get()}")
      appendLine()
      appendLine(AquaFeedbackBundle.message("old.user.dialog.problems.label"))
      appendLine(" ${problemsOrMissingFeatures.get()}")
      appendLine()
      appendLine()
      appendLine(systemInfoData.value.toString())
    }
  }

  private fun createCollectedDataJsonString(): JsonObject {
    val collectedData = buildJsonObject {
      put(FEEDBACK_REPORT_ID_KEY, feedbackReportId)
      put("format_version", feedbackJsonVersion)
      put("satisfaction", satisfactionRating.get())
      put("like_most", likeMost.get())
      put("problems_or_missing_features", problemsOrMissingFeatures.get())
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
    okAction.putValue(OkAction.NAME, AquaFeedbackBundle.message("old.user.dialog.ok.label"))
    return okAction
  }

  override fun getCancelAction(): Action {
    val cancelAction = super.getCancelAction()
    cancelAction.putValue(Action.NAME, AquaFeedbackBundle.message("old.user.dialog.cancel.label"))
    return cancelAction
  }
}