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
import training.learn.LearningModule
import training.learn.LessonsBundle
import training.learn.course.LearningCourseBase
import training.learn.interfaces.LessonType
import training.learn.lesson.general.*
import training.learn.lesson.general.assistance.CodeFormatLesson
import training.learn.lesson.general.assistance.ParameterInfoLesson
import training.learn.lesson.general.assistance.QuickPopupsLesson
import training.learn.lesson.general.refactorings.ExtractVariableFromBubbleLesson
import training.learn.lesson.kimpl.LessonUtil

class JavaLearningCourse : LearningCourseBase(JavaLanguage.INSTANCE.id) {
  override fun modules() = listOf(
    LearningModule(name = LessonsBundle.message("essential.module.name"),
                   description = LessonsBundle.message("essential.module.description", LessonUtil.productName),
                   primaryLanguage = langSupport,
                   moduleType = LessonType.SCRATCH) {
      fun ls(sampleName: String) = loadSample("EditorBasics/$sampleName")
      listOf(
        JavaContextActionsLesson(it),
        GotoActionLesson(it, lang, ls("00.Actions.java.sample"), firstLesson = false),
        JavaSearchEverywhereLesson(it),
        JavaBasicCompletionLesson(it),
      )
    },
    LearningModule(name = LessonsBundle.message("editor.basics.module.name"),
                   description = LessonsBundle.message("editor.basics.module.description"),
                   primaryLanguage = langSupport,
                   moduleType = LessonType.SCRATCH) {
      fun ls(sampleName: String) = loadSample("EditorBasics/$sampleName")
      listOf(
        JavaSelectLesson(it),
        SingleLineCommentLesson(it, lang, ls("02.Comment.java.sample")),
        DuplicateLesson(it, lang, ls("04.Duplicate.java.sample")),
        MoveLesson(it, lang, "run()", ls("05.Move.java.sample")),
        CollapseLesson(it, lang, ls("06.Collapse.java.sample")),
        JavaSurroundAndUnwrapLesson(it),
        MultipleSelectionHtmlLesson(it),
      )
    },
    LearningModule(name = LessonsBundle.message("code.completion.module.name"),
                   description = LessonsBundle.message("code.completion.module.description"),
                   primaryLanguage = langSupport,
                   moduleType = LessonType.SCRATCH) {
      listOf(
        JavaBasicCompletionLesson(it),
        JavaSmartTypeCompletionLesson(it),
        JavaPostfixCompletionLesson(it),
        JavaStatementCompletionLesson(it),
        JavaCompletionWithTabLesson(it),
      )
    },
    LearningModule(name = LessonsBundle.message("refactorings.module.name"),
                   description = LessonsBundle.message("refactorings.module.description"),
                   primaryLanguage = langSupport,
                   moduleType = LessonType.SINGLE_EDITOR) {
      fun ls(sampleName: String) = loadSample("Refactorings/$sampleName")
      listOf(
        JavaRenameLesson(it),
        ExtractVariableFromBubbleLesson(it, lang, ls("ExtractVariable.java.sample")),
        JavaExtractMethodCocktailSortLesson(it),
        JavaRefactoringMenuLesson(it),
      )
    },
    LearningModule(name = LessonsBundle.message("code.assistance.module.name"),
                   description = LessonsBundle.message("code.assistance.module.description"),
                   primaryLanguage = langSupport,
                   moduleType = LessonType.SINGLE_EDITOR) {
      fun ls(sampleName: String) = loadSample("CodeAssistance/$sampleName")
      listOf(
        CodeFormatLesson(it, lang, ls("CodeFormat.java.sample"), true),
        ParameterInfoLesson(it, lang, ls("ParameterInfo.java.sample")),
        QuickPopupsLesson(it, lang, ls("QuickPopups.java.sample")),
        JavaEditorCodingAssistanceLesson(it, lang, ls("EditorCodingAssistance.java.sample")),
      )
    },
    LearningModule(name = LessonsBundle.message("navigation.module.name"),
                   description = LessonsBundle.message("navigation.module.description"),
                   primaryLanguage = langSupport,
                   moduleType = LessonType.PROJECT) {
      listOf(
        JavaSearchEverywhereLesson(it),
        JavaFileStructureLesson(it),
        JavaDeclarationAndUsagesLesson(it),
        JavaInheritanceHierarchyLesson(it),
        JavaRecentFilesLesson(it),
        JavaOccurrencesLesson(it),
      )
    },
    LearningModule(name = LessonsBundle.message("run.debug.module.name"),
                   description = LessonsBundle.message("run.debug.module.description"),
                   primaryLanguage = langSupport,
                   moduleType = LessonType.SINGLE_EDITOR) {
      listOf(
        JavaRunConfigurationLesson(it),
        JavaDebugLesson(it),
      )
    },
  )
}