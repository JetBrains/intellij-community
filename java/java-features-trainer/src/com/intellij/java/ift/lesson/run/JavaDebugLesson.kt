// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ift.lesson.run

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.icons.AllIcons
import com.intellij.java.ift.JavaLessonsBundle
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.IdeActions
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

internal class JavaDebugLesson : CommonDebugLesson("java.debug.workflow") {
  override val testScriptProperties: TaskTestContext.TestScriptProperties = TaskTestContext.TestScriptProperties(duration = 60)

  private val demoClassName = "Sample"
  override val configurationName: String = demoClassName

  override val sample: LessonSample = parseLessonSample("""
    public class $demoClassName {
        public static void main(String[] args) {
            double average = findAverage(prepareValues());
            System.out.println("The average is " + average);
        }

        private static double findAverage(String[] input) {
            checkInput(input);
            double result = 0;
            for (String s : input) {
                <caret>result += <select id=1>validateNumber(extractNumber(removeQuotes(s)))</select>;
            }
            <caret id=3/>return <select id=4>result / input.length</select>;
        }

        private static String[] prepareValues() {
            return new String[] {"'apple 1'", "orange 2", "'tomato 3'"};
        }

        private static int extractNumber(String s) {
            return Integer.parseInt(<select id=2>s.split(" ")[0]</select>);
        }

        private static void checkInput(String[] input) {
            if (input == null || input.length == 0) {
                throw new IllegalArgumentException("Invalid input");
            }
        }

        private static String removeQuotes(String s) {
            if (s.startsWith("'") && s.endsWith("'") && s.length() > 1) {
                return s.substring(1, s.length() - 1);
            }
            return s;
        }

        private static int validateNumber(int number) {
            if (number < 0) throw new IllegalArgumentException("Invalid number: " + number);
            return number;
        }
    }
  """.trimIndent())

  override var logicalPosition: LogicalPosition = LogicalPosition(10, 6)
  private val debugLineNumber = StringUtil.offsetToLineNumber(sample.text, sample.getPosition (2).startOffset)

  override val confNameForWatches: String = "Application"
  override val quickEvaluationArgument: String = "Integer.parseInt"
  override val debuggingMethodName: String = "findAverage"
  override val methodForStepInto: String = "extractNumber"
  override val stepIntoDirectionToRight: Boolean = true

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

    task("CompileDirty") {
      text(JavaLessonsBundle.message("java.debug.workflow.rebuild", action(it)))
      if (isAlwaysHotSwap()) {
        addFutureStep {
          subscribeForMessageBus(Notifications.TOPIC, object : Notifications {
            override fun notify(notification: Notification) {
              if (notification.actions.singleOrNull()?.templateText == JavaDebuggerBundle.message("status.hot.swap.completed.stop")) {
                completeStep()
              }
            }
          })
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
        dialog(JavaDebuggerBundle.message("hotswap.dialog.title.with.session", demoClassName)) {
          button("Reload").click()
        }
      }
    }

    task {
      triggerAndBorderHighlight { usePulsation = true }.listItem { item ->
        (item as? JavaStackFrame)?.descriptor.isToStringContains("extractNumber")
      }
    }

    task(IdeActions.ACTION_RESET_FRAME) {
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
