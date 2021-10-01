// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.feedback.dialog

import com.intellij.feedback.bundle.FeedbackBundle
import com.intellij.feedback.notification.ThanksForFeedbackNotification
import com.intellij.ide.BrowserUtil
import com.intellij.ide.feedback.RatingComponent
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.LicensingFacade
import com.intellij.ui.PopupBorder
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.GridLayout
import java.awt.Insets
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.BoxLayout
import javax.swing.BoxLayout.Y_AXIS
import javax.swing.JComponent
import javax.swing.JPanel

class ProjectCreationFeedbackDialog(
  private val project: Project?,
  private val createdProjectTypeName: String
) : DialogWrapper(project) {

  private val PRIVACY_POLICY_URL: String = "https://www.jetbrains.com/legal/docs/privacy/privacy.html"
  private val PRIVACY_POLICY_THIRD_PARTIES_URL = "https://www.jetbrains.com/legal/docs/privacy/third-parties.html"

  private val propertyGraph = PropertyGraph()
  private val ratingProperty = propertyGraph.graphProperty { 0 }
  private val checkBoxNoProblemProperty = propertyGraph.graphProperty { false }
  private val checkBoxEmptyProjectDontWorkProperty = propertyGraph.graphProperty { false }
  private val checkBoxHardFindDesireProjectProperty = propertyGraph.graphProperty { false }
  private val checkBoxFrameworkProperty = propertyGraph.graphProperty { false }
  private val textFieldFrameworkProperty = propertyGraph.graphProperty { "" }
  private val checkBoxOtherProperty = propertyGraph.graphProperty { false }
  private val textFieldOtherProblemProperty = propertyGraph.graphProperty { "" }
  private val textAreaOverallFeedbackProperty = propertyGraph.graphProperty { "" }
  private val checkBoxEmailProperty = propertyGraph.graphProperty { false }
  private val textFieldEmailProperty = propertyGraph.graphProperty { LicensingFacade.INSTANCE?.getLicenseeEmail().orEmpty() }
  private var ratingComponent: RatingComponent? = null
  private var missingRatingTooltip: JComponent? = null

  private var checkBoxOther: JBCheckBox? = null
  private var checkBoxEmail: JBCheckBox? = null

  private val textAreaRowSize = 4
  private val textFieldEmailColumnSize = 25

  init {
    init()
    title = FeedbackBundle.message("dialog.creation.project.top.title")
    isResizable = false
  }

  override fun doOKAction() {
    super.doOKAction()
    ThanksForFeedbackNotification().notify(project)
  }

  override fun createCenterPanel(): JComponent {
    val wrapperPanel = JPanel().apply {
      layout = BoxLayout(this, Y_AXIS)
      add(createInnerPanel())
      checkBoxFrameworkProperty.afterChange { _ ->
        this.requestFocus()
        this.removeAll()
        this.add(createInnerPanel())
        pack()
        this.revalidate()
        this.repaint()
        this.requestFocus()
      }
    }

    return wrapperPanel
  }

  private fun createInnerPanel(): DialogPanel {
    return panel {
      row {
        label(FeedbackBundle.message("dialog.creation.project.title")).also {
          it.component.font = JBFont.h1()
        }
      }
      row {
        label(FeedbackBundle.message("dialog.creation.project.description"))
        largeGapAfter()
      }

      row {
        label(FeedbackBundle.message("dialog.created.project.rating.label"))
      }
      row {
        cell {
          ratingComponent = RatingComponent().also {
            it.requestFocus()
            it.rating = ratingProperty.get()
            it.addPropertyChangeListener(RatingComponent.RATING_PROPERTY) { _ ->
              ratingProperty.set(it.rating)
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
          }
        }
        largeGapAfter()
      }

      row {
        label("")
      }

      row {
        label(FeedbackBundle.message("dialog.created.project.group.checkbox.title"))
      }

      row {
        checkBox(FeedbackBundle.message("dialog.created.project.checkbox.1.label"), checkBoxNoProblemProperty)
      }
      row {
        checkBox(FeedbackBundle.message("dialog.created.project.checkbox.2.label"), checkBoxEmptyProjectDontWorkProperty)
      }
      row {
        checkBox(FeedbackBundle.message("dialog.created.project.checkbox.3.label"), checkBoxHardFindDesireProjectProperty)
      }
      row {
        checkBox(FeedbackBundle.message("dialog.created.project.checkbox.4.label"), checkBoxFrameworkProperty)
        if (checkBoxFrameworkProperty.get()) {
          row {
            textField(textFieldFrameworkProperty).apply {
              this.component.emptyText.text = FeedbackBundle.message("dialog.created.project.checkbox.4.textfield.placeholder")
            }
          }
        }
      }
      row {
        cell {
          checkBox("", checkBoxOtherProperty).also {
            checkBoxOther = it.component
          }
          textField(textFieldOtherProblemProperty).also {
            it.component.isEnabled = checkBoxOtherProperty.get()
            it.component.emptyText.text = FeedbackBundle.message("dialog.created.project.checkbox.5.placeholder")
            checkBoxOther?.addChangeListener { _ ->
              it.component.isEnabled = checkBoxOtherProperty.get()
            }
          }.withErrorOnApplyIf(FeedbackBundle.message("dialog.created.project.checkbox.5.required")) {
            checkBoxOtherProperty.get() && it.text.isBlank()
          }
        }
        largeGapAfter()
      }

      row {
        label("")
      }

      row {
        cell(isVerticalFlow = true) {
          label(FeedbackBundle.message("dialog.created.project.textarea.label"))
          scrollableTextArea(textAreaOverallFeedbackProperty, rows = textAreaRowSize).apply {
            this.component.wrapStyleWord = true
            this.component.lineWrap = true
            this.component.margin = Insets(2, 6, 2, 6)
          }
        }
        largeGapAfter()
      }

      row {
        label("")
      }

      row {
        checkBox(FeedbackBundle.message("dialog.created.project.checkbox.email"), checkBoxEmailProperty).also {
          checkBoxEmail = it.component
        }

        row {
          textField(textFieldEmailProperty, columns = textFieldEmailColumnSize).also {
            it.component.emptyText.text = FeedbackBundle.message("dialog.created.project.textfield.email.placeholder")
            it.component.isEnabled = checkBoxEmailProperty.get()

            checkBoxEmail?.addActionListener { _ ->
              it.component.isEnabled = checkBoxEmailProperty.get()
            }
          }.withErrorOnApplyIf(FeedbackBundle.message("dialog.created.project.textfield.email.required")) {
            checkBoxEmailProperty.get() && it.text.isBlank()
          }.withErrorOnApplyIf(ApplicationBundle.message("feedback.form.email.invalid")) {
            checkBoxEmailProperty.get() && it.text.isNotBlank() && !it.text.matches(Regex(".+@.+\\..+"))
          }

          largeGapAfter()
        }
        largeGapAfter()
      }
      row {
        JPanel().apply {
          layout = GridLayout(3, 1, 0, 0)

          add(createLineOfConsent(FeedbackBundle.message("dialog.created.project.consent.1.1"),
            FeedbackBundle.message("dialog.created.project.consent.1.2"),
            FeedbackBundle.message("dialog.created.project.consent.1.3")) {
            ProjectCreationFeedbackSystemInfo(project, createdProjectTypeName).show()
          })

          add(createLineOfConsent(FeedbackBundle.message("dialog.created.project.consent.2.1"),
            FeedbackBundle.message("dialog.created.project.consent.2.2"),
            FeedbackBundle.message("dialog.created.project.consent.2.3")) {
            BrowserUtil.browse(PRIVACY_POLICY_THIRD_PARTIES_URL, project)
          })

          add(createLineOfConsent("",
            FeedbackBundle.message("dialog.created.project.consent.3.2"),
            FeedbackBundle.message("dialog.created.project.consent.3.3")) {
            BrowserUtil.browse(PRIVACY_POLICY_URL, project)
          })
        }()
        largeGapAfter()
      }
    }.also {
      it.border = JBEmptyBorder(15, 10, 0, 10)
    }
  }

  private fun createLineOfConsent(prefixTest: String, linkText: String, postfix: String, action: () -> Unit): HyperlinkLabel {
    val text = HtmlBuilder()
      .append(prefixTest) //NON-NLS
      .append(HtmlChunk.tag("hyperlink")
        .addText(linkText)) //NON-NLS
      .append(postfix) //NON-NLS
    val label = HyperlinkLabel().apply {
      setTextWithHyperlink(text.toString())
      addHyperlinkListener {
        action()
      }
      foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
      minimumSize = Dimension(preferredSize.width, minimumSize.height)
    }
    UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, label)

    return label
  }

  override fun createActions(): Array<Action> {
    return arrayOf(cancelAction, okAction)
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