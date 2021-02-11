// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift.lesson.run

import com.intellij.icons.AllIcons
import com.intellij.java.ift.JavaLessonsBundle
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.testGuiFramework.impl.button
import training.dsl.LessonContext
import training.dsl.TaskTestContext
import training.dsl.highlightButtonById
import training.learn.lesson.general.run.CommonDebugLesson

class JavaDebugLesson : CommonDebugLesson("java.debug.workflow", "JAVA") {
  private val demoClassName = JavaRunLessonsUtils.demoClassName
  override val configurationName: String = demoClassName
  override val sample = JavaRunLessonsUtils.demoSample
  override var logicalPosition: LogicalPosition = LogicalPosition(10, 6)

  override val confNameForWatches: String = "Application"
  override val quickEvaluationArgument = "Integer.parseInt"
  override val expressionToBeEvaluated = "result/input.length"
  override val debuggingMethodName = "findAverage"
  override val methodForStepInto: String = "extractNumber"
  override val stepIntoDirection = "→"

  override fun LessonContext.applyProgramChangeTasks() {
    highlightButtonById("CompileDirty")

    task("CompileDirty") {
      text(JavaLessonsBundle.message("java.debug.workflow.rebuild", action(it), icon(AllIcons.Actions.Compile)))
      stateCheck {
        inHotSwapDialog()
      }
      proposeModificationRestore(afterFixText)
      test { actions(it) }
    }

    task {
      text(JavaLessonsBundle.message("java.debug.workflow.confirm.hot.swap"))
      stateCheck {
        !inHotSwapDialog()
      }
      proposeModificationRestore(afterFixText)
      test {
        with(TaskTestContext.guiTestCase) {
          dialog(null, needToKeepDialog = true) {
            button("Yes").click()
          }
        }
      }
    }

    highlightButtonById("Debugger.PopFrame")

    actionTask("Debugger.PopFrame") {
      proposeModificationRestore(afterFixText)
      JavaLessonsBundle.message("java.debug.workflow.drop.frame", code("extractNumber"), code("extractNumber"), icon(AllIcons.Actions.PopFrame), action(it))
    }
  }

  private fun inHotSwapDialog(): Boolean {
    return Thread.currentThread().stackTrace.any { traceElement ->
      traceElement.className.contains("HotSwapUIImpl")
    }
  }

  override val testScriptProperties: TaskTestContext.TestScriptProperties
    get() = TaskTestContext.TestScriptProperties(duration = 20)

  override val fileName: String = "$demoClassName.java"
}
