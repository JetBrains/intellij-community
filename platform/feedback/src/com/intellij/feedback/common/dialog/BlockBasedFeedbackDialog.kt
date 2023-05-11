// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common.dialog

import com.intellij.feedback.common.FeedbackRequestData
import com.intellij.feedback.common.FeedbackRequestType
import com.intellij.feedback.common.bundle.CommonFeedbackBundle
import com.intellij.feedback.common.dialog.uiBlocks.FeedbackBlock
import com.intellij.feedback.common.dialog.uiBlocks.JsonDataProvider
import com.intellij.feedback.common.dialog.uiBlocks.NoEmailAgreementBlock
import com.intellij.feedback.common.submitFeedback
import com.intellij.feedback.new_ui.CancelFeedbackNotification
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import javax.swing.Action
import javax.swing.JComponent

abstract class BlockBasedFeedbackDialog(@NlsContexts.DialogTitle private val myTitle: String,
                                        protected val myBlocks: List<FeedbackBlock>,
                                        protected val myProject: Project?,
                                        protected val forTest: Boolean) : DialogWrapper(myProject) {

  /** Increase the additional number when feedback format is changed */
  protected abstract val feedbackJsonVersion: Int
  protected abstract val feedbackReportId: String
  protected abstract val systemInfoData: CommonFeedbackSystemInfoData

  protected val myJsonConverter = Json { prettyPrint = true }

  protected val systemInfoJsonName: String = "system_info"

  private val noEmailAgreementBlock = NoEmailAgreementBlock(myProject) { showFeedbackSystemInfoDialog(myProject, systemInfoData) }

  init {
    title = myTitle
    isResizable = false
  }

  override fun createCenterPanel(): JComponent {
    val mainPanel = panel {
      for (block in myBlocks) {
        block.addToPanel(this)
      }

      addFooterToPanel(this)
    }.also { dialog ->
      dialog.border = JBEmptyBorder(JBUI.scale(15), JBUI.scale(10), JBUI.scale(0), JBUI.scale(10))
    }
    return mainPanel
    //TODO: Make it scrollable
    //return JBScrollPane(mainPanel, JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER).apply {
    //  border = JBUI.Borders.empty()
    //}
  }

  protected open fun addFooterToPanel(panel: Panel) {
    noEmailAgreementBlock.addToPanel(panel)
  }

  override fun doOKAction() {
    super.doOKAction()
    sendFeedbackData()
  }

  protected open fun sendFeedbackData() {
    //TODO: Add updating settings, maybe to IdleFeedbackTypes
    //AquaNewUserFeedbackService.getInstance().state.feedbackSent = true
    val feedbackData = FeedbackRequestData(feedbackReportId, createCollectedDataJsonObject())
    //TODO: Add parameter to send thank you notification
    submitFeedback(myProject,
                   feedbackData,
                   { },
                   { },
                   if (forTest) FeedbackRequestType.TEST_REQUEST else FeedbackRequestType.PRODUCTION_REQUEST)
  }

  protected fun createCollectedDataJsonObject(): JsonObject {
    return buildJsonObject {
      for (block in myBlocks) {
        if (block is JsonDataProvider) {
          block.collectBlockDataToJson(this)
        }
      }

      put(systemInfoJsonName, myJsonConverter.encodeToJsonElement(systemInfoData))
    }
  }

  override fun createActions(): Array<Action> {
    return arrayOf(cancelAction, okAction)
  }

  override fun getOKAction(): Action {
    return object : OkAction() {
      init {
        putValue(NAME, CommonFeedbackBundle.message("dialog.feedback.ok.label"))
      }
    }
  }

  override fun getCancelAction(): Action {
    val cancelAction = super.getCancelAction()
    cancelAction.putValue(Action.NAME, CommonFeedbackBundle.message("dialog.feedback.cancel.label"))
    return cancelAction
  }

  override fun doCancelAction() {
    super.doCancelAction()
    CancelFeedbackNotification().notify(myProject)
  }

}