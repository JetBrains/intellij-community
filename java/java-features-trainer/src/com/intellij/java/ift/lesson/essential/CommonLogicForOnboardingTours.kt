// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ift.lesson.essential

import com.intellij.execution.ui.UIExperiment
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.ide.ui.UISettings
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.idea.ActionsBundle
import com.intellij.java.ift.JavaLessonsBundle
import com.intellij.java.ift.JavaProjectUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.actions.ToggleCaseAction
import com.intellij.openapi.module.LanguageLevelUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.roots.ui.configuration.SdkDetector
import com.intellij.openapi.ui.ex.MultiLineLabel
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.WindowStateService
import com.intellij.openapi.wm.impl.FocusManagerImpl
import com.intellij.ui.UIBundle
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.dsl.builder.Panel
import com.intellij.util.PlatformUtils
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.XDebuggerManager
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import org.jetbrains.annotations.Nls
import training.dsl.*
import training.dsl.LessonUtil.adjustSearchEverywherePosition
import training.dsl.LessonUtil.checkEditorModification
import training.dsl.LessonUtil.restoreIfModified
import training.dsl.LessonUtil.restoreIfModifiedOrMoved
import training.dsl.LessonUtil.restorePopupPosition
import training.learn.LessonsBundle
import training.learn.course.KLesson
import training.learn.course.LessonProperties
import training.learn.lesson.general.run.clearBreakpoints
import training.learn.lesson.general.run.toggleBreakpointTask
import training.ui.LearningUiHighlightingManager
import training.ui.getFeedbackProposedPropertyName
import training.ui.shouldCollectFeedbackResults
import training.util.LessonEndInfo
import training.util.OnboardingFeedbackData
import training.util.isToStringContains
import java.awt.Point
import java.awt.event.KeyEvent
import java.util.concurrent.CompletableFuture
import javax.swing.JWindow

abstract class CommonLogicForOnboardingTours(id: String, @Nls lessonName: String) : KLesson(id, lessonName) {
  abstract val sample: LessonSample

  private var backupPopupLocation: Point? = null

  private var hideToolStripesPreference = false
  private var showMainToolbarPreference = true

  private val uiSettings get() = UISettings.getInstance()

  @NlsSafe
  private var jdkAtStart: String = "undefined"

  override val testScriptProperties: TaskTestContext.TestScriptProperties = TaskTestContext.TestScriptProperties(skipTesting = true)

  open fun TaskRuntimeContext.rememberJdkAtStart() {
    jdkAtStart = getCurrentJdkVersionString(project)
  }

  private fun getCurrentJdkVersionString(project: Project): String {
    return JavaProjectUtil.getEffectiveJdk(project)?.let { JavaSdk.getInstance().getVersionString(it) } ?: "none"
  }

  protected fun LessonContext.checkUiSettings() {
    showInvalidDebugLayoutWarning()

    if (!uiSettings.hideToolStripes && uiSettings.showNewMainToolbar) {
      // a small hack to have same tasks count. It is needed to track statistics result.
      task { }
      task { }
      return
    }

    task {
      text(JavaLessonsBundle.message("java.onboarding.change.ui.settings"))
      proceedLink()
    }

    prepareRuntimeTask {
      hideToolStripesPreference = uiSettings.hideToolStripes
      showMainToolbarPreference = uiSettings.showNewMainToolbar
      uiSettings.hideToolStripes = false
      uiSettings.showNewMainToolbar = true
      uiSettings.fireUISettingsChanged()
    }
  }
  
  protected fun LessonContext.commonTasks() {
    sdkConfigurationTasks()

    waitIndexingTasks()

    runTasks()

    debugTasks()

    completionSteps()

    waitBeforeContinue(500)

    contextActions()

    waitBeforeContinue(500)

    searchEverywhereTasks()
  }

