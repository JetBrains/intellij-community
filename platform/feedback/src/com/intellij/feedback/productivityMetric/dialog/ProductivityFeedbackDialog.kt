// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.productivityMetric.dialog

import com.intellij.feedback.FeedbackRequestData
import com.intellij.feedback.common.FEEDBACK_REPORT_ID_KEY
import com.intellij.feedback.common.FeedbackRequestType
import com.intellij.feedback.common.dialog.COMMON_FEEDBACK_SYSTEM_INFO_VERSION
import com.intellij.feedback.common.dialog.CommonFeedbackSystemInfoData
import com.intellij.feedback.common.dialog.showFeedbackSystemInfoDialog
import com.intellij.feedback.common.feedbackAgreement
import com.intellij.feedback.new_ui.CancelFeedbackNotification
import com.intellij.feedback.productivityMetric.bundle.ProductivityFeedbackBundle
import com.intellij.feedback.submitFeedback
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import kotlinx.serialization.json.*
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.SwingConstants

class ProductivityFeedbackDialog(
  private val project: Project?,
  private val forTest: Boolean
) : DialogWrapper(project) {

  /** Increase the additional number when feedback format is changed */
  private val FEEDBACK_JSON_VERSION = COMMON_FEEDBACK_SYSTEM_INFO_VERSION

  private val FEEDBACK_REPORT_ID_VALUE = "productivity_metric_feedback"

  private val systemInfoData: Lazy<CommonFeedbackSystemInfoData> = lazy { CommonFeedbackSystemInfoData.getCurrentData() }

  private val digitRegexp: Regex = Regex("\\d")
  private val applicationName: String = run {
    val fullAppName: String = ApplicationInfoEx.getInstanceEx().fullApplicationName
    val range: IntRange? = digitRegexp.find(fullAppName)?.range
    if (range != null) {
      fullAppName.substring(0, range.first).trim()
    }
    else {
      fullAppName
    }
  }

  private val propertyGraph = PropertyGraph()
  private val productivityProperty = propertyGraph.property(0)
  private val proficiencyProperty = propertyGraph.property(0)
  private val usingExperience = propertyGraph.property("")

  private val jsonConverter = Json { prettyPrint = true }

  init {
    init()
    title = ProductivityFeedbackBundle.message("dialog.top.title")
    isResizable = false
  }

  override fun doOKAction() {
    super.doOKAction()
    val feedbackData = FeedbackRequestData(FEEDBACK_REPORT_ID_VALUE, createCollectedDataJsonString())
    submitFeedback(project, feedbackData,
                   { }, { },
                   if (forTest) FeedbackRequestType.TEST_REQUEST else FeedbackRequestType.PRODUCTION_REQUEST)
  }

  override fun doCancelAction() {
    super.doCancelAction()
    CancelFeedbackNotification().notify(project)
  }

  private fun createCollectedDataJsonString(): JsonObject {
    val collectedData = buildJsonObject {
      put(FEEDBACK_REPORT_ID_KEY, FEEDBACK_REPORT_ID_VALUE)
      put("format_version", FEEDBACK_JSON_VERSION)
      put("productivity_influence", productivityProperty.get())
      put("proficiency_level", proficiencyProperty.get())
      put("using_experience", usingExperience.get())
      put("system_info", jsonConverter.encodeToJsonElement(systemInfoData.value))
    }
    return collectedData
  }

  override fun createCenterPanel(): JComponent {
    val productivitySegmentedButtonPanel = panel {
      row {
        label(ProductivityFeedbackBundle.message("dialog.segmentedButton.1.label", applicationName))
          .customize(Gaps(top = IntelliJSpacingConfiguration().verticalComponentGap))
          .bold()
      }.bottomGap(BottomGap.SMALL).topGap(TopGap.MEDIUM)
      row {
        segmentedButton(List(9) { it + 1 }) { it.toString() }
          .apply {
            maxButtonsCount(9)
          }.customize(Gaps(top = IntelliJSpacingConfiguration().verticalComponentGap))
          .whenItemSelected { productivityProperty.set(it) }
          .align(Align.FILL)
      }
      row {
        label(ProductivityFeedbackBundle.message("dialog.segmentedButton.1.left.label"))
          .applyToComponent { font = ComponentPanelBuilder.getCommentFont(font) }
          .widthGroup("Group1")
        label(ProductivityFeedbackBundle.message("dialog.segmentedButton.1.middle.label"))
          .applyToComponent { font = ComponentPanelBuilder.getCommentFont(font) }
          .align(AlignX.CENTER)
          .resizableColumn()
        label(ProductivityFeedbackBundle.message("dialog.segmentedButton.1.right.label"))
          .applyToComponent {
            font = ComponentPanelBuilder.getCommentFont(font)
            horizontalAlignment = SwingConstants.RIGHT
          }
          .widthGroup("Group1")
      }
    }
    val proficiencySegmentedButtonPanel = panel {
      row {
        label(ProductivityFeedbackBundle.message("dialog.segmentedButton.2.label", applicationName))
          .customize(Gaps(top = IntelliJSpacingConfiguration().verticalComponentGap))
          .bold()
      }.bottomGap(BottomGap.SMALL).topGap(TopGap.MEDIUM)
      row {
        segmentedButton(List(9) { it + 1 }) { it.toString() }
          .apply {
            maxButtonsCount(9)
          }.customize(Gaps(top = IntelliJSpacingConfiguration().verticalComponentGap))
          .whenItemSelected { proficiencyProperty.set(it) }
          .align(Align.FILL)
      }
      row {
        label(ProductivityFeedbackBundle.message("dialog.segmentedButton.2.left.label"))
          .applyToComponent { font = ComponentPanelBuilder.getCommentFont(font) }
          .widthGroup("Group2")
          .resizableColumn()
        label(ProductivityFeedbackBundle.message("dialog.segmentedButton.2.right.label"))
          .applyToComponent {
            font = ComponentPanelBuilder.getCommentFont(font)
            horizontalAlignment = SwingConstants.RIGHT
          }
          .widthGroup("Group2")
      }
    }

    val mainPanel = panel {
      row {
        label(ProductivityFeedbackBundle.message("dialog.title"))
          .applyToComponent {
            font = JBFont.h1()
          }
      }

      row {
        cell(productivitySegmentedButtonPanel)
          .align(Align.FILL)
      }

      row {
        cell(proficiencySegmentedButtonPanel)
          .align(Align.FILL)
      }

      row {
        label(ProductivityFeedbackBundle.message("dialog.combobox.label", applicationName))
          .customize(Gaps(top = IntelliJSpacingConfiguration().verticalComponentGap))
          .bold()
      }.bottomGap(BottomGap.SMALL).topGap(TopGap.MEDIUM)
      row {
        comboBox(List(8) { ProductivityFeedbackBundle.message("dialog.combobox.item.${it + 1}") })
          .applyToComponent {
            usingExperience.set(selectedItem?.toString() ?: "null")
            columns(COLUMNS_MEDIUM)
          }.whenItemSelectedFromUi { usingExperience.set(it) }

      }.bottomGap(BottomGap.MEDIUM)

      row {
        feedbackAgreement(project) {
          showFeedbackSystemInfoDialog(project, systemInfoData.value)
        }
      }.bottomGap(BottomGap.SMALL)
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
        putValue(NAME, ProductivityFeedbackBundle.message("dialog.ok.label"))
      }
    }
  }

  override fun getCancelAction(): Action {
    val cancelAction = super.getCancelAction()
    cancelAction.putValue(Action.NAME, ProductivityFeedbackBundle.message("dialog.cancel.label"))
    return cancelAction
  }
}