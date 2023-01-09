// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.feedback

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.AboutDialog
import com.intellij.ide.actions.ReportProblemAction
import com.intellij.ide.actions.SendFeedbackAction
import com.intellij.notification.Notification
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.application.impl.ZenDeskForm
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.LicensingFacade
import com.intellij.ui.PopupBorder
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.TextComponentEmptyText
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.util.function.Predicate
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.event.HyperlinkEvent

private data class ZenDeskComboOption(val displayName: @Nls String, val id: String) {
  override fun toString(): String = displayName
}

private val topicOptions = listOf(
  ZenDeskComboOption(ApplicationBundle.message("feedback.form.topic.bug"), "ij_bug"),
  ZenDeskComboOption(ApplicationBundle.message("feedback.form.topic.howto"), "ij_howto"),
  ZenDeskComboOption(ApplicationBundle.message("feedback.form.topic.problem"), "ij_problem"),
  ZenDeskComboOption(ApplicationBundle.message("feedback.form.topic.suggestion"), "ij_suggestion"),
  ZenDeskComboOption(ApplicationBundle.message("feedback.form.topic.misc"), "ij_misc")
)

class FeedbackForm(
  private val project: Project?,
  val form: ZenDeskForm,
  val isEvaluation: Boolean
) : DialogWrapper(project, false) {
  private var details = ""
  private var email = LicensingFacade.INSTANCE?.getLicenseeEmail().orEmpty()
  private var needSupport = false
  private var shareSystemInformation = false
  private var ratingComponent: RatingComponent? = null
  private var missingRatingTooltip: JComponent? = null
  private var topic: ZenDeskComboOption? = null
  private lateinit var topicComboBox: JComboBox<ZenDeskComboOption?>

  init {
    title = if (isEvaluation) ApplicationBundle.message("feedback.form.title") else ApplicationBundle.message("feedback.form.prompt")
    init()
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      if (isEvaluation) {
        row {
          label(ApplicationBundle.message("feedback.form.evaluation.prompt")).applyToComponent {
            font = JBFont.h1()
          }
        }
        row {
          label(ApplicationBundle.message("feedback.form.comment"))
        }
        row {
          label(ApplicationBundle.message("feedback.form.rating", ApplicationNamesInfo.getInstance().fullProductName))
        }
        row {
          ratingComponent = RatingComponent().also {
            it.addPropertyChangeListener { evt ->
              if (evt.propertyName == RatingComponent.RATING_PROPERTY) {
                missingRatingTooltip?.isVisible = false
              }
            }
            cell(it)
          }

          missingRatingTooltip = label(ApplicationBundle.message("feedback.form.rating.required")).applyToComponent {
            border = JBUI.Borders.compound(PopupBorder.Factory.createColored(JBUI.CurrentTheme.Validator.errorBorderColor()),
              JBUI.Borders.empty(4, 8))
            background = JBUI.CurrentTheme.Validator.errorBackgroundColor()
            isVisible = false
            isOpaque = true
          }.component
        }
      }
      else {
        row {
          topicComboBox = comboBox(CollectionComboBoxModel(topicOptions))
            .label(ApplicationBundle.message("feedback.form.topic"), LabelPosition.TOP)
            .bindItem({ topic }, { topic = it})
            .errorOnApply(ApplicationBundle.message("feedback.form.topic.required")) {
              it.selectedItem == null
            }.component

          icon(AllIcons.General.BalloonInformation)
            .gap(RightGap.SMALL)
            .visibleIf(topicComboBox.selectedValueMatches { it?.id == "ij_bug" })
          text(ApplicationBundle.message("feedback.form.issue")) {
            ReportProblemAction.submit(project)
          }.visibleIf(topicComboBox.selectedValueMatches { it?.id == "ij_bug" })
        }
      }
      row {
        val label = if (isEvaluation) ApplicationBundle.message("feedback.form.evaluation.details") else ApplicationBundle.message("feedback.form.details")
        textArea()
          .label(label, LabelPosition.TOP)
          .bindText(::details)
          .align(Align.FILL)
          .rows(5)
          .focused()
          .errorOnApply(ApplicationBundle.message("feedback.form.details.required")) {
            it.text.isBlank()
          }
          .applyToComponent {
            emptyText.text = if (isEvaluation)
              ApplicationBundle.message("feedback.form.evaluation.details.emptyText")
            else
              ApplicationBundle.message("feedback.form.details.emptyText")
            putClientProperty(TextComponentEmptyText.STATUS_VISIBLE_FUNCTION, Predicate<JBTextArea> { it.text.isEmpty() })
            addKeyListener(object : KeyAdapter() {
              override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_TAB) {
                  if ((e.modifiersEx and KeyEvent.SHIFT_DOWN_MASK) != 0) {
                    transferFocusBackward()
                  }
                  else {
                    transferFocus()
                  }
                  e.consume()
                }
              }
            })
          }
      }.resizableRow()

      row {
        textField()
          .label(ApplicationBundle.message("feedback.form.email"), LabelPosition.TOP)
          .bindText(::email)
          .columns(COLUMNS_MEDIUM)
          .errorOnApply(ApplicationBundle.message("feedback.form.email.required")) { it.text.isBlank() }
          .errorOnApply(ApplicationBundle.message("feedback.form.email.invalid")) { !it.text.matches(Regex(".+@.+\\..+")) }
      }
      row {
        checkBox(ApplicationBundle.message("feedback.form.need.support"))
          .bindSelected(::needSupport)
      }
      row {
        checkBox(ApplicationBundle.message("feedback.form.share.system.information"))
          .bindSelected(::shareSystemInformation)
          .gap(RightGap.SMALL)
        @Suppress("DialogTitleCapitalization")
        link(ApplicationBundle.message("feedback.form.share.system.information.link")) {
          showSystemInformation()
        }
      }
      row {
        comment(ApplicationBundle.message("feedback.form.consent"))
      }
    }
  }

  private fun showSystemInformation() {
    val systemInfo = AboutDialog(project).extendedAboutText
    val scrollPane = JBScrollPane(JBTextArea(systemInfo))
    dialog(ApplicationBundle.message("feedback.form.system.information.title"), scrollPane, createActions = {
      listOf(object : AbstractAction(CommonBundle.getCloseButtonText()) {
        init {
          putValue(DEFAULT_ACTION, true)
        }

        override fun actionPerformed(event: ActionEvent) {
          val wrapper = findInstance(event.source as? Component)
          wrapper?.close(OK_EXIT_CODE)
        }
      })
    }).show()
  }

  override fun getOKAction(): Action {
    return object : DialogWrapper.OkAction() {
      init {
        putValue(Action.NAME, ApplicationBundle.message("feedback.form.ok"))
      }

      override fun doAction(e: ActionEvent) {
        val ratingComponent = ratingComponent
        missingRatingTooltip?.isVisible = ratingComponent?.myRating == 0
        if (ratingComponent == null || ratingComponent.myRating != 0) {
          super.doAction(e)
        }
        else {
          enabled = false
        }
      }
    }
  }

  override fun getCancelAction(): Action {
    return super.getCancelAction().apply {
      if (isEvaluation) {
        putValue(Action.NAME, ApplicationBundle.message("feedback.form.cancel"))
      }
    }
  }

  override fun doOKAction() {
    super.doOKAction()
    val systemInfo = if (shareSystemInformation) AboutDialog(project).extendedAboutText else ""
    ApplicationManager.getApplication().executeOnPooledThread {
      ZenDeskRequests().submit(
        form,
        email,
        ApplicationNamesInfo.getInstance().fullProductName + " Feedback",
        details.ifEmpty { "No details" },
        mapOf(
          "systeminfo" to systemInfo,
          "needsupport" to needSupport
        ) + (ratingComponent?.let { mapOf("rating" to it.myRating) } ?: mapOf()) + (topic?.let { mapOf("topic" to it.id) } ?: emptyMap())
        , onDone = {
        ApplicationManager.getApplication().invokeLater {
          var message = ApplicationBundle.message("feedback.form.thanks", ApplicationNamesInfo.getInstance().fullProductName)
          if (isEvaluation) {
            message += "<br/>" + ApplicationBundle.message("feedback.form.share.later")
          }
          Notification("feedback.form",
                       ApplicationBundle.message("feedback.form.thanks.title"),
                       message,
                       NotificationType.INFORMATION).notify(project)
        }
      }, onError = {
        ApplicationManager.getApplication().invokeLater {
          Notification("feedback.form",
                       ApplicationBundle.message("feedback.form.error.title"),
                       ApplicationBundle.message("feedback.form.error.text"),
                       NotificationType.ERROR
          )
            .setListener(object : NotificationListener.Adapter() {
              override fun hyperlinkActivated(notification: Notification, e: HyperlinkEvent) {
                SendFeedbackAction.submit(project)
              }
            })
            .notify(project)
        }
      })
    }
  }

  override fun doCancelAction() {
    super.doCancelAction()
    if (isEvaluation) {
      Notification("feedback.form", ApplicationBundle.message("feedback.form.share.later"), NotificationType.INFORMATION).notify(project)
    }
  }
}
