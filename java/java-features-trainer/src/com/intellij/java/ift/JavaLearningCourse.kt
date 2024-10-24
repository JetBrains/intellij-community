// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift

import com.intellij.execution.actions.RunConfigurationsComboBoxAction
import com.intellij.java.ift.lesson.assistance.JavaEditorCodingAssistanceLesson
import com.intellij.java.ift.lesson.basic.JavaContextActionsLesson
import com.intellij.java.ift.lesson.basic.JavaSelectLesson
import com.intellij.java.ift.lesson.basic.JavaSurroundAndUnwrapLesson
import com.intellij.java.ift.lesson.completion.*
import com.intellij.java.ift.lesson.essential.JavaOnboardingTourLesson
import com.intellij.java.ift.lesson.essential.JavaReworkedOnboardingTourLesson
import com.intellij.java.ift.lesson.navigation.*
import com.intellij.java.ift.lesson.refactorings.JavaExtractMethodCocktailSortLesson
import com.intellij.java.ift.lesson.refactorings.JavaRefactoringMenuLesson
import com.intellij.java.ift.lesson.refactorings.JavaRenameLesson
import com.intellij.java.ift.lesson.run.JavaDebugLesson
import com.intellij.java.ift.lesson.run.JavaRunConfigurationLesson
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.PlatformUtils
import training.dsl.LessonUtil
import training.learn.CourseManager
import training.learn.LessonsBundle
import training.learn.NewUsersOnboardingExperimentAccessor
import training.learn.course.IftModule
import training.learn.course.LearningCourseBase
import training.learn.course.LearningModule
import training.learn.course.LessonType
import training.learn.lesson.general.*
import training.learn.lesson.general.assistance.CodeFormatLesson
import training.learn.lesson.general.assistance.LocalHistoryLesson
import training.learn.lesson.general.assistance.ParameterInfoLesson
import training.learn.lesson.general.assistance.QuickPopupsLesson
import training.learn.lesson.general.navigation.FindInFilesLesson
import training.learn.lesson.general.refactorings.ExtractVariableFromBubbleLesson
import training.util.useShortOnboardingLesson

class JavaLearningCourse : LearningCourseBase(JavaLanguage.INSTANCE.id) {
  override fun modules(): List<IftModule> = onboardingTour() + stableModules() + CourseManager.instance.findCommonModules("Git")

  private val isOnboardingLessonEnabled: Boolean
    get() = PlatformUtils.isIntelliJ() && !NewUsersOnboardingExperimentAccessor.isExperimentEnabled()

  private fun onboardingTour() = if (isOnboardingLessonEnabled) listOf(
    LearningModule(id = "Java.Onboarding",
                   name = JavaLessonsBundle.message("java.onboarding.module.name"),
                   description = JavaLessonsBundle.message("java.onboarding.module.description", LessonUtil.productName),
                   primaryLanguage = langSupport,
                   moduleType = LessonType.PROJECT) {
      listOf(JavaOnboardingTourLesson())
    }
  )
  else emptyList()

