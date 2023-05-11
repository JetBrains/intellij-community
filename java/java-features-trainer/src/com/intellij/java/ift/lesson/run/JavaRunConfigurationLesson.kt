// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift.lesson.run

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.icons.AllIcons
import com.intellij.java.ift.JavaLessonsBundle
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import training.dsl.*
import training.learn.lesson.general.run.CommonRunConfigurationLesson
import java.awt.Rectangle

class JavaRunConfigurationLesson : CommonRunConfigurationLesson("java.run.configuration") {
  private val demoClassName = "Sample"

  override val sample: LessonSample = parseLessonSample("""
    public class $demoClassName {
        public static void main(String[] args) {
            System.out.println("It is a run configurations sample");
            for (String arg: args) {
                System.out.println("Passed argument: " + arg);
            }
        }
    }
  """.trimIndent())

  override val demoConfigurationName: String = demoClassName

  override fun LessonContext.runTask() {
    task {
      caret(1, 1)
      highlightRunGutters()
    }

    task("RunClass") {
      text(JavaLessonsBundle.message("java.run.configuration.lets.run",
                                     icon(AllIcons.RunConfigurations.TestState.Run),
                                     strong(ExecutionBundle.message("default.runner.start.action.text").dropMnemonic()),
                                     action(it)))
      timerCheck { configurations().isNotEmpty() }
      //Wait toolwindow
      checkToolWindowState("Run", true)
      test {
        actions(it)
      }
    }
  }

  override fun LessonContext.addAnotherRunConfiguration() {
    prepareRuntimeTask {
      addNewRunConfigurationFromContext { runConfiguration ->
        runConfiguration.name = demoWithParametersName
        (runConfiguration as ApplicationConfiguration).programParameters = "hello world"
      }
    }
  }

  override val sampleFilePath: String = "src/${demoClassName}.java"
}

internal fun TaskContext.highlightRunGutters(highlightInside: Boolean = false, usePulsation: Boolean = false) {
  triggerAndBorderHighlight {
    this.highlightInside = highlightInside
    this.usePulsation = usePulsation
  }.componentPart l@{ ui: EditorGutterComponentEx ->
    if (CommonDataKeys.EDITOR.getData(ui as DataProvider) != editor) return@l null
    val runGutterLines = (0 until editor.document.lineCount).mapNotNull { lineInd ->
      if (ui.getGutterRenderers(lineInd).any { (it as? LineMarkerInfo.LineMarkerGutterIconRenderer<*>)?.featureId == "run" })
        lineInd
      else null
    }
    if (runGutterLines.size < 2) return@l null
    val startLineY = editor.visualLineToY(runGutterLines.first())
    val endLineY = editor.visualLineToY(runGutterLines.last())
    Rectangle(25, startLineY, ui.width - 40, endLineY - startLineY + editor.lineHeight)
  }
}
