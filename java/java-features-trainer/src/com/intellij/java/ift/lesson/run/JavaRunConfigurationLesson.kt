// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift.lesson.run

import com.intellij.execution.ExecutionBundle
import com.intellij.icons.AllIcons
import com.intellij.java.ift.JavaLessonsBundle
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import training.learn.interfaces.Module
import training.learn.lesson.general.run.CommonRunConfigurationLesson
import training.learn.lesson.kimpl.LessonContext
import training.learn.lesson.kimpl.LessonSample
import training.learn.lesson.kimpl.dropMnemonic
import training.learn.lesson.kimpl.toolWindowShowed
import java.awt.Rectangle

class JavaRunConfigurationLesson(module: Module) : CommonRunConfigurationLesson(module, "java.run.configuration", "JAVA") {
  override val sample: LessonSample = JavaRunLessonsUtils.demoSample
  override val demoConfigurationName: String = JavaRunLessonsUtils.demoClassName

  override fun LessonContext.runTask() {
    task {
      triggerByPartOfComponent<EditorGutterComponentEx> l@{ ui ->
        if (CommonDataKeys.EDITOR.getData(ui as DataProvider) != editor) return@l null
        val y = editor.visualLineToY(0)
        return@l Rectangle(25, y, ui.width - 40, editor.lineHeight * 2)
      }
    }

    task("RunClass") {
      text(JavaLessonsBundle.message("java.run.configuration.lets.run", icon(AllIcons.Actions.Execute), action(it),
                                     strong(ExecutionBundle.message("default.runner.start.action.text").dropMnemonic())))
      //Wait toolwindow
      toolWindowShowed("Run")
      stateCheck {
        configurations().isNotEmpty()
      }
      test {
        actions(it)
      }
    }
  }

  override val fileName: String = "${JavaRunLessonsUtils.demoClassName}.java"
}
