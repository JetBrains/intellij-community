// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift.lesson.run

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.icons.AllIcons
import com.intellij.java.ift.JavaLessonsBundle
import training.dsl.LessonContext
import training.dsl.LessonSample
import training.dsl.LessonUtil.highlightRunGutter
import training.dsl.addNewRunConfigurationFromContext
import training.dsl.checkToolWindowState
import training.dsl.dropMnemonic
import training.dsl.parseLessonSample
import training.learn.lesson.general.run.CommonRunConfigurationLesson

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
      highlightRunGutter()
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