  private fun LessonContext.debugTasks() {
    clearBreakpoints()

    var logicalPosition = LogicalPosition(0, 0)
    prepareRuntimeTask {
      logicalPosition = editor.offsetToLogicalPosition(sample.startOffset)
    }
    caret(sample.startOffset)

    toggleBreakpointTask(sample, { logicalPosition }, checkLine = false) {
      text(JavaLessonsBundle.message("java.onboarding.balloon.click.here"),
           LearningBalloonConfig(Balloon.Position.below, width = 0, cornerToPointerDistance = 20))
      text(JavaLessonsBundle.message("java.onboarding.toggle.breakpoint.1",
                                     code("6.5"), code("findAverage"), code("26")))
      text(JavaLessonsBundle.message("java.onboarding.toggle.breakpoint.2"))
    }

    highlightButtonById("Debug", highlightInside = false, usePulsation = false)

    actionTask("Debug") {
      showBalloonOnHighlightingComponent(JavaLessonsBundle.message("java.onboarding.balloon.start.debugging"))
      restoreState {
        lineWithBreakpoints() != setOf(logicalPosition.line)
      }
      restoreIfModified(sample)
      JavaLessonsBundle.message("java.onboarding.start.debugging", icon(AllIcons.Actions.StartDebugger))
    }

    lateinit var debuggerGotItTaskId: TaskContext.TaskId
    task {
      debuggerGotItTaskId = taskId
    }

    highlightDebugActionsToolbar()

    task {
      rehighlightPreviousUi = true
      gotItStep(Balloon.Position.above, width = 0, cornerToPointerDistance = 130,
                text = JavaLessonsBundle.message("java.onboarding.balloon.about.debug.panel",
                                                 strong(UIBundle.message("tool.window.name.debug")),
                                                 strong(LessonsBundle.message("debug.workflow.lesson.name"))))
      restoreByUi(debuggerGotItTaskId)
    }

    highlightButtonById("Stop", highlightInside = false, usePulsation = false)
    task {
      val position = if (UIExperiment.isNewDebuggerUIEnabled()) Balloon.Position.above else Balloon.Position.atRight
      showBalloonOnHighlightingComponent(JavaLessonsBundle.message("java.onboarding.balloon.stop.debugging"),
                                         position, cornerToPointerDistance = 35) { list -> list.maxByOrNull { it.locationOnScreen.y } }
      text(JavaLessonsBundle.message("java.onboarding.stop.debugging", icon(AllIcons.Actions.Suspend)))
      restoreIfModified(sample)
      stateCheck {
        XDebuggerManager.getInstance(project).currentSession == null
      }
    }

    prepareRuntimeTask {
      LearningUiHighlightingManager.clearHighlights()
    }
  }

  private fun LessonContext.waitIndexingTasks() {
    task {
      triggerAndBorderHighlight().component { progress: NonOpaquePanel ->
        progress.javaClass.name.contains("InlineProgressPanel")
      }
    }

    task {
      text(JavaLessonsBundle.message("java.onboarding.indexing.description"))
      text(JavaLessonsBundle.message("java.onboarding.wait.indexing"), LearningBalloonConfig(Balloon.Position.above, 0))
      waitSmartModeStep()
    }

    waitBeforeContinue(300)

    prepareRuntimeTask {
      LearningUiHighlightingManager.clearHighlights()
    }
  }

  private fun LessonContext.runTasks() {
    highlightRunToolbar(highlightInside = false, usePulsation = false)

    task {
      triggerUI {
        clearPreviousHighlights = false
      }.component { ui: ActionButton -> ActionManager.getInstance().getId(ui.action) == "Run" }
    }

    task {
      val introductionText = JavaLessonsBundle.message("java.onboarding.temporary.configuration.description",
                                                       strong(ActionsBundle.actionText("NewUiRunWidget")),
                                                       icon(AllIcons.Actions.Execute),
                                                       icon(AllIcons.Actions.StartDebugger))
      val runOptionsText = if (PlatformUtils.isIdeaUltimate()) {
        JavaLessonsBundle.message("java.onboarding.run.options.ultimate",
                                  icon(AllIcons.Actions.Profile),
                                  icon(AllIcons.General.RunWithCoverage),
                                  icon(AllIcons.Actions.More))
      }
      else {
        JavaLessonsBundle.message("java.onboarding.run.options.community",
                                  icon(AllIcons.General.RunWithCoverage),
                                  icon(AllIcons.Actions.More))
      }

      text("$introductionText $runOptionsText")
      text(JavaLessonsBundle.message("java.onboarding.run.sample", icon(AllIcons.Actions.Execute), action("Run")))
      text(JavaLessonsBundle.message("java.onboarding.run.sample.balloon", icon(AllIcons.Actions.Execute), action("Run")),
           LearningBalloonConfig(Balloon.Position.below, 0))
      checkToolWindowState("Run", true)
      restoreIfModified(sample)
    }
  }