  private fun stableModules() = listOf(
    LearningModule(id = "Java.Essential",
                   name = LessonsBundle.message("essential.module.name"),
                   description = LessonsBundle.message("essential.module.description", LessonUtil.productName),
                   primaryLanguage = langSupport,
                   moduleType = LessonType.SCRATCH) {
      fun ls(sampleName: String) = loadSample("EditorBasics/$sampleName")

      val onboarding = when {
        !NewUsersOnboardingExperimentAccessor.isExperimentEnabled() -> emptyList()
        useShortOnboardingLesson -> listOf(JavaReworkedOnboardingTourLesson())
        else -> listOf(JavaOnboardingTourLesson())
      }
      onboarding + listOf(
        JavaContextActionsLesson(),
        GotoActionLesson(ls("00.Actions.java.sample"), firstLesson = false),
        JavaSearchEverywhereLesson(),
        JavaBasicCompletionLesson(),
      )
    },
    LearningModule(id = "Java.EditorBasics",
                   name = LessonsBundle.message("editor.basics.module.name"),
                   description = LessonsBundle.message("editor.basics.module.description"),
                   primaryLanguage = langSupport,
                   moduleType = LessonType.SCRATCH) {
      fun ls(sampleName: String) = loadSample("EditorBasics/$sampleName")
      listOf(
        JavaSelectLesson(),
        CommentUncommentLesson(ls("02.Comment.java.sample"), blockCommentsAvailable = true),
        DuplicateLesson(ls("04.Duplicate.java.sample")),
        MoveLesson("run()", ls("05.Move.java.sample")),
        CollapseLesson(ls("06.Collapse.java.sample")),
        JavaSurroundAndUnwrapLesson(),
        MultipleSelectionHtmlLesson(),
      )
    },
    LearningModule(id = "Java.CodeCompletion",
                   name = LessonsBundle.message("code.completion.module.name"),
                   description = LessonsBundle.message("code.completion.module.description"),
                   primaryLanguage = langSupport,
                   moduleType = LessonType.SCRATCH) {
      listOf(
        JavaBasicCompletionLesson(),
        JavaSmartTypeCompletionLesson(),
        JavaPostfixCompletionLesson(),
        JavaStatementCompletionLesson(),
        JavaCompletionWithTabLesson(),
      )
    },
    LearningModule(id = "Java.Refactorings",
                   name = LessonsBundle.message("refactorings.module.name"),
                   description = LessonsBundle.message("refactorings.module.description"),
                   primaryLanguage = langSupport,
                   moduleType = LessonType.SINGLE_EDITOR) {
      fun ls(sampleName: String) = loadSample("Refactorings/$sampleName")
      listOf(
        JavaRenameLesson(),
        ExtractVariableFromBubbleLesson(ls("ExtractVariable.java.sample")),
        JavaExtractMethodCocktailSortLesson(),
        JavaRefactoringMenuLesson(),
      )
    },
    LearningModule(id = "Java.CodeAssistance",
                   name = LessonsBundle.message("code.assistance.module.name"),
                   description = LessonsBundle.message("code.assistance.module.description"),
                   primaryLanguage = langSupport,
                   moduleType = LessonType.SINGLE_EDITOR) {
      fun ls(sampleName: String) = loadSample("CodeAssistance/$sampleName")
      listOf(
        LocalHistoryLesson(),
        CodeFormatLesson(ls("CodeFormat.java.sample"), true),
        ParameterInfoLesson(ls("ParameterInfo.java.sample")),
        QuickPopupsLesson(ls("QuickPopups.java.sample"), "viewing-reference-information.html#inline-quick-documentation"),
        JavaEditorCodingAssistanceLesson(ls("EditorCodingAssistance.java.sample")),
      )
    },
    LearningModule(id = "Java.Navigation",
                   name = LessonsBundle.message("navigation.module.name"),
                   description = LessonsBundle.message("navigation.module.description"),
                   primaryLanguage = langSupport,
                   moduleType = LessonType.PROJECT) {
      listOf(
        JavaSearchEverywhereLesson(),
        FindInFilesLesson("src/warehouse/FindInFilesSample.java"),
        JavaFileStructureLesson(),
        JavaDeclarationAndUsagesLesson(),
        JavaInheritanceHierarchyLesson(),
        JavaRecentFilesLesson(),
        JavaOccurrencesLesson(),
      )
    },
  ) + if (RunConfigurationsComboBoxAction.hasRunCurrentFileItem(ProjectManager.getInstance().defaultProject)) { // project doesn't matter in this check for us
    listOf(
      LearningModule(id = "Java.RunAndDebug",
                     name = LessonsBundle.message("run.debug.module.name"),
                     description = LessonsBundle.message("run.debug.module.description"),
                     primaryLanguage = langSupport,
                     moduleType = LessonType.SINGLE_EDITOR) {
        listOf(
          JavaRunConfigurationLesson(),
          JavaDebugLesson(),
        )
      }
    )
  }
  else emptyList()

  override fun getLessonIdToTipsMap(): Map<String, List<String>> = mutableMapOf(
    // Essential
    "context.actions" to listOf("ContextActions"),
    "Actions" to listOf("find_action", "GoToAction"),
    "Search everywhere" to listOf("SearchEverywhere", "GoToClass", "search_everywhere_general"),
    "Basic completion" to listOf("CodeCompletion"),

    // EditorBasics
    "Select" to listOf("smart_selection", "CtrlW"),
    "Comment line" to listOf("CommentCode"),
    "Duplicate" to listOf("CtrlD", "DeleteLine"),
    "Move" to listOf("MoveUpDown"),
    "Surround and unwrap" to listOf("SurroundWith"),

    // CodeCompletion
    "Basic completion" to listOf("CodeCompletion"),
    "Smart type completion" to listOf("SmartTypeCompletion", "SmartTypeAfterNew", "SecondSmartCompletionToar"),
    "Postfix completion" to listOf("PostfixCompletion"),
    "Statement completion" to listOf("CompleteStatement", "FinishBySmartEnter"),
    "Completion with tab" to listOf("TabInLookups"),

    // Refactorings
    "Refactorings.Rename" to listOf("Rename"),
    "Extract variable" to listOf("IntroduceVariable"),
    "Refactorings.ExtractMethod" to listOf("ExtractMethod"),
    "java.refactoring.menu" to listOf("RefactorThis"),

    // CodeAssistance
    "CodeAssistance.LocalHistory" to listOf("local_history"),
    "CodeAssistance.CodeFormatting" to listOf("LayoutCode"),
    "CodeAssistance.ParameterInfo" to listOf("ParameterInfo"),
    "CodeAssistance.QuickPopups" to listOf("CtrlShiftIForLookup", "CtrlShiftI", "QuickJavaDoc"),
    "CodeAssistance.EditorCodingAssistance" to listOf("HighlightUsagesInFile", "NextPrevError", "NavigateBetweenErrors"),

    // Navigation
    "Search everywhere" to listOf("SearchEverywhere", "GoToClass", "search_everywhere_general"),
    "Find in files" to listOf("FindReplaceToggle", "FindInPath"),
    "File structure" to listOf("FileStructurePopup"),
    "Declaration and usages" to listOf("GoToDeclaration", "ShowUsages"),
    "java.inheritance.hierarchy.lesson" to listOf("HierarchyBrowser"),
    "Recent Files and Locations" to listOf("recent-locations", "RecentFiles"),

    // RunAndDebug
    "java.run.configuration" to listOf("SelectRunDebugConfiguration"),
    "java.debug.workflow" to listOf("BreakpointSpeedmenu", "QuickEvaluateExpression", "EvaluateExpressionInEditor"),
  ).also { map ->
    val gitCourse = CourseManager.instance.findCommonCourseById("Git")
    if (gitCourse != null) {
      map.putAll(gitCourse.getLessonIdToTipsMap())
    }
  }
}