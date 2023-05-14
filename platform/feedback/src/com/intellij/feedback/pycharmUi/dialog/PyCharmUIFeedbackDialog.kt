// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.pycharmUi.dialog

import com.intellij.feedback.common.*
import com.intellij.feedback.common.dialog.BaseFeedbackDialog
import com.intellij.feedback.common.dialog.COMMON_FEEDBACK_SYSTEM_INFO_VERSION
import com.intellij.feedback.common.dialog.CommonFeedbackSystemInfoData
import com.intellij.feedback.common.dialog.showFeedbackSystemInfoDialog
import com.intellij.feedback.common.dialog.uiBlocks.*
import com.intellij.feedback.common.notification.ThanksForFeedbackNotification
import com.intellij.feedback.pycharmUi.bundle.PyCharmUIFeedbackBundle
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.ui.LicensingFacade
import com.intellij.ui.NewUI
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import javax.swing.Action
import javax.swing.Action.NAME
import javax.swing.JComponent

class PyCharmUIFeedbackDialog(
  myProject: Project?,
  private val forTest: Boolean
) : BaseFeedbackDialog(myProject) {

  /** Increase the additional number when feedback format is changed */
  override val feedbackJsonVersion = COMMON_FEEDBACK_SYSTEM_INFO_VERSION

  private val TICKET_TITLE_ZENDESK = "PyCharm in-IDE Feedback"
  private val FEEDBACK_TYPE_ZENDESK = "PyCharm in-IDE Feedback"
  override val feedbackReportId = "pycharm_feedback"

  private val systemInfoData: Lazy<PyCharmFeedbackSystemInfoData> = lazy {
    createPyCharmFeedbackSystemInfoData(NewUI.isEnabled())
  }

  private val propertyGraph = PropertyGraph()
  private val ratingOverallImpressionProperty = propertyGraph.property(0)
  private val ratingUIImpressionProperty = propertyGraph.property(0)
  private val textAreaLikeMostProperty = propertyGraph.property("")
  private val textAreaDislikeProperty = propertyGraph.property("")
  private val textFieldEmailProperty = propertyGraph.lazyProperty { LicensingFacade.INSTANCE?.getLicenseeEmail().orEmpty() }

  private val jsonConverter = Json { prettyPrint = true }

  private val blocks: List<FeedbackBlock> = listOf(
    TopLabelBlock(PyCharmUIFeedbackBundle.message("dialog.title")),
    DescriptionBlock(PyCharmUIFeedbackBundle.message("dialog.description")),
    RatingBlock(PyCharmUIFeedbackBundle.message("dialog.rating.overall.impression.label"), "overall_impression"),
    RatingBlock(PyCharmUIFeedbackBundle.message("dialog.rating.ui.impression.label"),
                "ui_impression"),
    TextAreaBlock(PyCharmUIFeedbackBundle.message("dialog.like_most.textarea.label"), "like_most"),
    TextAreaBlock(PyCharmUIFeedbackBundle.message("dialog.dislike.textarea.label"), "dislike_most"),
    EmailBlock(myProject) { showPyCharmFeedbackSystemInfoDialog(myProject, systemInfoData.value) }
  )

  init {
    init()
    title = PyCharmUIFeedbackBundle.message("dialog.top.title")
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

    ThanksForFeedbackNotification(description = PyCharmUIFeedbackBundle.message(
      "notification.thanks.feedback.content")).notify(myProject)
  }

  private fun createCollectedDataJsonString(): JsonObject {
    val collectedData = buildJsonObject {
      put(FEEDBACK_REPORT_ID_KEY, feedbackReportId)
      put("format_version", feedbackJsonVersion)
      put("overall_impression", ratingOverallImpressionProperty.get())
      put("ui_impression", ratingUIImpressionProperty.get())
      put("like_most", textAreaLikeMostProperty.get())
      put("dislike_most", textAreaDislikeProperty.get())
      put("system_info", jsonConverter.encodeToJsonElement(systemInfoData.value))
    }
    return collectedData
  }

  private fun createRequestDescription(): String {
    return buildString {
      appendLine(PyCharmUIFeedbackBundle.message("dialog.zendesk.title"))
      appendLine(PyCharmUIFeedbackBundle.message("dialog.zendesk.description"))
      appendLine()
      appendLine(PyCharmUIFeedbackBundle.message("dialog.zendesk.rating.overall.impression.label"))
      appendLine(" ${ratingOverallImpressionProperty.get()}")
      appendLine()
      appendLine(PyCharmUIFeedbackBundle.message("dialog.zendesk.rating.ui.impression.label"))
      appendLine(" ${ratingUIImpressionProperty.get()}")
      appendLine()
      appendLine(PyCharmUIFeedbackBundle.message("dialog.zendesk.like_most.textarea.label"))
      appendLine(textAreaLikeMostProperty.get())
      appendLine()
      appendLine(PyCharmUIFeedbackBundle.message("dialog.zendesk.dislike.textarea.label"))
      appendLine(textAreaDislikeProperty.get())
      appendLine()
      appendLine()
      appendLine(systemInfoData.value.toString())
    }
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
        putValue(NAME, PyCharmUIFeedbackBundle.message("dialog.ok.label"))
      }
    }
  }

  override fun getCancelAction(): Action {
    val cancelAction = super.getCancelAction()
    cancelAction.putValue(NAME, PyCharmUIFeedbackBundle.message("dialog.cancel.label"))
    return cancelAction
  }
}

@Serializable
private data class PyCharmFeedbackSystemInfoData(
  val isNewUINowEnabled: Boolean,
  val commonSystemInfo: CommonFeedbackSystemInfoData
) {
  override fun toString(): String {
    return buildString {
      appendLine(PyCharmUIFeedbackBundle.message("dialog.system.info.isNewUIEnabled"))
      appendLine()
      appendLine(if (isNewUINowEnabled) "True" else "False")
      appendLine()
      commonSystemInfo.toString()
    }
  }
}

private fun showPyCharmFeedbackSystemInfoDialog(project: Project?,
                                                systemInfoData: PyCharmFeedbackSystemInfoData
) = showFeedbackSystemInfoDialog(project, systemInfoData.commonSystemInfo) {
  row(PyCharmUIFeedbackBundle.message("dialog.system.info.isNewUIEnabled")) {
    label(if (systemInfoData.isNewUINowEnabled) "True" else "False") //NON-NLS
  }
}

private fun createPyCharmFeedbackSystemInfoData(isNewUINowEnabled: Boolean): PyCharmFeedbackSystemInfoData {
  return PyCharmFeedbackSystemInfoData(isNewUINowEnabled, CommonFeedbackSystemInfoData.getCurrentData())
}