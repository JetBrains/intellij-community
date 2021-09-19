// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.feedback.dialog

import com.intellij.feedback.bundle.FeedbackBundle
import com.intellij.ide.feedback.RatingComponent
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.LicensingFacade
import com.intellij.ui.PopupBorder
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JComponent

class ProjectCreationFeedbackDialog(
  private val project: Project?,
  private val createdProjectTypeName: String
) : DialogWrapper(project) {

  private val propertyGraph = PropertyGraph()
  private val checkBoxOtherProblemProperty = propertyGraph.graphProperty { "" }
  private val textAreaOverallFeedbackProperty = propertyGraph.graphProperty { "" }
  private val textFieldEmailProperty = propertyGraph.graphProperty { "" }
  private var ratingComponent: RatingComponent? = null
  private var missingRatingTooltip: JComponent? = null

  private var checkBoxOther: JBCheckBox? = null
  private var checkBoxEmail: JBCheckBox? = null

  private val textAreaRowSize = 8
  private val textFieldEmailColumnSize = 25

  init {
    init()
  }

  override fun createCenterPanel(): JComponent {
    val dialogPanel = panel {
      row {
        label(FeedbackBundle.message("dialog.creation.project.title")).also {
          it.component.font = JBFont.h1()
        }
        largeGapAfter()
      }

      row {
        cell(isVerticalFlow = true) {
          label(FeedbackBundle.message("dialog.created.project.rating.label"))
          ratingComponent = RatingComponent().also {
            it.addPropertyChangeListener(RatingComponent.RATING_PROPERTY) { _ ->
              missingRatingTooltip?.isVisible = false
            }
            it()
          }

          missingRatingTooltip = JBLabel(FeedbackBundle.message("dialog.created.project.rating.required")).apply {
            border = JBUI.Borders.compound(PopupBorder.Factory.createColored(JBUI.CurrentTheme.Validator.errorBorderColor()),
              JBUI.Borders.empty(4, 8))
            background = JBUI.CurrentTheme.Validator.errorBackgroundColor()
            isVisible = false
            isOpaque = true
            this().withLargeLeftGap()
          }
        }
      }

      row {
        label(FeedbackBundle.message("dialog.created.project.group.checkbox.title"))
      }

      row {
        checkBox(FeedbackBundle.message("dialog.created.project.checkbox.1.label")).also {
          it.component.isEnabled = false
          ratingComponent?.addPropertyChangeListener(RatingComponent.RATING_PROPERTY) { evt ->
            it.component.isEnabled = evt.newValue == 5
          }
        }
      }
      row {
        checkBox(FeedbackBundle.message("dialog.created.project.checkbox.2.label"))
      }
      row {
        checkBox(FeedbackBundle.message("dialog.created.project.checkbox.3.label"))
      }
      row {
        checkBox(FeedbackBundle.message("dialog.created.project.checkbox.4.label"))
      }
      row {
        cell {
          checkBox("").also {
            checkBoxOther = it.component
          }
          textField(checkBoxOtherProblemProperty).also {
            it.component.isEnabled = false
            it.component.emptyText.text = FeedbackBundle.message("dialog.created.project.checkbox.5.placeholder")
            checkBoxOther?.addChangeListener { _ ->
              it.component.isEnabled = checkBoxOther?.isSelected == true
            }

          }
        }
        largeGapAfter()
      }

      row {
        cell(isVerticalFlow = true) {
          label(FeedbackBundle.message("dialog.created.project.textarea.label"))
          scrollableTextArea(textAreaOverallFeedbackProperty, rows = textAreaRowSize)
        }
        largeGapAfter()
      }

      row {
        cell {
          checkBox(FeedbackBundle.message("dialog.created.project.checkbox.email")).also {
            checkBoxEmail = it.component
          }
        }
      }
      row {
        textField(textFieldEmailProperty, columns = textFieldEmailColumnSize).also {
          it.component.text = LicensingFacade.INSTANCE?.getLicenseeEmail().orEmpty()
          it.component.emptyText.text = FeedbackBundle.message("dialog.created.project.textfield.email.placeholder")
          it.component.isEnabled = false

          it.withLargeLeftGap()
          checkBoxEmail?.addChangeListener { _ ->
            it.component.isEnabled = checkBoxEmail?.isSelected == true
          }
        }.withErrorOnApplyIf(FeedbackBundle.message("dialog.created.project.textfield.email.required")) {
          checkBoxEmail?.isSelected == true && it.text.isBlank()
        }.withErrorOnApplyIf(ApplicationBundle.message("feedback.form.email.invalid")) {
          checkBoxEmail?.isSelected == true && it.text.isNotBlank() && !it.text.matches(Regex(".+@.+\\..+"))
        }

        largeGapAfter()
      }

      row {
        label(FeedbackBundle.message("dialog.created.project.consent"), style = UIUtil.ComponentStyle.SMALL).also {
          it.component.foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
          it.component.minimumSize = Dimension(it.component.preferredSize.width, it.component.minimumSize.height)
        }
      }
    }

    return dialogPanel
  }

  override fun getOKAction(): Action {
    return object : DialogWrapper.OkAction() {
      init {
        putValue(Action.NAME, FeedbackBundle.message("dialog.created.project.ok"))
      }

      override fun doAction(e: ActionEvent) {
        val ratingComponent = ratingComponent
        missingRatingTooltip?.isVisible = ratingComponent?.rating == 0
        if (ratingComponent == null || ratingComponent.rating != 0) {
          super.doAction(e)
        }
        else {
          enabled = false
        }
      }
    }
  }
}