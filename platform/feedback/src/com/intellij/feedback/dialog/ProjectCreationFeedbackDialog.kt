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
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.LicensingFacade
import com.intellij.ui.PopupBorder
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.GridLayout
import java.awt.event.ActionEvent
import javax.swing.Action
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
  private val textFieldNoFrameworkColumnSize = 41
  private val textFieldOtherColumnSize = 41
  private val textAreaOverallFeedbackColumnSize = 42

  private val checkBoxFrameworkComponentPredicate = object : ComponentPredicate() {
    override fun addListener(listener: (Boolean) -> Unit) {
      checkBoxFrameworkProperty.afterChange {
        listener(checkBoxFrameworkProperty.get())
      }
    }

    override fun invoke(): Boolean {
      return checkBoxFrameworkProperty.get()
    }
  }

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
    return panel {
      row {
        label(FeedbackBundle.message("dialog.creation.project.title")).applyToComponent {
          font = JBFont.h1()
        }
      }
      row {
        label(FeedbackBundle.message("dialog.creation.project.description"))
      }.bottomGap(BottomGap.MEDIUM)

      row {
        ratingComponent = RatingComponent().also {
          it.requestFocus()
          it.rating = ratingProperty.get()
          it.addPropertyChangeListener(RatingComponent.RATING_PROPERTY) { _ ->
            ratingProperty.set(it.rating)
            missingRatingTooltip?.isVisible = false
          }
          cell(it).label(FeedbackBundle.message("dialog.created.project.rating.label"), LabelPosition.TOP)
        }

        missingRatingTooltip = label(FeedbackBundle.message("dialog.created.project.rating.required")).applyToComponent {
          border = JBUI.Borders.compound(PopupBorder.Factory.createColored(JBUI.CurrentTheme.Validator.errorBorderColor()),
            JBUI.Borders.empty(JBUI.scale(4), JBUI.scale(8)))
          background = JBUI.CurrentTheme.Validator.errorBackgroundColor()
          isVisible = false
          isOpaque = true
        }.component
      }.bottomGap(BottomGap.MEDIUM)

      row {
        checkBox(FeedbackBundle.message("dialog.created.project.checkbox.1.label")).bindSelected(checkBoxNoProblemProperty)
          .label(FeedbackBundle.message("dialog.created.project.group.checkbox.title"), LabelPosition.TOP)
      }.topGap(TopGap.MEDIUM)
      row {
        checkBox(FeedbackBundle.message("dialog.created.project.checkbox.2.label")).bindSelected(checkBoxEmptyProjectDontWorkProperty)
      }
      row {
        checkBox(FeedbackBundle.message("dialog.created.project.checkbox.3.label")).bindSelected(checkBoxHardFindDesireProjectProperty)
      }
      row {
        checkBox(FeedbackBundle.message("dialog.created.project.checkbox.4.label")).bindSelected(checkBoxFrameworkProperty)
      }
      indent {
        row {
          textField()
            .bindText(textFieldFrameworkProperty)
            .columns(textFieldNoFrameworkColumnSize).applyToComponent {
              emptyText.text = FeedbackBundle.message("dialog.created.project.checkbox.4.textfield.placeholder")
            }
        }.visibleIf(checkBoxFrameworkComponentPredicate)
      }

      row {
        checkBox("").bindSelected(checkBoxOtherProperty).applyToComponent {
          checkBoxOther = this
        }.customize(Gaps(right = JBUI.scale(4)))

        textField()
          .bindText(textFieldOtherProblemProperty)
          .columns(textFieldOtherColumnSize)
          .errorOnApply(FeedbackBundle.message("dialog.created.project.checkbox.5.required")) {
            checkBoxOtherProperty.get() && it.text.isBlank()
          }
          .applyToComponent {
            isEnabled = checkBoxOtherProperty.get()
            emptyText.text = FeedbackBundle.message("dialog.created.project.checkbox.5.placeholder")
            checkBoxOther?.addChangeListener { _ ->
              isEnabled = checkBoxOtherProperty.get()
            }
          }
      }.bottomGap(BottomGap.MEDIUM)

      row {
        textArea()
          .bindText(textAreaOverallFeedbackProperty)
          .rows(textAreaRowSize)
          .columns(textAreaOverallFeedbackColumnSize)
          .label(FeedbackBundle.message("dialog.created.project.textarea.label"), LabelPosition.TOP)
          .applyToComponent {
            wrapStyleWord = true
            lineWrap = true
          }
      }.bottomGap(BottomGap.MEDIUM).topGap(TopGap.SMALL)

      row {
        checkBox(FeedbackBundle.message("dialog.created.project.checkbox.email"))
          .bindSelected(checkBoxEmailProperty).applyToComponent {
            checkBoxEmail = this
          }
      }.topGap(TopGap.MEDIUM)
      indent {
        row {
          textField().bindText(textFieldEmailProperty).columns(textFieldEmailColumnSize).applyToComponent {
            emptyText.text = FeedbackBundle.message("dialog.created.project.textfield.email.placeholder")
            isEnabled = checkBoxEmailProperty.get()

            checkBoxEmail?.addActionListener { _ ->
              isEnabled = checkBoxEmailProperty.get()
            }
          }.errorOnApply(FeedbackBundle.message("dialog.created.project.textfield.email.required")) {
            checkBoxEmailProperty.get() && it.text.isBlank()
          }.errorOnApply(ApplicationBundle.message("feedback.form.email.invalid")) {
            checkBoxEmailProperty.get() && it.text.isNotBlank() && !it.text.matches(Regex(".+@.+\\..+"))
          }
        }.bottomGap(BottomGap.MEDIUM)
      }

      row {
        cell(JPanel().apply {
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
        })
      }.bottomGap(BottomGap.MEDIUM).topGap(TopGap.MEDIUM)
    }.also { dialog ->
      dialog.border = JBEmptyBorder(JBUI.scale(15), JBUI.scale(10), JBUI.scale(0), JBUI.scale(10))
      checkBoxFrameworkProperty.afterChange {
        pack()
        repaint()
      }
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