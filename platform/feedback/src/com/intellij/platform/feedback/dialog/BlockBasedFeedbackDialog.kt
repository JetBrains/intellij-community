// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.dialog

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.platform.feedback.dialog.uiBlocks.FeedbackBlock
import com.intellij.platform.feedback.dialog.uiBlocks.JsonDataProvider
import com.intellij.platform.feedback.dialog.uiBlocks.NoEmailAgreementBlock
import com.intellij.platform.feedback.impl.FEEDBACK_REPORT_ID_KEY
import com.intellij.platform.feedback.impl.FeedbackRequestData
import com.intellij.platform.feedback.impl.FeedbackRequestType
import com.intellij.platform.feedback.impl.bundle.CommonFeedbackBundle
import com.intellij.platform.feedback.impl.submitFeedback
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.swing.Action
import javax.swing.JComponent

/** This number should be increased when [BlockBasedFeedbackDialog] fields changing */
const val BLOCK_BASED_FEEDBACK_VERSION = 1

abstract class BlockBasedFeedbackDialog<T : SystemDataJsonSerializable>(
  protected val myProject: Project?,
  protected val myForTest: Boolean
) : DialogWrapper(myProject) {

  private val myFeedbackJsonVersionKey: String = "format_version"

  /** Increase the additional number when feedback format is changed */
  protected open val myFeedbackJsonVersion: Int = COMMON_FEEDBACK_SYSTEM_INFO_VERSION + BLOCK_BASED_FEEDBACK_VERSION
  protected abstract val myFeedbackReportId: String

  protected abstract val myTitle: String
  protected abstract val myBlocks: List<FeedbackBlock>

  protected abstract val mySystemInfoData: T
  protected abstract val myShowFeedbackSystemInfoDialog: () -> Unit

  private val myJsonConverter = Json { prettyPrint = true }

  private val mySystemInfoJsonName: String = "system_info"

  private val myNoEmailAgreementBlock: NoEmailAgreementBlock = NoEmailAgreementBlock(myProject) {
    myShowFeedbackSystemInfoDialog()
  }

  init {
    isResizable = false
  }

  fun setTitle() {
    title = myTitle
  }

  override fun createCenterPanel(): JComponent {
    setTitle()

    val mainPanel = panel {
      for (block in myBlocks) {
        block.addToPanel(this)
      }

      addFooterToPanel(this)
    }.also { dialog ->
      dialog.border = JBEmptyBorder(0, 10, 0, 10)
    }

    val scrollablePane = JBScrollPane(
      mainPanel,
      JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
      JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    ).apply {
      border = JBUI.Borders.empty()
    }

    return panel {
      row {
        cell(scrollablePane)
          .align(Align.FILL)
      }.resizableRow()
    }.apply {
      registerIntegratedPanel(mainPanel)
    }
  }

  protected open fun addFooterToPanel(panel: Panel) {
    myNoEmailAgreementBlock.addToPanel(panel)
  }

  override fun doOKAction() {
    super.doOKAction()
    sendFeedbackData()
  }

  protected open fun sendFeedbackData() {
    val feedbackData = FeedbackRequestData(myFeedbackReportId, collectDataToJsonObject())
    submitFeedback(feedbackData,
                   { showThanksNotification() },
                   { },
                   if (myForTest) FeedbackRequestType.TEST_REQUEST else FeedbackRequestType.PRODUCTION_REQUEST)
  }

  protected open fun showThanksNotification() {

  }

  protected fun collectDataToJsonObject(): JsonObject {
    return buildJsonObject {
      put(FEEDBACK_REPORT_ID_KEY, myFeedbackReportId)
      put(myFeedbackJsonVersionKey, myFeedbackJsonVersion)

      for (block in myBlocks) {
        if (block is JsonDataProvider) {
          block.collectBlockDataToJson(this)
        }
      }

      put(mySystemInfoJsonName, mySystemInfoData.serializeToJson(myJsonConverter))
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
}