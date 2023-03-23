// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.productivityMetric.dialog

import com.intellij.feedback.common.*
import com.intellij.feedback.common.bundle.CommonFeedbackBundle
import com.intellij.feedback.common.dialog.*
import com.intellij.feedback.common.notification.ThanksForFeedbackNotification
import com.intellij.feedback.productivityMetric.bundle.ProductivityFeedbackBundle
import com.intellij.feedback.productivityMetric.statistics.ProductivityMetricCountCollector
import com.intellij.ide.ui.laf.darcula.ui.DarculaComboBoxUI.DISABLE_SETTING_FOREGROUND
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import kotlinx.serialization.json.*
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JList

class ProductivityFeedbackDialog(
  private val project: Project?,
  private val forTest: Boolean
) : BaseFeedbackDialog(project) {

  /** Increase the additional number when feedback format is changed */
  override val feedbackJsonVersion = COMMON_FEEDBACK_SYSTEM_INFO_VERSION

  override val feedbackReportId = "productivity_metric_feedback"

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
  private val usingExperience: GraphProperty<String?> = propertyGraph.property(null)

  private val jsonConverter = Json { prettyPrint = true }

  init {
    init()
    title = ProductivityFeedbackBundle.message("dialog.top.title")
  }

  override fun doOKAction() {
    super.doOKAction()
    ProductivityMetricCountCollector.logProductivityMetricFeedback(productivityProperty.get(),
                                                                   proficiencyProperty.get(),
                                                                   mapExperienceToInt(usingExperience.get()))
    val feedbackData = FeedbackRequestData(feedbackReportId, createCollectedDataJsonString())
    submitFeedback(project, feedbackData,
                   { }, { },
                   if (forTest) FeedbackRequestType.TEST_REQUEST else FeedbackRequestType.PRODUCTION_REQUEST,
                   ThanksForFeedbackNotification(description = ProductivityFeedbackBundle.message(
                     "notification.thanks.feedback.content", applicationName)))
  }

  private fun createCollectedDataJsonString(): JsonObject {
    val collectedData = buildJsonObject {
      put(FEEDBACK_REPORT_ID_KEY, feedbackReportId)
      put("format_version", feedbackJsonVersion)
      put("productivity_influence", productivityProperty.get())
      put("proficiency_level", proficiencyProperty.get())
      put("using_experience", usingExperience.get())
      put("system_info", jsonConverter.encodeToJsonElement(systemInfoData.value))
    }
    return collectedData
  }

  override fun createCenterPanel(): JComponent {
    val mainPanel = panel {
      row {
        label(ProductivityFeedbackBundle.message("dialog.title"))
          .applyToComponent {
            font = JBFont.h1()
          }
      }

      createSegmentedButtonWithBottomLabels(
        ProductivityFeedbackBundle.message("dialog.segmentedButton.1.label", applicationName),
        List(9) { it + 1 }, { it.toString() },
        9, productivityProperty,
        ProductivityFeedbackBundle.message("dialog.segmentedButton.1.left.label"),
        ProductivityFeedbackBundle.message("dialog.segmentedButton.1.middle.label"),
        ProductivityFeedbackBundle.message("dialog.segmentedButton.1.right.label")
      )

      createSegmentedButtonWithBottomLabels(
        ProductivityFeedbackBundle.message("dialog.segmentedButton.2.label", applicationName),
        List(9) { it + 1 }, { it.toString() },
        9, proficiencyProperty,
        ProductivityFeedbackBundle.message("dialog.segmentedButton.2.left.label"),
        null,
        ProductivityFeedbackBundle.message("dialog.segmentedButton.2.right.label")
      )

      row {
        label(ProductivityFeedbackBundle.message("dialog.combobox.label", applicationName))
          .customize(Gaps(top = IntelliJSpacingConfiguration().verticalComponentGap))
          .bold()
      }.bottomGap(BottomGap.SMALL).topGap(TopGap.MEDIUM)
      row {
        val renderer = object : SimpleListCellRenderer<String>() {
          override fun customize(list: JList<out String>, @NlsContexts.Label value: String?, index: Int, selected: Boolean, hasFocus: Boolean) {
            if (value == null) {
              text = ProductivityFeedbackBundle.message("dialog.combobox.placeholder")
              foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
            } else {
              text = value
            }
          }
        }
        comboBox(List(8) { ProductivityFeedbackBundle.message("dialog.combobox.item.${it + 1}") }, renderer)
          .applyToComponent {
            putClientProperty(DISABLE_SETTING_FOREGROUND, true)
            selectedItem = null
            columns(COLUMNS_MEDIUM)
          }.whenItemSelectedFromUi {
            usingExperience.set(it)
          }
          .validationOnApply {
            if (usingExperience.get() == null) {
              return@validationOnApply error(ProductivityFeedbackBundle.message("dialog.combobox.error"))
            }
            return@validationOnApply null
          }

      }.bottomGap(BottomGap.MEDIUM)

      row {
        feedbackAgreement(project, CommonFeedbackBundle.message("dialog.feedback.consent.withoutEmail")) {
          showFeedbackSystemInfoDialog(project, systemInfoData.value)
        }
      }.bottomGap(BottomGap.SMALL)
    }.also { dialog ->
      dialog.border = JBEmptyBorder(JBUI.scale(15), JBUI.scale(10), JBUI.scale(0), JBUI.scale(10))
    }
    mainPanel.registerValidators(myDisposable)
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

  private fun mapExperienceToInt(experience: String?): Int {
    return when (experience) {
      ProductivityFeedbackBundle.message("dialog.combobox.item.1") -> 1
      ProductivityFeedbackBundle.message("dialog.combobox.item.2") -> 2
      ProductivityFeedbackBundle.message("dialog.combobox.item.3") -> 3
      ProductivityFeedbackBundle.message("dialog.combobox.item.4") -> 4
      ProductivityFeedbackBundle.message("dialog.combobox.item.5") -> 5
      ProductivityFeedbackBundle.message("dialog.combobox.item.6") -> 6
      ProductivityFeedbackBundle.message("dialog.combobox.item.7") -> 7
      ProductivityFeedbackBundle.message("dialog.combobox.item.8") -> 8
      else -> -1
    }
  }
}