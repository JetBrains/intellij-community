// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.feedback.dialog

import com.intellij.feedback.*
import com.intellij.feedback.bundle.FeedbackBundle
import com.intellij.feedback.state.projectCreation.ProjectCreationInfoService
import com.intellij.ide.feedback.RatingComponent
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.LicensingFacade
import com.intellij.ui.PopupBorder
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.TextComponentEmptyText
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.awt.event.ActionEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.util.function.Predicate
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.SwingUtilities

class ProjectCreationFeedbackDialog(
  private val project: Project?,
  createdProjectTypeName: String,
  private val forTest: Boolean
) : DialogWrapper(project) {

  /** Increase the additional number when onboarding feedback format is changed */
  private val FEEDBACK_JSON_VERSION = COMMON_FEEDBACK_SYSTEM_INFO_VERSION + 0
  
  private val TICKET_TITLE_ZENDESK = "Project Creation Feedback"
  private val FEEDBACK_TYPE_ZENDESK = "Project Creation Feedback"

  private val NO_PROBLEM = "No problem"
  private val EMPTY_PROJECT = "Empty project"
  private val HARD_TO_FIND = "Hard to find"
  private val LACK_OF_FRAMEWORK = "Lack of framework"
  private val OTHER = "Other"

  private val systemInfoData: ProjectCreationFeedbackSystemInfoData = createProjectCreationFeedbackSystemInfoData(createdProjectTypeName)

  private val propertyGraph = PropertyGraph()
  private val ratingProperty = propertyGraph.graphProperty { 0 }
  private val checkBoxNoProblemProperty = propertyGraph.graphProperty { false }
  private val checkBoxEmptyProjectDontWorkProperty = propertyGraph.graphProperty { false }
  private val checkBoxHardFindDesireProjectProperty = propertyGraph.graphProperty { false }
  private val checkBoxFrameworkProperty = propertyGraph.graphProperty { false }
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
  private val textFieldOtherColumnSize = 41
  private val textAreaOverallFeedbackColumnSize = 42

  private val jsonConverter = Json { prettyPrint = true }

  init {
    init()
    title = FeedbackBundle.message("dialog.creation.project.top.title")
    isResizable = false
  }

  override fun doCancelAction() {
    super.doCancelAction()
  }

  override fun doOKAction() {
    super.doOKAction()
    val projectCreationInfoState = ProjectCreationInfoService.getInstance().state
    projectCreationInfoState.feedbackSent = true
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
      appendLine(FeedbackBundle.message("dialog.creation.project.zendesk.title"))
      appendLine(FeedbackBundle.message("dialog.creation.project.zendesk.description"))
      appendLine()
      appendLine(FeedbackBundle.message("dialog.created.project.zendesk.rating.label"))
      appendLine(" ${ratingProperty.get()}")
      appendLine()
      appendLine(FeedbackBundle.message("dialog.created.project.zendesk.problems.title"))
      appendLine(createProblemsList())
      appendLine()
      appendLine(FeedbackBundle.message("dialog.created.project.zendesk.overallExperience.label"))
      appendLine(textAreaOverallFeedbackProperty.get())
    }
  }

  private fun createProblemsList(): String {
    val resultProblemsList = mutableListOf<String>()
    if (checkBoxNoProblemProperty.get()) {
      resultProblemsList.add(FeedbackBundle.message("dialog.created.project.zendesk.problem.1.label"))
    }
    if (checkBoxEmptyProjectDontWorkProperty.get()) {
      resultProblemsList.add(FeedbackBundle.message("dialog.created.project.zendesk.problem.2.label"))
    }
    if (checkBoxHardFindDesireProjectProperty.get()) {
      resultProblemsList.add(FeedbackBundle.message("dialog.created.project.zendesk.problem.3.label"))
    }
    if (checkBoxFrameworkProperty.get()) {
      resultProblemsList.add(FeedbackBundle.message("dialog.created.project.zendesk.problem.4.label"))
    }
    if (checkBoxOtherProperty.get()) {
      resultProblemsList.add(textFieldOtherProblemProperty.get())
    }
    return resultProblemsList.joinToString(prefix = "- ", separator = "\n- ")
  }

  private fun createCollectedDataJsonString(): String {
    val collectedData = buildJsonObject {
      put(FEEDBACK_REPORT_ID_KEY, "new_project_creation_dialog")
      put("format_version", FEEDBACK_JSON_VERSION)
      put("rating", ratingProperty.get())
      put("project_type", systemInfoData.createdProjectTypeName)
      putJsonArray("problems") {
        if (checkBoxNoProblemProperty.get()) {
          add(createProblemJsonObject(NO_PROBLEM))
        }
        if (checkBoxEmptyProjectDontWorkProperty.get()) {
          add(createProblemJsonObject(EMPTY_PROJECT))
        }
        if (checkBoxHardFindDesireProjectProperty.get()) {
          add(createProblemJsonObject(HARD_TO_FIND))
        }
        if (checkBoxFrameworkProperty.get()) {
          add(createProblemJsonObject(LACK_OF_FRAMEWORK))
        }
        if (checkBoxOtherProperty.get()) {
          add(createProblemJsonObject(OTHER, textFieldOtherProblemProperty.get()))
        }
      }
      put("overall_exp", textAreaOverallFeedbackProperty.get())
      put("system_info", jsonConverter.encodeToJsonElement(systemInfoData.commonSystemInfo))
    }
    return jsonConverter.encodeToString(collectedData)
  }

  private fun createProblemJsonObject(name: String, description: String? = null): JsonObject {
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
        label(FeedbackBundle.message("dialog.creation.project.title")).applyToComponent {
          font = JBFont.h1()
        }
      }
      row {
        label(FeedbackBundle.message("dialog.creation.project.description"))
      }.bottomGap(BottomGap.MEDIUM)

      row {
        ratingComponent = RatingComponent().also {
          it.addPropertyChangeListener(RatingComponent.RATING_PROPERTY) { _ ->
            ratingProperty.set(it.myRating)
            missingRatingTooltip?.isVisible = false
          }
          cell(it)
            .label(FeedbackBundle.message("dialog.created.project.rating.label"), LabelPosition.TOP)
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
            emptyText.text = FeedbackBundle.message("dialog.created.project.checkbox.5.placeholder")
            textFieldOtherProblemProperty.afterChange {
              if (it.isNotBlank()) {
                checkBoxOtherProperty.set(true)
              }
              else {
                checkBoxOtherProperty.set(false)
              }
            }
            putClientProperty(TextComponentEmptyText.STATUS_VISIBLE_FUNCTION,
                              Predicate<JBTextField> { textField -> textField.text.isEmpty() })
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
            putClientProperty(TextComponentEmptyText.STATUS_VISIBLE_FUNCTION,
                              Predicate<JBTextField> { textField -> textField.text.isEmpty() })
          }.errorOnApply(FeedbackBundle.message("dialog.created.project.textfield.email.required")) {
            checkBoxEmailProperty.get() && it.text.isBlank()
          }.errorOnApply(ApplicationBundle.message("feedback.form.email.invalid")) {
            checkBoxEmailProperty.get() && it.text.isNotBlank() && !it.text.matches(Regex(".+@.+\\..+"))
          }
        }.bottomGap(BottomGap.MEDIUM)
      }

      row {
        cell(createFeedbackAgreementComponent(project) {
          showProjectCreationFeedbackSystemInfoDialog(project, systemInfoData)
        })
      }.bottomGap(BottomGap.SMALL).topGap(TopGap.MEDIUM)
    }.also { dialog ->
      dialog.border = JBEmptyBorder(JBUI.scale(15), JBUI.scale(10), JBUI.scale(0), JBUI.scale(10))
    }
    return JBScrollPane(mainPanel, JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER).apply {
      border = JBEmptyBorder(0)
    }
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

@Serializable
private data class ProjectCreationFeedbackSystemInfoData(
  val createdProjectTypeName: String,
  val commonSystemInfo: CommonFeedbackSystemInfoData
)

private fun showProjectCreationFeedbackSystemInfoDialog(project: Project?,
                                                        systemInfoData: ProjectCreationFeedbackSystemInfoData
) = showFeedbackSystemInfoDialog(project, systemInfoData.commonSystemInfo) {
  row(FeedbackBundle.message("dialog.created.project.system.info.panel.project.type")) {
    label(systemInfoData.createdProjectTypeName) //NON-NLS
  }
}

private fun createProjectCreationFeedbackSystemInfoData(createdProjectTypeName: String): ProjectCreationFeedbackSystemInfoData {
  return ProjectCreationFeedbackSystemInfoData(createdProjectTypeName, CommonFeedbackSystemInfoData.getCurrentData())
}