  abstract val completionStepExpectedCompletion: String

  private fun LessonContext.completionSteps() {
    prepareRuntimeTask {
      setSample(sample.insertAtPosition(2, " / values<caret>"))
      FocusManagerImpl.getInstance(project).requestFocusInProject(editor.contentComponent, project)
    }

    task {
      val textToFind = "result / values"
      triggerOnEditorText(textToFind, centerOffset = textToFind.length)
    }

    task {
      text(JavaLessonsBundle.message("java.onboarding.type.division",
                                     code(" / values")))
      text(JavaLessonsBundle.message("java.onboarding.invoke.completion", code(".")))
      text(JavaLessonsBundle.message("java.onboarding.invoke.completion.balloon", code(".")),
           LearningBalloonConfig(Balloon.Position.below, width = 0))
      triggerAndBorderHighlight().listItem { // no highlighting
        it.isToStringContains(completionStepExpectedCompletion)
      }
      proposeRestoreForInvalidText(".")
    }

    task {
      text(JavaLessonsBundle.message("java.onboarding.choose.values.item",
                                     code(completionStepExpectedCompletion), action("EditorChooseLookupItem")))
      text(JavaLessonsBundle.message("java.onboarding.invoke.completion.tip", action("CodeCompletion")))
      stateCheck {
        checkEditorModification(sample, modificationPositionId = 2, needChange = "/values.$completionStepExpectedCompletion")
      }
    }
  }

  abstract fun LessonContext.contextActions()

  private fun LessonContext.searchEverywhereTasks() {
    val toggleCase = ActionsBundle.message("action.EditorToggleCase.text")
    caret("AVERAGE", select = true)
    task("SearchEverywhere") {
      text(JavaLessonsBundle.message("java.onboarding.invoke.search.everywhere.1",
                                     strong(toggleCase), code("AVERAGE")))
      text(JavaLessonsBundle.message("java.onboarding.invoke.search.everywhere.2",
                                     LessonUtil.rawKeyStroke(KeyEvent.VK_SHIFT), LessonUtil.actionName(it)))
      triggerAndBorderHighlight().component { ui: ExtendableTextField ->
        UIUtil.getParentOfType(SearchEverywhereUI::class.java, ui) != null
      }
      restoreIfModifiedOrMoved()
    }

    task {
      transparentRestore = true
      before {
        if (backupPopupLocation != null) return@before
        val ui = previous.ui ?: return@before
        val popupWindow = UIUtil.getParentOfType(JWindow::class.java, ui) ?: return@before
        val oldPopupLocation = WindowStateService.getInstance(project).getLocation(SearchEverywhereManagerImpl.LOCATION_SETTINGS_KEY)
        if (adjustSearchEverywherePosition(popupWindow, "of array ") || LessonUtil.adjustPopupPosition(project, popupWindow)) {
          backupPopupLocation = oldPopupLocation
        }
      }
      text(JavaLessonsBundle.message("java.onboarding.search.everywhere.description",
                                     code("AVERAGE"), code(JavaLessonsBundle.message("toggle.case.part"))))
      triggerAndBorderHighlight().listItem { item ->
        val value = (item as? GotoActionModel.MatchedValue)?.value
        (value as? GotoActionModel.ActionWrapper)?.action is ToggleCaseAction
      }
      restoreByUi()
      restoreIfModifiedOrMoved()
    }

    task {
      text(JavaLessonsBundle.message("java.onboarding.apply.action", strong(toggleCase), LessonUtil.rawEnter()))
      stateCheck {
        editor.document.text.contains("\"average")
      }
      restoreByUi(delayMillis = defaultRestoreDelay)
    }

    text(JavaLessonsBundle.message("java.onboarding.case.changed"))
  }

