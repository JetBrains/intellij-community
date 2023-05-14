// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common.dialog

import com.intellij.feedback.common.FeedbackRequestData
import com.intellij.feedback.common.FeedbackRequestType
import com.intellij.feedback.common.bundle.CommonFeedbackBundle
import com.intellij.feedback.common.dialog.uiBlocks.FeedbackBlock
import com.intellij.feedback.common.dialog.uiBlocks.JsonDataProvider
import com.intellij.feedback.common.dialog.uiBlocks.NoEmailAgreementBlock
import com.intellij.feedback.common.submitFeedback
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
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

abstract class BlockBasedFeedbackDialog(protected val myProject: Project?,
                                        protected val myForTest: Boolean) : DialogWrapper(myProject) {

  /** Increase the additional number when feedback format is changed */
  protected abstract val myFeedbackJsonVersion: Int
  protected abstract val myFeedbackReportId: String

  protected abstract val myTitle: String
  protected abstract val myBlocks: List<FeedbackBlock>

  protected abstract val mySystemInfoData: CommonFeedbackSystemInfoData

  private val myJsonConverter = Json { prettyPrint = true }

  private val mySystemInfoJsonName: String = "system_info"

  private val myNoEmailAgreementBlock: NoEmailAgreementBlock = NoEmailAgreementBlock(myProject) {
    showFeedbackSystemInfoDialog(myProject, mySystemInfoData)
  }

  init {
    setTitle()
    isResizable = false
  }

  fun setTitle() {
    title = myTitle
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

    //TODO: Add scroll to dialog for small displays. Fix problem with overlapping scroll bar and bottom agreement
    //val scrollablePane = JBScrollPane(
    //  mainPanel,
    //  JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
    //  JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    //).apply {
    //  border = JBUI.Borders.empty()
    //  setViewportView(mainPanel)
    //}
    //
    //return panel {
    //  row {
    //    cell(scrollablePane)
    //  }.resizableRow()
    //}.apply {
    //  registerIntegratedPanel(mainPanel)
    //}
  }

  protected open fun addFooterToPanel(panel: Panel) {
    myNoEmailAgreementBlock.addToPanel(panel)
  }

  override fun doOKAction() {
    super.doOKAction()
    sendFeedbackData()
  }

  protected open fun sendFeedbackData() {
    //TODO: Add updating settings, maybe to IdleFeedbackTypes
    //AquaNewUserFeedbackService.getInstance().state.feedbackSent = true
    val feedbackData = FeedbackRequestData(myFeedbackReportId, collectDataToJsonObject())
    submitFeedback(feedbackData,
                   { },
                   { },
                   if (myForTest) FeedbackRequestType.TEST_REQUEST else FeedbackRequestType.PRODUCTION_REQUEST)
  }

  protected fun collectDataToJsonObject(): JsonObject {
    return buildJsonObject {
      for (block in myBlocks) {
        if (block is JsonDataProvider) {
          block.collectBlockDataToJson(this)
        }
      }

      put(mySystemInfoJsonName, myJsonConverter.encodeToJsonElement(mySystemInfoData))
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