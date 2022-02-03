// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift.lesson.run

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.icons.AllIcons
import com.intellij.java.ift.JavaLessonsBundle
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.options.OptionsBundle
import com.intellij.xdebugger.XDebuggerBundle
import training.dsl.LessonContext
import training.dsl.TaskTestContext
import training.dsl.highlightButtonById
import training.dsl.restoreChangedSettingsInformer
import training.learn.lesson.general.run.CommonDebugLesson
import training.ui.LearningUiManager
import javax.swing.JEditorPane

class JavaDebugLesson : CommonDebugLesson("java.debug.workflow") {

  override val testScriptProperties = TaskTestContext.TestScriptProperties(duration = 60)

  private val demoClassName = JavaRunLessonsUtils.demoClassName
  override val configurationName: String = demoClassName
  override val sample = JavaRunLessonsUtils.demoSample
  override var logicalPosition: LogicalPosition = LogicalPosition(10, 6)

  override val confNameForWatches: String = "Application"
  override val quickEvaluationArgument = "Integer.parseInt"
  override val debuggingMethodName = "findAverage"
  override val methodForStepInto: String = "extractNumber"
  override val stepIntoDirectionToRight = true

  override fun LessonContext.applyProgramChangeTasks() {

    if (isHotSwapDisabled()) {
      task {
        val feature = stateCheck { !isHotSwapDisabled() }
        val callbackId = LearningUiManager.addCallback {
          feature.complete(true)
          DebuggerSettings.getInstance().RUN_HOTSWAP_AFTER_COMPILE = DebuggerSettings.RUN_HOTSWAP_ASK
        }
        showWarning(JavaLessonsBundle.message("java.debug.workflow.hotswap.disabled.warning",
                                              strong(OptionsBundle.message("configurable.group.build.settings.display.name")),
                                              strong(OptionsBundle.message("exportable.XDebuggerSettings.presentable.name")),
                                              strong(XDebuggerBundle.message("debugger.hotswap.display.name")),
                                              strong(JavaDebuggerBundle.message("label.debugger.hotswap.configurable.reload.classes")
                                                       .removeSuffix(":")),
                                              callbackId)) {
          isHotSwapDisabled()
        }
      }
    }

    highlightButtonById("CompileDirty")

    task("CompileDirty") {
      text(JavaLessonsBundle.message("java.debug.workflow.rebuild", action(it), icon(AllIcons.Actions.Compile)))
      if (isAlwaysHotSwap()) {
        triggerByUiComponentAndHighlight(highlightBorder = false, highlightInside = false) { ui: JEditorPane ->
          ui.text.contains(JavaDebuggerBundle.message("status.hot.swap.completed.stop"))
        }
      }
      else {
        stateCheck {
          inHotSwapDialog()
        }
      }
      proposeModificationRestore(afterFixText)
      test { actions(it) }
    }

    task {
      if (!isAlwaysHotSwap()) {
        text(JavaLessonsBundle.message("java.debug.workflow.confirm.hot.swap"))
      }
      else {
        text(JavaLessonsBundle.message("java.debug.workflow.no.confirmation"))
      }
      stateCheck {
        isAlwaysHotSwap() || !inHotSwapDialog()
      }
      proposeModificationRestore(afterFixText)
      test(waitEditorToBeReady = false) {
        dialog(JavaDebuggerBundle.message("hotswap.dialog.title.with.session", JavaRunLessonsUtils.demoClassName)) {
          button("Reload").click()
        }
      }
    }

    highlightButtonById("Debugger.PopFrame")

    actionTask("Debugger.PopFrame") {
      proposeModificationRestore(afterFixText)
      JavaLessonsBundle.message("java.debug.workflow.drop.frame", code("extractNumber"), code("extractNumber"),
                                icon(AllIcons.Actions.PopFrame), action(it))
    }
  }

  private fun runHotSwapAfterCompile() = DebuggerSettings.getInstance().RUN_HOTSWAP_AFTER_COMPILE
  private fun isHotSwapDisabled() = runHotSwapAfterCompile() == DebuggerSettings.RUN_HOTSWAP_NEVER
  private fun isAlwaysHotSwap() = runHotSwapAfterCompile() == DebuggerSettings.RUN_HOTSWAP_ALWAYS

  private fun inHotSwapDialog(): Boolean {
    return Thread.currentThread().stackTrace.any { traceElement ->
      traceElement.className.contains("HotSwapUIImpl")
    }
  }

  override fun LessonContext.restoreHotSwapStateInformer() {
    if (!isHotSwapDisabled()) return
    restoreChangedSettingsInformer {
      DebuggerSettings.getInstance().RUN_HOTSWAP_AFTER_COMPILE = DebuggerSettings.RUN_HOTSWAP_NEVER
    }
  }

  override val fileName: String = "$demoClassName.java"
}
