// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ift.lesson.essential

import com.intellij.execution.RunManager
import com.intellij.java.ift.JavaLessonsBundle
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.IdeUICustomization
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.ui.tree.TreeUtil
import training.dsl.*
import training.dsl.LessonUtil.restoreIfModified
import training.learn.LearnBundle
import training.learn.NewUsersOnboardingExperimentAccessor
import training.learn.course.LessonProperties
import training.learn.course.LessonType
import training.learn.lesson.general.run.clearBreakpoints
import training.project.ProjectUtils
import training.ui.LearningUiHighlightingManager
import training.ui.LearningUiManager
import training.util.*
import javax.swing.JTree
import javax.swing.tree.TreePath

abstract class OnboardingTourLessonBase(id: String) : CommonLogicForOnboardingTours(id, JavaLessonsBundle.message("java.onboarding.lesson.name")) {
  override val lessonType: LessonType = LessonType.PROJECT

  override val properties: LessonProperties = LessonProperties(
    canStartInDumbMode = true,
    openFileAtStart = false
  )

  private lateinit var openLearnTaskId: TaskContext.TaskId

  private val demoFileDirectory: String = "src"
  private val demoFileNameWithoutExtension: String = "Welcome"
  abstract val demoFileExtension: String
  private val demoFileName: String
    get() = "$demoFileNameWithoutExtension.$demoFileExtension"

  abstract val learningProjectName: String

  override val lessonContent: LessonContext.() -> Unit = {
    prepareRuntimeTask {
      rememberJdkAtStart()
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

    commonTasks()

    task {
      if (!NewUsersOnboardingExperimentAccessor.isExperimentEnabled()) {
        text(JavaLessonsBundle.message("java.onboarding.epilog",
                                       getCallBackActionId("CloseProject"),
                                       LessonUtil.returnToWelcomeScreenRemark(),
                                       LearningUiManager.addCallback { LearningUiManager.resetModulesView() }))
      }
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
        val result = path.pathCount >= 2 && path.getPathComponent(1).isToStringContains(learningProjectName)
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
                     && path.getPathComponent(1).isToStringContains(learningProjectName)
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

  override fun onLessonEnd(project: Project, lessonEndInfo: LessonEndInfo) {
    super.onLessonEnd(project, lessonEndInfo)
    if (!NewUsersOnboardingExperimentAccessor.isExperimentEnabled()) {
      showEndOfLessonDialogAndFeedbackForm(this, lessonEndInfo, project)
    }
  }
}