// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift.lesson.run

import com.intellij.execution.ExecutionBundle
import com.intellij.icons.AllIcons
import com.intellij.java.ift.JavaLessonsBundle
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import training.dsl.*
import training.learn.lesson.general.run.CommonRunConfigurationLesson
import java.awt.Rectangle

class JavaRunConfigurationLesson : CommonRunConfigurationLesson("java.run.configuration") {
  override val sample: LessonSample = JavaRunLessonsUtils.demoSample
  override val demoConfigurationName: String = JavaRunLessonsUtils.demoClassName

  override fun LessonContext.runTask() {
    task {
      highlightRunGutters(0)
    }

    task("RunClass") {
      text(JavaLessonsBundle.message("java.run.configuration.lets.run", icon(AllIcons.Actions.Execute), action(it),
                                     strong(ExecutionBundle.message("default.runner.start.action.text").dropMnemonic())))
      //Wait toolwindow
      checkToolWindowState("Run", true)
      test {
        actions(it)
      }
    }
  }

  override val sampleFilePath: String = "src/${JavaRunLessonsUtils.demoClassName}.java"
}

internal fun TaskContext.highlightRunGutters(startLineIndex: Int, highlightInside: Boolean = false, usePulsation: Boolean = false) {
  triggerAndBorderHighlight {
    this.highlightInside = highlightInside
    this.usePulsation = usePulsation
  }.componentPart l@{ ui: EditorGutterComponentEx ->
    if (CommonDataKeys.EDITOR.getData(ui as DataProvider) != editor) return@l null
    val y = editor.visualLineToY(startLineIndex)
    return@l Rectangle(25, y, ui.width - 40, editor.lineHeight * 2)
  }
}