  override fun onLessonEnd(project: Project, lessonEndInfo: LessonEndInfo) {
    prepareFeedbackData(project, lessonEndInfo)
    restorePopupPosition(project, SearchEverywhereManagerImpl.LOCATION_SETTINGS_KEY, backupPopupLocation)
    backupPopupLocation = null

    uiSettings.hideToolStripes = hideToolStripesPreference
    uiSettings.showNewMainToolbar = showMainToolbarPreference
    uiSettings.fireUISettingsChanged()
  }

  private fun prepareFeedbackData(project: Project, lessonEndInfo: LessonEndInfo) {
    if (!shouldCollectFeedbackResults()) {
      return
    }

    val primaryLanguage = module.primaryLanguage
    if (primaryLanguage == null) {
      thisLogger().error("Onboarding lesson has no language support for some magical reason")
      return
    }
    val configPropertyName = getFeedbackProposedPropertyName(primaryLanguage)
    if (PropertiesComponent.getInstance().getBoolean(configPropertyName, false)) {
      return
    }

    val jdkVersionsFuture = CompletableFuture<List<String>>()
    runBackgroundableTask(ProjectBundle.message("progress.title.detecting.sdks"), project, false) { indicator ->
      val jdkVersions = mutableListOf<String>()
      SdkDetector.getInstance().detectSdks(JavaSdk.getInstance(), indicator, object : SdkDetector.DetectedSdkListener {
        override fun onSdkDetected(type: SdkType, version: String, home: String) {
          jdkVersions.add(version)
        }

        override fun onSearchCompleted() {
          jdkVersionsFuture.complete(jdkVersions)
        }
      })
    }

    @Suppress("HardCodedStringLiteral")
    val currentJdkVersion: @NlsSafe String = getCurrentJdkVersionString(project)

    val module = ModuleManager.getInstance(project).modules.first()

    @Suppress("HardCodedStringLiteral")
    val currentLanguageLevel: @NlsSafe String = LanguageLevelUtil.getEffectiveLanguageLevel(module).name

    primaryLanguage.onboardingFeedbackData = object : OnboardingFeedbackData("IDEA Onboarding Tour Feedback", lessonEndInfo) {
      override val feedbackReportId = "idea_onboarding_tour"

      override val additionalFeedbackFormatVersion: Int = 1

      private val jdkVersions: List<String>? by lazy {
        if (jdkVersionsFuture.isDone) jdkVersionsFuture.get() else null
      }

      override val addAdditionalSystemData: JsonObjectBuilder.() -> Unit = {
        put("jdk_at_start", jdkAtStart)
        put("current_jdk", currentJdkVersion)
        put("language_level", currentLanguageLevel)
        put("found_jdk", buildJsonArray {
          for (version in jdkVersions ?: emptyList()) {
            add(JsonPrimitive(version))
          }
        })
      }

      override val addRowsForUserAgreement: Panel.() -> Unit = {
        row(JavaLessonsBundle.message("java.onboarding.feedback.system.found.jdks")) {
          val versions: @NlsSafe String = jdkVersions?.joinToString("\n") ?: "none"
          cell(MultiLineLabel(versions))
        }
        row(JavaLessonsBundle.message("java.onboarding.feedback.system.jdk.at.start")) {
          label(jdkAtStart)
        }
        row(JavaLessonsBundle.message("java.onboarding.feedback.system.current.jdk")) {
          label(currentJdkVersion)
        }
        row(JavaLessonsBundle.message("java.onboarding.feedback.system.lang.level")) {
          label(currentLanguageLevel)
        }
      }

      override fun feedbackHasBeenProposed() {
        PropertiesComponent.getInstance().setValue(configPropertyName, true, false)
      }
    }
  }
}