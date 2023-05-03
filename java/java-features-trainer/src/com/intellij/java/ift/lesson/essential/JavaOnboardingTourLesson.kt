// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ift.lesson.essential

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature
import com.intellij.execution.RunManager
import com.intellij.execution.ui.UIExperiment
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.ide.ui.UISettings
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.idea.ActionsBundle
import com.intellij.java.ift.JavaLessonsBundle
import com.intellij.java.ift.JavaProjectUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.actions.ToggleCaseAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.LanguageLevelUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.roots.ui.configuration.SdkDetector
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ex.MultiLineLabel
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.WindowStateService
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.FocusManagerImpl
import com.intellij.ui.IdeUICustomization
import com.intellij.ui.UIBundle
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.PlatformUtils
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.xdebugger.XDebuggerManager
import com.siyeh.InspectionGadgetsBundle
import com.siyeh.IntentionPowerPackBundle
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import org.jetbrains.annotations.Nls
import training.FeaturesTrainerIcons
import training.dsl.*
import training.dsl.LessonUtil.adjustSearchEverywherePosition
import training.dsl.LessonUtil.checkEditorModification
import training.dsl.LessonUtil.restoreIfModified
import training.dsl.LessonUtil.restoreIfModifiedOrMoved
import training.dsl.LessonUtil.restorePopupPosition
import training.learn.LearnBundle
import training.learn.LessonsBundle
import training.learn.course.KLesson
import training.learn.course.LessonProperties
import training.learn.lesson.LessonManager
import training.learn.lesson.general.run.clearBreakpoints
import training.learn.lesson.general.run.toggleBreakpointTask
import training.project.ProjectUtils
import training.ui.LearningUiHighlightingManager
import training.ui.LearningUiManager
import training.ui.getFeedbackProposedPropertyName
import training.util.*
import java.awt.Point
import java.awt.event.KeyEvent
import java.util.concurrent.CompletableFuture
import javax.swing.JTree
import javax.swing.JWindow
import javax.swing.tree.TreePath

class JavaOnboardingTourLesson : KLesson("java.onboarding", JavaLessonsBundle.message("java.onboarding.lesson.name")) {
  private lateinit var openLearnTaskId: TaskContext.TaskId
  private var useDelay: Boolean = false

  private val demoFileDirectory: String = "src"
  private val demoFileNameWithoutExtension: String = "Welcome"
  private val demoFileName: String = "$demoFileNameWithoutExtension.java"

  private val uiSettings get() = UISettings.getInstance()

  override val properties = LessonProperties(
    canStartInDumbMode = true,
    openFileAtStart = false
  )

  override val testScriptProperties = TaskTestContext.TestScriptProperties(skipTesting = true)

  private var backupPopupLocation: Point? = null
  private var hideToolStripesPreference = false
  private var showNavigationBarPreference = true

  @NlsSafe
  private var jdkAtStart: String = "undefined"

  val sample: LessonSample = parseLessonSample("""
    import java.util.Arrays;
    import java.util.List;
    
    class Welcome {
        public static void main(String[] args) {
            int[] array = {5, 6, 7, 8};
            System.out.println("AVERAGE of array " + Arrays.toString(array) + " is " + findAverage(array));
        }
    
        private static double findAverage(int[] values) {
            double result = 0;
            <caret id=3/>for (int i = 0; i < values.length; i++) {
                result += values[i];
            }
            <caret>return result<caret id=2/>;
        }
    }
    """.trimIndent())

