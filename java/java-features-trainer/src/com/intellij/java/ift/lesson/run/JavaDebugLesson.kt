// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift.lesson.run

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.icons.AllIcons
import com.intellij.java.ift.JavaLessonsBundle
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.options.OptionsBundle
import com.intellij.openapi.util.text.StringUtil
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.XDebuggerManager
import training.dsl.*
import training.learn.CourseManager
import training.learn.lesson.general.run.CommonDebugLesson
import training.statistic.LessonStartingWay
import training.ui.LearningUiManager
import training.util.isToStringContains
import javax.swing.JEditorPane

class JavaDebugLesson : CommonDebugLesson("java.debug.workflow") {

  override val testScriptProperties = TaskTestContext.TestScriptProperties(duration = 60)

  private val demoClassName = JavaRunLessonsUtils.demoClassName
  override val configurationName: String = demoClassName
  override val sample = JavaRunLessonsUtils.demoSample
  override var logicalPosition: LogicalPosition = LogicalPosition(10, 6)
  private val debugLineNumber = StringUtil.offsetToLineNumber(sample.text, sample.getPosition (2).startOffset)

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
        triggerUI().component { ui: JEditorPane ->
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

    task {
      triggerAndBorderHighlight { usePulsation = true }.listItem { item ->
        (item as? JavaStackFrame)?.descriptor.isToStringContains("extractNumber")
      }
    }

    task("Debugger.PopFrame") {
      text(JavaLessonsBundle.message("java.debug.workflow.drop.frame", code("extractNumber"), code("extractNumber"),
                                icon(AllIcons.Actions.InlineDropFrame), action(it)))
      stateCheck {
        val currentSession = XDebuggerManager.getInstance(project).currentSession ?: return@stateCheck false
        currentSession.currentPosition?.line == logicalPosition.line
      }
      proposeRestore {
        val currentSession = XDebuggerManager.getInstance(project).currentSession
        val line = currentSession?.currentPosition?.line
        if (line == null || !(line == debugLineNumber || line == logicalPosition.line)) {
          TaskContext.RestoreNotification(JavaLessonsBundle.message("java.debug.workflow.invalid.drop")) {
            CourseManager.instance.openLesson(project, this@JavaDebugLesson, LessonStartingWay.RESTORE_LINK)
          }
        } else null
      }
      test { actions(it) }
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

  override val sampleFilePath: String = "src/$demoClassName.java"
}
