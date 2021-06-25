// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.feedback

import com.intellij.CommonBundle
import com.intellij.ide.actions.AboutDialog
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.LicensingFacade
import com.intellij.ui.PopupBorder
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent

class EvaluationFeedbackForm(private val project: Project?) : DialogWrapper(project, false) {
  private var details = ""
  private var email = LicensingFacade.INSTANCE.getLicenseeEmail().orEmpty()
  private var needSupport = false
  private var shareSystemInformation = false
  private lateinit var ratingComponent: RatingComponent
  private lateinit var missingRatingTooltip: JComponent

  init {
    title = ApplicationBundle.message("feedback.form.title")
    init()
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      row {
        label(ApplicationBundle.message("feedback.form.prompt")).also {
          it.component.font = JBFont.h3().asBold()
        }
      }
      row {
        label(ApplicationBundle.message("feedback.form.comment")).also {
          it.component.foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
        }
      }
      row {
        label(ApplicationBundle.message("feedback.form.rating", ApplicationNamesInfo.getInstance().fullProductName))
      }
      row {
        cell {
          ratingComponent = RatingComponent().also {
            it.addPropertyChangeListener { evt ->
              if (evt.propertyName == "rating") {
                missingRatingTooltip.isVisible = false
              }
            }
          }
          ratingComponent()

          missingRatingTooltip = JBLabel(ApplicationBundle.message("feedback.form.rating.required")).apply {
            border = JBUI.Borders.compound(PopupBorder.Factory.createColored(JBUI.CurrentTheme.Validator.errorBorderColor()),
                                           JBUI.Borders.empty(4, 8))
            background = JBUI.CurrentTheme.Validator.errorBackgroundColor()
            isVisible = false
            isOpaque = true
          }
          missingRatingTooltip().withLargeLeftGap()
        }
      }
      row {
        label(ApplicationBundle.message("feedback.form.details"))
      }
      row {
        scrollableTextArea(::details, rows = 5).also {
          it.component.emptyText.text = ApplicationBundle.message("feedback.form.details.emptyText")

          it.component.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
              if (e.keyCode == KeyEvent.VK_TAB) {
                if ((e.modifiersEx and KeyEvent.SHIFT_DOWN_MASK) != 0) {
                  it.component.transferFocusBackward()
                }
                else {
                  it.component.transferFocus()
                }
                e.consume()
              }
            }
          })
        }
      }
      row {
        label(ApplicationBundle.message("feedback.form.email"))
      }
      row {
        textField(::email, columns = 25)
          .focused()
          .withErrorOnApplyIf(ApplicationBundle.message("feedback.form.email.required")) { it.text.isBlank() }

        checkBox(ApplicationBundle.message("feedback.form.need.support"), ::needSupport)
      }
      row {
        cell {
          checkBox(ApplicationBundle.message("feedback.form.share.system.information"), ::shareSystemInformation)
          HyperlinkLabel(ApplicationBundle.message("feedback.form.share.system.information.link"))().also {
            it.component.addHyperlinkListener {
              showSystemInformation()
            }
          }
        }
      }
      commentRow(ApplicationBundle.message("feedback.form.consent"))
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
        missingRatingTooltip.isVisible = ratingComponent.rating == 0
        if (ratingComponent.rating != 0) {
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
      putValue(Action.NAME, ApplicationBundle.message("feedback.form.cancel"))
    }
  }

  override fun doOKAction() {
    super.doOKAction()
    ApplicationManager.getApplication().executeOnPooledThread {
      ZenDeskRequests().submit(
        email,
        ApplicationNamesInfo.getInstance().fullProductName + " Feedback",
        details.ifEmpty { "No details" },
        ratingComponent.rating
      )
    }
  }
}