  override val lessonContent: LessonContext.() -> Unit = {
    prepareRuntimeTask {
      jdkAtStart = getCurrentJdkVersionString(project)
      useDelay = true
      invokeActionForFocusContext(getActionById("Stop"))
      val runManager = RunManager.getInstance(project)
      runManager.allSettings.forEach(runManager::removeConfiguration)

      val root = ProjectUtils.getCurrentLearningProjectRoot()
      val srcDir = root.findChild(demoFileDirectory) ?: error("'src' directory not found.")
      if (srcDir.findChild(demoFileName) == null) invokeLater {
        runWriteAction {
          srcDir.createChildData(this, demoFileName)
          // todo: This file shows with .java extension in the Project view and this extension disappears when user open it
          //  (because we fill the file after the user open it) Fill the file immediately in this place?
        }
      }
    }
    clearBreakpoints()

    checkUiSettings()

    projectTasks()

    prepareSample(sample, checkSdkConfiguration = false)

    openLearnToolwindow()

    sdkConfigurationTasks()

    waitIndexingTasks()

    runTasks()

    debugTasks()

    completionSteps()

    waitBeforeContinue(500)

    contextActions()

    waitBeforeContinue(500)

    searchEverywhereTasks()

    task {
      text(JavaLessonsBundle.message("java.onboarding.epilog",
                                     getCallBackActionId("CloseProject"),
                                     LessonUtil.returnToWelcomeScreenRemark(),
                                     LearningUiManager.addCallback { LearningUiManager.resetModulesView() }))
    }
  }

  override fun onLessonEnd(project: Project, lessonEndInfo: LessonEndInfo) {
    prepareFeedbackData(project, lessonEndInfo)
    restorePopupPosition(project, SearchEverywhereManagerImpl.LOCATION_SETTINGS_KEY, backupPopupLocation)
    backupPopupLocation = null

    uiSettings.hideToolStripes = hideToolStripesPreference
    uiSettings.showNavigationBar = showNavigationBarPreference
    uiSettings.fireUISettingsChanged()

    if (!lessonEndInfo.lessonPassed) {
      LessonUtil.showFeedbackNotification(this, project)
      return
    }
    val dataContextPromise = DataManager.getInstance().dataContextFromFocusAsync
    invokeLater {
      val result = MessageDialogBuilder.yesNoCancel(JavaLessonsBundle.message("java.onboarding.finish.title"),
                                                    JavaLessonsBundle.message("java.onboarding.finish.text",
                                                                              LessonUtil.returnToWelcomeScreenRemark()))
        .yesText(JavaLessonsBundle.message("java.onboarding.finish.exit"))
        .noText(JavaLessonsBundle.message("java.onboarding.finish.modules"))
        .icon(FeaturesTrainerIcons.PluginIcon)
        .show(project)

      when (result) {
        Messages.YES -> invokeLater {
          LessonManager.instance.stopLesson()
          val closeAction = getActionById("CloseProject")
          dataContextPromise.onSuccess { context ->
            invokeLater {
              val event = AnActionEvent.createFromAnAction(closeAction, null, ActionPlaces.LEARN_TOOLWINDOW, context)
              ActionUtil.performActionDumbAwareWithCallbacks(closeAction, event)
            }
          }
        }
        Messages.NO -> invokeLater {
          LearningUiManager.resetModulesView()
        }
      }
      if (result != Messages.YES) {
        LessonUtil.showFeedbackNotification(this, project)
      }
    }
  }

