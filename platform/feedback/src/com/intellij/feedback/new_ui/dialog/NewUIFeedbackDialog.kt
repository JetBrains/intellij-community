// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.new_ui.dialog

import com.intellij.feedback.common.*
import com.intellij.feedback.common.dialog.COMMON_FEEDBACK_SYSTEM_INFO_VERSION
import com.intellij.feedback.common.dialog.CommonFeedbackSystemInfoData
import com.intellij.feedback.common.dialog.adjustBehaviourForFeedbackForm
import com.intellij.feedback.common.dialog.showFeedbackSystemInfoDialog
import com.intellij.feedback.new_ui.CancelFeedbackNotification
import com.intellij.feedback.new_ui.bundle.NewUIFeedbackBundle
import com.intellij.feedback.new_ui.state.NewUIInfoService
import com.intellij.ide.feedback.RatingComponent
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ex.MultiLineLabel
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.LicensingFacade
import com.intellij.ui.PopupBorder
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.TextComponentEmptyText
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.awt.event.ActionEvent
import java.util.function.Predicate
import javax.swing.Action
import javax.swing.Action.NAME
import javax.swing.JComponent
import javax.swing.SwingUtilities

class NewUIFeedbackDialog(
  private val project: Project?,
  private val forTest: Boolean
) : DialogWrapper(project) {

  /** Increase the additional number when feedback format is changed */
  private val FEEDBACK_JSON_VERSION = COMMON_FEEDBACK_SYSTEM_INFO_VERSION + 1
  
  private val TICKET_TITLE_ZENDESK = "New UI in-IDE Feedback"
  private val FEEDBACK_TYPE_ZENDESK = "New UI in-IDE Feedback"
  private val FEEDBACK_REPORT_ID_VALUE = "new_ui_feedback"

  private val newUISystemInfoData: Lazy<NewUIFeedbackSystemInfoData> = lazy { 
    val state = NewUIInfoService.getInstance().state
    createNewUIFeedbackSystemInfoData(Registry.get("ide.experimental.ui").asBoolean(),
                                      state.enableNewUIDate, state.disableNewUIDate) 
  }

  private val propertyGraph = PropertyGraph()
  private val ratingProperty = propertyGraph.property(0)
  private val textAreaLikeMostFeedbackProperty = propertyGraph.property("")
  private val textAreaDislikeFeedbackProperty = propertyGraph.property("")
  private val checkBoxEmailProperty = propertyGraph.property(false)
  private val textFieldEmailProperty = propertyGraph.lazyProperty { LicensingFacade.INSTANCE?.getLicenseeEmail().orEmpty() }
  private var ratingComponent: RatingComponent? = null
  private var missingRatingTooltip: JComponent? = null

  private var checkBoxEmail: JBCheckBox? = null

  private val textAreaRowSize = 5
  private val textFieldEmailColumnSize = 25
  private val textAreaFeedbackColumnSize = 42

  private val jsonConverter = Json { prettyPrint = true }

  init {
    init()
    title = NewUIFeedbackBundle.message("dialog.top.title")
    isResizable = false
  }

  override fun doOKAction() {
    super.doOKAction()
    val newUIInfoState = NewUIInfoService.getInstance().state
    newUIInfoState.feedbackSent = true
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

  override fun doCancelAction() {
    super.doCancelAction()
    CancelFeedbackNotification().notify(project)
  }

  private fun createRequestDescription(): String {
    return buildString {
      appendLine(NewUIFeedbackBundle.message("dialog.zendesk.title"))
      appendLine(NewUIFeedbackBundle.message("dialog.zendesk.description"))
      appendLine()
      appendLine(NewUIFeedbackBundle.message("dialog.zendesk.rating.label"))
      appendLine(" ${ratingProperty.get()}")
      appendLine()
      appendLine(NewUIFeedbackBundle.message("dialog.zendesk.like_most.textarea.label"))
      appendLine(textAreaLikeMostFeedbackProperty.get())
      appendLine()
      appendLine(NewUIFeedbackBundle.message("dialog.zendesk.dislike.textarea.label"))
      appendLine(textAreaDislikeFeedbackProperty.get())
      appendLine()
      appendLine()
      appendLine(newUISystemInfoData.value.toString())
    }
  }

  private fun createCollectedDataJsonString(): String {
    val collectedData = buildJsonObject {
      put(FEEDBACK_REPORT_ID_KEY, FEEDBACK_REPORT_ID_VALUE)
      put("format_version", FEEDBACK_JSON_VERSION)
      put("rating", ratingProperty.get())
      put("like_most", textAreaLikeMostFeedbackProperty.get())
      put("dislike", textAreaDislikeFeedbackProperty.get())
      put("system_info", jsonConverter.encodeToJsonElement(newUISystemInfoData.value))
    }
    return jsonConverter.encodeToString(collectedData)
  }

  override fun createCenterPanel(): JComponent {
    val mainPanel = panel {
      row {
        label(NewUIFeedbackBundle.message("dialog.title")).applyToComponent {
          font = JBFont.h1()
        }
      }
      row {
        cell(MultiLineLabel(NewUIFeedbackBundle.message("dialog.description")))
      }.bottomGap(BottomGap.SMALL)

      row {
        ratingComponent = RatingComponent().also {
          it.addPropertyChangeListener(RatingComponent.RATING_PROPERTY) { _ ->
            ratingProperty.set(it.myRating)
            missingRatingTooltip?.isVisible = false
          }
          cell(it)
            .label(NewUIFeedbackBundle.message("dialog.rating.label"), LabelPosition.TOP)
        }

        missingRatingTooltip = label(NewUIFeedbackBundle.message("dialog.rating.required")).applyToComponent {
          border = JBUI.Borders.compound(PopupBorder.Factory.createColored(JBUI.CurrentTheme.Validator.errorBorderColor()),
                                         JBUI.Borders.empty(JBUI.scale(4), JBUI.scale(8)))
          background = JBUI.CurrentTheme.Validator.errorBackgroundColor()
          isVisible = false
          isOpaque = true
        }.component
      }

      row {
        textArea()
          .bindText(textAreaLikeMostFeedbackProperty)
          .rows(textAreaRowSize)
          .columns(textAreaFeedbackColumnSize)
          .label(NewUIFeedbackBundle.message("dialog.like_most.textarea.label"), LabelPosition.TOP)
          .applyToComponent {
            adjustBehaviourForFeedbackForm()
          }
      }.bottomGap(BottomGap.MEDIUM)

      row {
        textArea()
          .bindText(textAreaDislikeFeedbackProperty)
          .rows(textAreaRowSize)
          .columns(textAreaFeedbackColumnSize)
          .label(NewUIFeedbackBundle.message("dialog.dislike.textarea.label"), LabelPosition.TOP)
          .applyToComponent {
            adjustBehaviourForFeedbackForm()
          }
      }.bottomGap(BottomGap.MEDIUM)

      row {
        checkBox(NewUIFeedbackBundle.message("dialog.email.checkbox.label"))
          .bindSelected(checkBoxEmailProperty).applyToComponent {
            checkBoxEmail = this
          }
      }.topGap(TopGap.MEDIUM)
      indent {
        row {
          textField().bindText(textFieldEmailProperty).columns(textFieldEmailColumnSize).applyToComponent {
            emptyText.text = NewUIFeedbackBundle.message("dialog.email.textfield.placeholder")
            isEnabled = checkBoxEmailProperty.get()

            checkBoxEmail?.addActionListener {
              isEnabled = checkBoxEmailProperty.get()
            }
            putClientProperty(TextComponentEmptyText.STATUS_VISIBLE_FUNCTION, Predicate<JBTextField> { it.text.isEmpty() })
          }.errorOnApply(NewUIFeedbackBundle.message("dialog.email.textfield.required")) {
            checkBoxEmailProperty.get() && it.text.isBlank()
          }.errorOnApply(ApplicationBundle.message("feedback.form.email.invalid")) {
            checkBoxEmailProperty.get() && it.text.isNotBlank() && !it.text.matches(Regex(".+@.+\\..+"))
          }
        }.bottomGap(BottomGap.MEDIUM)
      }

      row {
        feedbackAgreement(project) {
          showNewUIFeedbackSystemInfoDialog(project, newUISystemInfoData.value)
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
        putValue(NAME, NewUIFeedbackBundle.message("dialog.ok.label"))
      }

      override fun doAction(e: ActionEvent) {
        val ratingComponent = ratingComponent
        missingRatingTooltip?.isVisible = ratingComponent?.myRating == 0
        if (ratingComponent == null || ratingComponent.myRating != 0) {
          super.doAction(e)
        }
        else {
          enabled = false
          SwingUtilities.invokeLater {
            ratingComponent.requestFocusInWindow()
          }
        }
      }
    }
  }

  override fun getCancelAction(): Action {
    val cancelAction = super.getCancelAction()
    cancelAction.putValue(NAME, NewUIFeedbackBundle.message("dialog.cancel.label"))
    return cancelAction
  }
}

@Serializable
private data class NewUIFeedbackSystemInfoData(
  val isNewUINowEnabled: Boolean,
  val enableNewUIDate: LocalDateTime?,
  val disableNewUIDate: LocalDateTime?,
  val commonSystemInfo: CommonFeedbackSystemInfoData
) {
  override fun toString(): String {
    return buildString {
      appendLine(NewUIFeedbackBundle.message("dialog.system.info.isNewUIEnabled"))
      appendLine()
      appendLine(if (isNewUINowEnabled) "True" else "False")
      appendLine()
      appendLine(NewUIFeedbackBundle.message("dialog.system.info.enableNewUIDate"))
      appendLine()
      appendLine(enableNewUIDate?.date.toString())
      appendLine()
      appendLine(NewUIFeedbackBundle.message("dialog.system.info.disableNewUIDate"))
      appendLine()
      appendLine(disableNewUIDate?.date.toString())
      appendLine()
      commonSystemInfo.toString()
    }
  }
}

private fun showNewUIFeedbackSystemInfoDialog(project: Project?,
                                              systemInfoData: NewUIFeedbackSystemInfoData
) = showFeedbackSystemInfoDialog(project, systemInfoData.commonSystemInfo) {
  row(NewUIFeedbackBundle.message("dialog.system.info.isNewUIEnabled")) {
    label(if (systemInfoData.isNewUINowEnabled) "True" else "False") //NON-NLS
  }
  row(NewUIFeedbackBundle.message("dialog.system.info.enableNewUIDate")) {
    label(systemInfoData.enableNewUIDate?.date.toString())
  }
  row(NewUIFeedbackBundle.message("dialog.system.info.disableNewUIDate")) {
    label(systemInfoData.disableNewUIDate?.date.toString())
  }
}

private fun createNewUIFeedbackSystemInfoData(isNewUINowEnabled: Boolean,
                                              enableNewUIDate: LocalDateTime?,
                                              disableNewUIDate: LocalDateTime?): NewUIFeedbackSystemInfoData {
  return NewUIFeedbackSystemInfoData(isNewUINowEnabled, enableNewUIDate, disableNewUIDate,
                                     CommonFeedbackSystemInfoData.getCurrentData())
}