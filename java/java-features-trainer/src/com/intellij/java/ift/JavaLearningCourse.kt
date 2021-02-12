// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift

import com.intellij.java.ift.lesson.assistance.JavaEditorCodingAssistanceLesson
import com.intellij.java.ift.lesson.basic.JavaContextActionsLesson
import com.intellij.java.ift.lesson.basic.JavaSelectLesson
import com.intellij.java.ift.lesson.basic.JavaSurroundAndUnwrapLesson
import com.intellij.java.ift.lesson.completion.*
import com.intellij.java.ift.lesson.navigation.*
import com.intellij.java.ift.lesson.refactorings.JavaExtractMethodCocktailSortLesson
import com.intellij.java.ift.lesson.refactorings.JavaRefactoringMenuLesson
import com.intellij.java.ift.lesson.refactorings.JavaRenameLesson
import com.intellij.java.ift.lesson.run.JavaDebugLesson
import com.intellij.java.ift.lesson.run.JavaRunConfigurationLesson
import com.intellij.lang.java.JavaLanguage
import training.dsl.LessonUtil
import training.learn.LessonsBundle
import training.learn.course.LearningCourseBase
import training.learn.course.LearningModule
import training.learn.course.LessonType
import training.learn.lesson.general.*
import training.learn.lesson.general.assistance.CodeFormatLesson
import training.learn.lesson.general.assistance.ParameterInfoLesson
import training.learn.lesson.general.assistance.QuickPopupsLesson
import training.learn.lesson.general.navigation.FindInFilesLesson
import training.learn.lesson.general.refactorings.ExtractVariableFromBubbleLesson

class JavaLearningCourse : LearningCourseBase(JavaLanguage.INSTANCE.id) {
  override fun modules() = listOf(
    LearningModule(name = LessonsBundle.message("essential.module.name"),
                   description = LessonsBundle.message("essential.module.description", LessonUtil.productName),
                   primaryLanguage = langSupport,
                   moduleType = LessonType.SCRATCH) {
      fun ls(sampleName: String) = loadSample("EditorBasics/$sampleName")
      listOf(
        JavaContextActionsLesson(),
        GotoActionLesson(ls("00.Actions.java.sample"), firstLesson = false),
        JavaSearchEverywhereLesson(),
        JavaBasicCompletionLesson(),
      )
    },
    LearningModule(name = LessonsBundle.message("editor.basics.module.name"),
                   description = LessonsBundle.message("editor.basics.module.description"),
                   primaryLanguage = langSupport,
                   moduleType = LessonType.SCRATCH) {
      fun ls(sampleName: String) = loadSample("EditorBasics/$sampleName")
      listOf(
        JavaSelectLesson(),
        SingleLineCommentLesson(ls("02.Comment.java.sample")),
        DuplicateLesson(ls("04.Duplicate.java.sample")),
        MoveLesson("run()", ls("05.Move.java.sample")),
        CollapseLesson(ls("06.Collapse.java.sample")),
        JavaSurroundAndUnwrapLesson(),
        MultipleSelectionHtmlLesson(),
      )
    },
    LearningModule(name = LessonsBundle.message("code.completion.module.name"),
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
    LearningModule(name = LessonsBundle.message("refactorings.module.name"),
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
    LearningModule(name = LessonsBundle.message("code.assistance.module.name"),
                   description = LessonsBundle.message("code.assistance.module.description"),
                   primaryLanguage = langSupport,
                   moduleType = LessonType.SINGLE_EDITOR) {
      fun ls(sampleName: String) = loadSample("CodeAssistance/$sampleName")
      listOf(
        CodeFormatLesson(ls("CodeFormat.java.sample"), true),
        ParameterInfoLesson(ls("ParameterInfo.java.sample")),
        QuickPopupsLesson(ls("QuickPopups.java.sample")),
        JavaEditorCodingAssistanceLesson(ls("EditorCodingAssistance.java.sample")),
      )
    },
    LearningModule(name = LessonsBundle.message("navigation.module.name"),
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
    LearningModule(name = LessonsBundle.message("run.debug.module.name"),
                   description = LessonsBundle.message("run.debug.module.description"),
                   primaryLanguage = langSupport,
                   moduleType = LessonType.SINGLE_EDITOR) {
      listOf(
        JavaRunConfigurationLesson(),
        JavaDebugLesson(),
      )
    },
  )
}