  private fun prepareFeedbackData(project: Project, lessonEndInfo: LessonEndInfo) {
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

  private fun getCurrentJdkVersionString(project: Project): String {
    return JavaProjectUtil.getEffectiveJdk(project)?.let { JavaSdk.getInstance().getVersionString(it) } ?: "none"
  }

  private fun getCallBackActionId(@Suppress("SameParameterValue") actionId: String): Int {
    val action = getActionById(actionId)
    return LearningUiManager.addCallback { invokeActionForFocusContext(action) }
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

    highlightDebugActionsToolbar()

    task {
      rehighlightPreviousUi = true
      gotItStep(Balloon.Position.above, width = 0,
                JavaLessonsBundle.message("java.onboarding.balloon.about.debug.panel",
                                          strong(UIBundle.message("tool.window.name.debug")),
                                          strong(LessonsBundle.message("debug.workflow.lesson.name"))))
      restoreIfModified(sample)
    }

    highlightButtonById("Stop", highlightInside = false, usePulsation = false)
    task {
      val position = if (UIExperiment.isNewDebuggerUIEnabled()) Balloon.Position.above else Balloon.Position.atRight
      showBalloonOnHighlightingComponent(JavaLessonsBundle.message("java.onboarding.balloon.stop.debugging"),
                                         position) { list -> list.maxByOrNull { it.locationOnScreen.y } }
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

  private fun LessonContext.openLearnToolwindow() {
    task {
      triggerAndBorderHighlight().component { stripe: ActionButton ->
        stripe.action.templateText == LearnBundle.message("toolwindow.stripe.Learn")
      }
    }

    task {
      openLearnTaskId = taskId
      text(JavaLessonsBundle.message("java.onboarding.balloon.open.learn.toolbar", strong(LearnBundle.message("toolwindow.stripe.Learn"))),
           LearningBalloonConfig(Balloon.Position.atRight, width = 0, duplicateMessage = true))
      stateCheck {
        ToolWindowManager.getInstance(project).getToolWindow("Learn")?.isVisible == true
      }
      restoreIfModified(sample)
    }

    prepareRuntimeTask {
      LearningUiHighlightingManager.clearHighlights()
      requestEditorFocus()
    }
  }


  private fun LessonContext.checkUiSettings() {
    hideToolStripesPreference = uiSettings.hideToolStripes
    showNavigationBarPreference = uiSettings.showNavigationBar

    showInvalidDebugLayoutWarning()

    if (!hideToolStripesPreference && (showNavigationBarPreference || uiSettings.showMainToolbar)) {
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
      uiSettings.hideToolStripes = false
      uiSettings.showNavigationBar = true
      uiSettings.fireUISettingsChanged()
    }
  }

  private fun LessonContext.projectTasks() {
    prepareRuntimeTask {
      LessonUtil.hideStandardToolwindows(project)
    }

    task {
      triggerAndBorderHighlight().component { stripe: ActionButton ->
        stripe.action.templateText == IdeUICustomization.getInstance().getProjectViewTitle(project)
      }
    }

    lateinit var openProjectViewTask: TaskContext.TaskId
    task {
      openProjectViewTask = taskId
      var projectDirExpanded = false

      text(JavaLessonsBundle.message("java.onboarding.project.view.description",
                                     action("ActivateProjectToolWindow")))
      text(JavaLessonsBundle.message("java.onboarding.balloon.project.view"),
           LearningBalloonConfig(Balloon.Position.atRight, width = 0, cornerToPointerDistance = 8))
      triggerUI().treeItem { tree: JTree, path: TreePath ->
        val result = path.pathCount >= 2 && path.getPathComponent(1).isToStringContains("IdeaLearningProject")
        if (result) {
          if (!projectDirExpanded) {
            invokeLater { tree.expandPath(path) }
          }
          projectDirExpanded = true
        }
        result
      }
    }

    task {
      var srcDirCollapsed = false
      triggerAndBorderHighlight().treeItem { tree: JTree, path: TreePath ->
        val result = path.pathCount >= 3
                     && path.getPathComponent(1).isToStringContains("IdeaLearningProject")
                     && path.getPathComponent(2).isToStringContains(demoFileDirectory)
        if (result) {
          if (!srcDirCollapsed) {
            invokeLater { tree.collapsePath(path) }
          }
          srcDirCollapsed = true
        }
        result
      }
    }

    fun isDemoFilePath(path: TreePath) =
      path.pathCount >= 4 && path.getPathComponent(3).isToStringContains(demoFileNameWithoutExtension)

    task {
      text(JavaLessonsBundle.message("java.onboarding.balloon.source.directory", strong(demoFileDirectory)),
           LearningBalloonConfig(Balloon.Position.atRight, duplicateMessage = true, width = 0))
      triggerAndBorderHighlight().treeItem { _: JTree, path: TreePath ->
        isDemoFilePath(path)
      }
      restoreByUi(openProjectViewTask)
    }

    task {
      text(JavaLessonsBundle.message("java.onboarding.balloon.open.file", strong(demoFileName)),
           LearningBalloonConfig(Balloon.Position.atRight, duplicateMessage = true, width = 0))
      stateCheck l@{
        if (FileEditorManager.getInstance(project).selectedTextEditor == null) return@l false
        virtualFile.name == demoFileName
      }
      restoreState {
        (previous.ui as? JTree)?.takeIf { tree ->
          TreeUtil.visitVisibleRows(tree, TreeVisitor { path ->
            if (isDemoFilePath(path)) TreeVisitor.Action.INTERRUPT else TreeVisitor.Action.CONTINUE
          }) != null
        }?.isShowing?.not() ?: true
      }
    }
  }

  private fun LessonContext.completionSteps() {
    prepareRuntimeTask {
      setSample(sample.insertAtPosition(2, " / values<caret>"))
      FocusManagerImpl.getInstance(project).requestFocusInProject(editor.contentComponent, project)
    }

    task {
      text(JavaLessonsBundle.message("java.onboarding.type.division",
                                     code(" / values")))
      text(JavaLessonsBundle.message("java.onboarding.invoke.completion", code(".")))
      triggerAndBorderHighlight().listItem { // no highlighting
        it.isToStringContains("length")
      }
      proposeRestoreForInvalidText(".")
    }

    task {
      text(JavaLessonsBundle.message("java.onboarding.choose.values.item",
                                     code("length"), action("EditorChooseLookupItem")))
      text(JavaLessonsBundle.message("java.onboarding.invoke.completion.tip", action("CodeCompletion")))
      stateCheck {
        checkEditorModification(sample, modificationPositionId = 2, needChange = "/values.length")
      }
    }
  }

  private fun LessonContext.contextActions() {
    val quickFixMessage = InspectionGadgetsBundle.message("foreach.replace.quickfix")
    caret(sample.getPosition(3))
    task("ShowIntentionActions") {
      text(JavaLessonsBundle.message("java.onboarding.invoke.intention.for.warning.1"))
      text(JavaLessonsBundle.message("java.onboarding.invoke.intention.for.warning.2", action(it)))
      triggerAndBorderHighlight().listItem { item ->
        item.isToStringContains(quickFixMessage)
      }
      restoreIfModifiedOrMoved()
    }

    task {
      text(JavaLessonsBundle.message("java.onboarding.select.fix", strong(quickFixMessage)))
      stateCheck {
        editor.document.text.contains("for (int value : values)")
      }
      restoreByUi(delayMillis = defaultRestoreDelay)
    }

    fun getIntentionMessage(project: Project): @Nls String {
      val module = ModuleManager.getInstance(project).modules.firstOrNull() ?: error("Not found modules in project '${project.name}'")
      val langLevel = LanguageLevelUtil.getEffectiveLanguageLevel(module)
      val messageKey = if (langLevel.isAtLeast(HighlightingFeature.TEXT_BLOCKS.level)) {
        "replace.concatenation.with.format.string.intention.name.formatted"
      }
      else "replace.concatenation.with.format.string.intention.name"
      return IntentionPowerPackBundle.message(messageKey)
    }

    caret("RAGE")
    task("ShowIntentionActions") {
      text(JavaLessonsBundle.message("java.onboarding.invoke.intention.for.code", action(it)))
      val intentionMessage = getIntentionMessage(project)
      triggerAndBorderHighlight().listItem { item ->
        item.isToStringContains(intentionMessage)
      }
      restoreIfModifiedOrMoved()
    }

    task {
      text(JavaLessonsBundle.message("java.onboarding.apply.intention", strong(getIntentionMessage(project)), LessonUtil.rawEnter()))
      stateCheck {
        val text = editor.document.text
        text.contains("System.out.printf") || text.contains("MessageFormat.format")
      }
      restoreByUi(delayMillis = defaultRestoreDelay)
    }
  }

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
}