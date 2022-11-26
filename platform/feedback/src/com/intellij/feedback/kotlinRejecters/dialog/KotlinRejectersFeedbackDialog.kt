// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.kotlinRejecters.dialog

import com.intellij.feedback.common.*
import com.intellij.feedback.common.dialog.COMMON_FEEDBACK_SYSTEM_INFO_VERSION
import com.intellij.feedback.common.dialog.CommonFeedbackSystemInfoData
import com.intellij.feedback.common.dialog.adjustBehaviourForFeedbackForm
import com.intellij.feedback.common.dialog.showFeedbackSystemInfoDialog
import com.intellij.feedback.kotlinRejecters.bundle.KotlinRejectersFeedbackBundle
import com.intellij.ide.feedback.kotlinRejecters.state.KotlinRejectersInfoService
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.LicensingFacade
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.TextComponentEmptyText
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.JBGaps
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.util.function.Predicate
import javax.swing.JComponent

class KotlinRejectersFeedbackDialog(
  private val project: Project?,
  private val forTest: Boolean
) : DialogWrapper(project) {

  /** Increase the additional number when feedback format is changed */
  private val FEEDBACK_JSON_VERSION = COMMON_FEEDBACK_SYSTEM_INFO_VERSION + 2

  private val TICKET_TITLE_ZENDESK = "Kotlin Rejecters Disable Plugin Feedback"
  private val FEEDBACK_TYPE_ZENDESK = "Kotlin Rejecters Disable Plugin Feedback"

  private val SLOW_DOWN_IDE = "Slows down IDE"
  private val BREAKS_CODE_ANALISYS = "Breaks code analisys"
  private val NOISE_NOTIFICATION = "Makes noise notifications and other stuff"
  private val USUALLY_DISABLE_PLUGINS = "Usually deactivate plugins that I don't use"
  private val OTHER = "Other"

  private val commonSystemInfoData: Lazy<CommonFeedbackSystemInfoData> = lazy { CommonFeedbackSystemInfoData.getCurrentData() }

  private val propertyGraph = PropertyGraph()

  private val checkBoxSlowsDownIDEProperty = propertyGraph.property(false)
  private val checkBoxBreaksCodeAnalysisProperty = propertyGraph.property(false)
  private val checkBoxMakeNoiseNotificationProperty = propertyGraph.property(false)
  private val checkBoxUsuallyDeactivatePluginsProperty = propertyGraph.property(false)
  private val checkBoxOtherProperty = propertyGraph.property(false)
  private val textFieldOtherProblemProperty = propertyGraph.property("")

  private val currentlyUseKotlin = propertyGraph.property(
    KotlinRejectersFeedbackBundle.message("dialog.kotlin.feedback.question.2.checkbox.1.label"))

  private val textAreaDetailExplainProperty = propertyGraph.property("")

  private val checkBoxEmailProperty = propertyGraph.property(false)
  private val textFieldEmailProperty = propertyGraph.lazyProperty { LicensingFacade.INSTANCE?.getLicenseeEmail().orEmpty() }

  private var checkBoxOther: JBCheckBox? = null
  private var checkBoxEmail: JBCheckBox? = null

  private val textFieldOtherColumnSize = 41
  private val textAreaRowSize = 4
  private val textAreaOverallFeedbackColumnSize = 42
  private val textFieldEmailColumnSize = 25

  private val jsonConverter = Json { prettyPrint = true }

  init {
    init()
    title = KotlinRejectersFeedbackBundle.message("dialog.kotlin.feedback.top.title")
    isResizable = false
  }

  override fun doOKAction() {
    super.doOKAction()
    if (!forTest) {
      val kotlinRejectersInfoState = KotlinRejectersInfoService.getInstance().state
      kotlinRejectersInfoState.feedbackSent = true
    }
    val email = if (checkBoxEmailProperty.get()) textFieldEmailProperty.get() else DEFAULT_NO_EMAIL_ZENDESK_REQUESTER
    submitGeneralFeedback(project,
                          TICKET_TITLE_ZENDESK,
                          createRequestDescription(),
                          FEEDBACK_TYPE_ZENDESK,
                          createCollectedDataJsonString(),
                          email,
                          { },
                          { },
                          if (forTest) FeedbackRequestType.TEST_REQUEST else FeedbackRequestType.PRODUCTION_REQUEST
    )
  }

  private fun createRequestDescription(): String {
    return buildString {
      appendLine(KotlinRejectersFeedbackBundle.message("dialog.kotlin.feedback.zendesk.title"))
      appendLine(KotlinRejectersFeedbackBundle.message("dialog.kotlin.feedback.zendesk.description"))
      appendLine()
      appendLine(KotlinRejectersFeedbackBundle.message("dialog.kotlin.feedback.zendesk.question.1"))
      appendLine(currentlyUseKotlin.get())
      appendLine()
      appendLine(KotlinRejectersFeedbackBundle.message("dialog.kotlin.feedback.zendesk.question.2"))
      appendLine(createReasonDisablingKotlinList())
      appendLine()
      appendLine(KotlinRejectersFeedbackBundle.message("dialog.kotlin.feedback.zendesk.textarea.label"))
      appendLine(textAreaDetailExplainProperty.get())
    }
  }

  private fun createReasonDisablingKotlinList(): String {
    val resultReasonsList = mutableListOf<String>()
    if (checkBoxUsuallyDeactivatePluginsProperty.get()) {
      resultReasonsList.add(KotlinRejectersFeedbackBundle.message("dialog.kotlin.feedback.zendesk.reason.1.label"))
    }
    if (checkBoxSlowsDownIDEProperty.get()) {
      resultReasonsList.add(KotlinRejectersFeedbackBundle.message("dialog.kotlin.feedback.zendesk.reason.2.label"))
    }
    if (checkBoxBreaksCodeAnalysisProperty.get()) {
      resultReasonsList.add(KotlinRejectersFeedbackBundle.message("dialog.kotlin.feedback.zendesk.reason.3.label"))
    }
    if (checkBoxMakeNoiseNotificationProperty.get()) {
      resultReasonsList.add(KotlinRejectersFeedbackBundle.message("dialog.kotlin.feedback.zendesk.reason.4.label"))
    }
    if (checkBoxOtherProperty.get()) {
      resultReasonsList.add(textFieldOtherProblemProperty.get())
    }
    return resultReasonsList.joinToString(prefix = "- ", separator = "\n- ")
  }

  private fun createCollectedDataJsonString(): String {
    val collectedData = buildJsonObject {
      put(FEEDBACK_REPORT_ID_KEY, "kotlin_rejecters_disabled_plugin")
      put("format_version", FEEDBACK_JSON_VERSION)
      put("developed_on_kotlin", currentlyUseKotlin.get())
      putJsonArray("problems") {
        if (checkBoxSlowsDownIDEProperty.get()) {
          add(createReasonJsonObject(SLOW_DOWN_IDE))
        }
        if (checkBoxBreaksCodeAnalysisProperty.get()) {
          add(createReasonJsonObject(BREAKS_CODE_ANALISYS))
        }
        if (checkBoxMakeNoiseNotificationProperty.get()) {
          add(createReasonJsonObject(NOISE_NOTIFICATION))
        }
        if (checkBoxUsuallyDeactivatePluginsProperty.get()) {
          add(createReasonJsonObject(USUALLY_DISABLE_PLUGINS))
        }
        if (checkBoxOtherProperty.get()) {
          add(createReasonJsonObject(OTHER, textFieldOtherProblemProperty.get()))
        }
      }
      put("detailed_explanation", textAreaDetailExplainProperty.get())
      put("system_info", jsonConverter.encodeToJsonElement(commonSystemInfoData.value))
    }
    return jsonConverter.encodeToString(collectedData)
  }

  private fun createReasonJsonObject(name: String, description: String? = null): JsonObject {
    return buildJsonObject {
      put("name", name)
      if (description != null) {
        put("description", description)
      }
    }
  }

  override fun createCenterPanel(): JComponent {
    val mainPanel = panel {
      row {
        label(KotlinRejectersFeedbackBundle.message("dialog.kotlin.feedback.title")).applyToComponent {
          font = JBFont.h1()
        }
      }

      row {
        label(KotlinRejectersFeedbackBundle.message("dialog.kotlin.feedback.description"))
      }.bottomGap(BottomGap.MEDIUM)


      row {
        label(
          KotlinRejectersFeedbackBundle.message("dialog.kotlin.feedback.question.1.title")).applyToComponent {
          font = JBFont.h4()
        }
      }.bottomGap(BottomGap.NONE)
      row {
        checkBox(KotlinRejectersFeedbackBundle.message("dialog.kotlin.feedback.question.1.checkbox.1.label"))
          .bindSelected(checkBoxUsuallyDeactivatePluginsProperty)
      }
      row {
        checkBox(KotlinRejectersFeedbackBundle.message("dialog.kotlin.feedback.question.1.checkbox.2.label"))
          .bindSelected(checkBoxSlowsDownIDEProperty)
      }
      row {
        checkBox(KotlinRejectersFeedbackBundle.message("dialog.kotlin.feedback.question.1.checkbox.3.label"))
          .bindSelected(checkBoxBreaksCodeAnalysisProperty)
      }
      row {
        checkBox(KotlinRejectersFeedbackBundle.message("dialog.kotlin.feedback.question.1.checkbox.4.label"))
          .bindSelected(checkBoxMakeNoiseNotificationProperty)
      }

      row {
        checkBox("").bindSelected(checkBoxOtherProperty).applyToComponent {
          checkBoxOther = this
        }.customize(JBGaps(right = 4))

        textField()
          .bindText(textFieldOtherProblemProperty)
          .columns(textFieldOtherColumnSize)
          .errorOnApply(KotlinRejectersFeedbackBundle.message("dialog.kotlin.feedback.question.1.checkbox.5.required")) {
            checkBoxOtherProperty.get() && it.text.isBlank()
          }
          .applyToComponent {
            emptyText.text = KotlinRejectersFeedbackBundle.message("dialog.kotlin.feedback.question.1.checkbox.5.placeholder")
            textFieldOtherProblemProperty.afterChange {
              if (it.isNotBlank()) {
                checkBoxOtherProperty.set(true)
              }
              else {
                checkBoxOtherProperty.set(false)
              }
            }
            putClientProperty(TextComponentEmptyText.STATUS_VISIBLE_FUNCTION, Predicate<JBTextField> { it.text.isEmpty() })
          }
      }.bottomGap(BottomGap.MEDIUM)


      row {
        label(
          KotlinRejectersFeedbackBundle.message("dialog.kotlin.feedback.question.2.title")).applyToComponent {
          font = JBFont.h4()
        }
      }.bottomGap(BottomGap.NONE)
      buttonsGroup {
        row {
          radioButton(KotlinRejectersFeedbackBundle.message("dialog.kotlin.feedback.question.2.checkbox.1.label"),
                      KotlinRejectersFeedbackBundle.message("dialog.kotlin.feedback.question.2.checkbox.1.label"))
        }
        row {
          radioButton(KotlinRejectersFeedbackBundle.message("dialog.kotlin.feedback.question.2.checkbox.2.label"),
                      KotlinRejectersFeedbackBundle.message("dialog.kotlin.feedback.question.2.checkbox.2.label"))
        }
        row {
          radioButton(KotlinRejectersFeedbackBundle.message("dialog.kotlin.feedback.question.2.checkbox.3.label"),
                      KotlinRejectersFeedbackBundle.message("dialog.kotlin.feedback.question.2.checkbox.3.label"))
        }.bottomGap(BottomGap.MEDIUM)
      }.bind({ currentlyUseKotlin.get() }, { currentlyUseKotlin.set(it) })


      row {
        label(KotlinRejectersFeedbackBundle.message("dialog.kotlin.feedback.textarea.label")).applyToComponent {
          font = JBFont.h4()
        }
      }.bottomGap(BottomGap.NONE)
      row {
        textArea()
          .bindText(textAreaDetailExplainProperty)
          .rows(textAreaRowSize)
          .columns(textAreaOverallFeedbackColumnSize)
          .applyToComponent {
            adjustBehaviourForFeedbackForm()
          }
      }.bottomGap(BottomGap.MEDIUM)

      row {
        checkBox(KotlinRejectersFeedbackBundle.message("dialog.kotlin.feedback.checkbox.email"))
          .bindSelected(checkBoxEmailProperty)
          .applyToComponent {
            checkBoxEmail = this
          }
      }.topGap(TopGap.MEDIUM)
      indent {
        row {
          textField().bindText(textFieldEmailProperty).columns(textFieldEmailColumnSize).applyToComponent {
            emptyText.text = KotlinRejectersFeedbackBundle.message("dialog.kotlin.feedback.textfield.email.placeholder")
            isEnabled = checkBoxEmailProperty.get()

            checkBoxEmail?.addActionListener { _ ->
              isEnabled = checkBoxEmailProperty.get()
            }
            putClientProperty(TextComponentEmptyText.STATUS_VISIBLE_FUNCTION,
                              Predicate<JBTextField> { textField -> textField.text.isEmpty() })
          }.errorOnApply(KotlinRejectersFeedbackBundle.message("dialog.kotlin.feedback.checkbox.email.required")) {
            checkBoxEmailProperty.get() && it.text.isBlank()
          }.errorOnApply(KotlinRejectersFeedbackBundle.message("dialog.kotlin.feedback.checkbox.email.invalid")) {
            checkBoxEmailProperty.get() && it.text.isNotBlank() && !it.text.matches(Regex(".+@.+\\..+"))
          }
        }.bottomGap(BottomGap.MEDIUM)
      }

      row {
        feedbackAgreement(project) {
          showFeedbackSystemInfoDialog(project, commonSystemInfoData.value)
        }
      }.bottomGap(BottomGap.SMALL).topGap(TopGap.MEDIUM)

    }.also { dialog ->
      dialog.border = JBEmptyBorder(JBUI.scale(15), JBUI.scale(10), JBUI.scale(0), JBUI.scale(10))
    }

    return JBScrollPane(mainPanel, JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER).apply {
      border = JBUI.Borders.empty()
    }
  }
}
