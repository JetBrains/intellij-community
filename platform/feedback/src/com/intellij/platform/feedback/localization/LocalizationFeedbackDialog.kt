// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.localization

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ex.MultiLineLabel
import com.intellij.platform.feedback.dialog.*
import com.intellij.platform.feedback.dialog.components.RatingComponent
import com.intellij.platform.feedback.impl.FEEDBACK_REPORT_ID_KEY
import com.intellij.platform.feedback.impl.FeedbackRequestData
import com.intellij.platform.feedback.impl.FeedbackRequestType
import com.intellij.platform.feedback.impl.bundle.CommonFeedbackBundle
import com.intellij.platform.feedback.impl.notification.ThanksForFeedbackNotification
import com.intellij.platform.feedback.impl.submitFeedback
import com.intellij.ui.PopupBorder
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import kotlinx.serialization.json.*
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.SwingUtilities

internal class LocalizationFeedbackDialog(
  private val myProject: Project?,
  private val forTest: Boolean
) : DialogWrapper(myProject) {
  private val feedbackJsonVersion = COMMON_FEEDBACK_SYSTEM_INFO_VERSION + 0 // different version for the form itself
  private val feedbackReportId = "localization_feedback"

  private var ratingComponent: RatingComponent? = null
  private var missingRatingTooltip: JComponent? = null
  private val propertyGraph = PropertyGraph()
  private val ratingProperty = propertyGraph.property(0)
  private val textAreaLikeMostFeedbackProperty = propertyGraph.property("")
  private val textAreaRowSize = 5
  private val textAreaFeedbackColumnSize = 42

  private val jsonConverter = Json { prettyPrint = true }

  init {
    init()
    title = LocalizationFeedbackBundle.message("dialog.top.title")
    isResizable = false
  }

  private val systemData by lazy { CommonFeedbackSystemData.getCurrentData() }

  override fun createCenterPanel() = panel {
    row {
      label(LocalizationFeedbackBundle.message("dialog.title")).applyToComponent {
        font = JBFont.h1()
      }
    }

    row {
      cell(MultiLineLabel(LocalizationFeedbackBundle.message("dialog.description")))
    }.bottomGap(BottomGap.SMALL)

    row {
      ratingComponent = RatingComponent().also {
        it.addPropertyChangeListener(RatingComponent.RATING_PROPERTY) { _ ->
          ratingProperty.set(it.myRating)
          missingRatingTooltip?.isVisible = false
        }
        cell(it)
          .label(LocalizationFeedbackBundle.message("dialog.rating.label"), LabelPosition.TOP)
      }

      missingRatingTooltip = label(CommonFeedbackBundle.message("dialog.feedback.rating.required")).applyToComponent {
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
        .label(LocalizationFeedbackBundle.message("dialog.input.label"), LabelPosition.TOP)
        .applyToComponent {
          adjustBehaviourForFeedbackForm()
        }
    }.bottomGap(BottomGap.MEDIUM)

    row {
      feedbackAgreement(myProject, LocalizationFeedbackBundle.message("dialog.feedback.consent.withEmail")) {
        showFeedbackSystemInfoDialog(myProject, systemData)
      }
    }.bottomGap(BottomGap.SMALL)
  }.also { dialog ->
    dialog.border = JBEmptyBorder(JBUI.scale(15), JBUI.scale(10), JBUI.scale(0), JBUI.scale(10))
  }

  override fun createActions(): Array<Action> {
    return arrayOf(cancelAction, okAction)
  }

  override fun doOKAction() {
    super.doOKAction()
    val feedbackData = FeedbackRequestData(feedbackReportId, createCollectedDataJsonString())
    submitFeedback(feedbackData,
                   { }, { },
                   if (forTest || System.getProperty(
                       "ide.feedback.localization.test")?.toBoolean() == true) FeedbackRequestType.TEST_REQUEST
                   else FeedbackRequestType.PRODUCTION_REQUEST)
    ThanksForFeedbackNotification(description = LocalizationFeedbackBundle.message("notification.thanks.feedback.content")).notify(
      myProject)
  }

  private fun createCollectedDataJsonString(): JsonObject {
    val collectedData = buildJsonObject {
      put(FEEDBACK_REPORT_ID_KEY, feedbackReportId)
      put("format_version", feedbackJsonVersion)
      put("rating", ratingProperty.get())
      put("comment", textAreaLikeMostFeedbackProperty.get())
      put("language_pack", LocalizationFeedbackService.getInstance().getLanguagePack().toString())
      put("system_info", jsonConverter.encodeToJsonElement(systemData))
    }
    return collectedData
  }

  override fun getOKAction(): Action {
    return object : OkAction() {
      init {
        putValue(NAME, LocalizationFeedbackBundle.message("dialog.button.send"))
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
}

private class ShowLocalizationFeedbackDialog : AnAction("Test Localization Dialog") { // NON-NLS
  override fun actionPerformed(e: AnActionEvent) {
    LocalizationFeedbackNotificationService.getInstance().showNotification()
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}