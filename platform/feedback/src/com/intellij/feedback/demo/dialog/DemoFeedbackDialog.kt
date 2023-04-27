// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.demo.dialog

import com.intellij.feedback.common.DEFAULT_FEEDBACK_CONSENT_ID
import com.intellij.feedback.common.FeedbackRequestDataWithDetailedAnswer
import com.intellij.feedback.common.FeedbackRequestType
import com.intellij.feedback.common.dialog.BaseFeedbackDialog
import com.intellij.feedback.common.dialog.COMMON_FEEDBACK_SYSTEM_INFO_VERSION
import com.intellij.feedback.common.dialog.CommonFeedbackSystemInfoData
import com.intellij.feedback.common.dialog.showFeedbackSystemInfoDialog
import com.intellij.feedback.common.dialog.uiBlocks.*
import com.intellij.feedback.common.submitFeedback
import com.intellij.feedback.demo.bundle.DemoFeedbackBundle
import com.intellij.feedback.new_ui.CancelFeedbackNotification
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.ui.LicensingFacade
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import javax.swing.Action
import javax.swing.JComponent

class DemoFeedbackDialog(
  project: Project?,
  private val forTest: Boolean,
) : BaseFeedbackDialog(project) {

  /** Increase the additional number when feedback format is changed */
  override val feedbackJsonVersion: Int = COMMON_FEEDBACK_SYSTEM_INFO_VERSION + 1
  override val feedbackReportId: String = "demo_feedback"

  private val TICKET_TITLE_ZENDESK = "Demo in-IDE Feedback"
  private val FEEDBACK_TYPE_ZENDESK = "Demo in-IDE Feedback"

  private val systemInfoData: CommonFeedbackSystemInfoData = CommonFeedbackSystemInfoData.getCurrentData()

  private val propertyGraph = PropertyGraph()
  private val textFieldEmailProperty = propertyGraph.lazyProperty { LicensingFacade.INSTANCE?.getLicenseeEmail().orEmpty() }

  private val jsonConverter = Json { prettyPrint = true }
  
  private val blocks: List<BaseFeedbackBlock> = listOf(
    TopLabelBlock(DemoFeedbackBundle.message("dialog.title")),
    TextAreaBlock(propertyGraph.property(""), DemoFeedbackBundle.message("dialog.textarea.label")),
    SegmentedButtonBlock(propertyGraph.property(0),
                         DemoFeedbackBundle.message("dialog.segmentedButton.label"),
                                   (1..9).toList(),
                         { it.toString() },
                         DemoFeedbackBundle.message("dialog.segmentedButton.leftHint"),
                         DemoFeedbackBundle.message("dialog.segmentedButton.middleHint"),
                         DemoFeedbackBundle.message("dialog.segmentedButton.rightHint")),
    EmailBlock(textFieldEmailProperty, myProject) { showFeedbackSystemInfoDialog(myProject, systemInfoData) }
  )

  init {
    init()
    title = DemoFeedbackBundle.message("dialog.top.title")
  }

  override fun doOKAction() {
    super.doOKAction()
    val feedbackData = FeedbackRequestDataWithDetailedAnswer(feedbackReportId, TICKET_TITLE_ZENDESK, createRequestDescription(),
                                                             DEFAULT_FEEDBACK_CONSENT_ID, FEEDBACK_TYPE_ZENDESK, createCollectedDataJsonObject())
    submitFeedback(myProject, 
                   feedbackData,
                   { }, 
                   { }, 
                   if (forTest) FeedbackRequestType.TEST_REQUEST else FeedbackRequestType.PRODUCTION_REQUEST)
  }

  override fun doCancelAction() {
    super.doCancelAction()
    CancelFeedbackNotification().notify(myProject)
  }

  private fun createRequestDescription(): String {
    return buildString {
      // TODO: FIX IT
      //appendLine(DemoFeedbackBundle.message("dialog.zendesk.title"))
      //appendLine(DemoFeedbackBundle.message("dialog.zendesk.description"))
      //appendLine()
      //appendLine(DemoFeedbackBundle.message("dialog.zendesk.rating.label"))
      //appendLine(" ${ratingProperty.get()}")
      //appendLine()
      //appendLine(DemoFeedbackBundle.message("dialog.zendesk.like_most.textarea.label"))
      //appendLine(textAreaLikeMostFeedbackProperty.get())
      //appendLine()
      //appendLine(DemoFeedbackBundle.message("dialog.zendesk.dislike.textarea.label"))
      //appendLine(textAreaDislikeFeedbackProperty.get())
      //appendLine()
      //appendLine()
      //appendLine(newUISystemInfoData.value.toString())
    }
  }

  private fun createCollectedDataJsonObject(): JsonObject {
    val collectedData = buildJsonObject {
      // TODO: FIX IT
      //put(FEEDBACK_REPORT_ID_KEY, FEEDBACK_REPORT_ID_VALUE)
      //put("format_version", FEEDBACK_JSON_VERSION)
      //put("rating", ratingProperty.get())
      //put("like_most", textAreaLikeMostFeedbackProperty.get())
      //put("dislike", textAreaDislikeFeedbackProperty.get())
      //put("system_info", jsonConverter.encodeToJsonElement(newUISystemInfoData.value))
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

  override fun createActions(): Array<Action> {
    return arrayOf(cancelAction, okAction)
  }

  override fun getOKAction(): Action {
    return object : OkAction() {
      init {
        putValue(NAME, DemoFeedbackBundle.message("dialog.ok.label"))
      }
    }
  }

  override fun getCancelAction(): Action {
    val cancelAction = super.getCancelAction()
    cancelAction.putValue(Action.NAME, DemoFeedbackBundle.message("dialog.cancel.label"))
    return cancelAction
  }
}