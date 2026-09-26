// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift.lesson.basic

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.siyeh.IntentionPowerPackBundle
import training.dsl.LessonSample
import training.dsl.parseLessonSample
import training.learn.lesson.general.ContextActionsLesson

class JavaContextActionsLesson : ContextActionsLesson() {
  override val sample: LessonSample = parseLessonSample("""
    class Scratch {
        public static void main(String[] args) {
            methodWithUnusedParameter("first", "second");
            methodWithUnusedParameter("used", "unused");
        }

        private static void methodWithUnusedParameter(String used, String <caret>redundant) {
            System.err.println("It is used parameter: " + used);
        }

        public int intentionExample(boolean z, boolean a, boolean b) {
            if (!(z ? a : b)) return 1;
            return 2;
        }
    }
  """.trimIndent())

  override val warningQuickFix: String = QuickFixBundle.message("safe.delete.text", "redundant")
  override val warningPossibleArea: String = "redundant"

  override val intentionText: String = IntentionPowerPackBundle.message("negate.conditional.intention.name")
  override val intentionCaret: String = "? a : b"
  override val intentionPossibleArea: String = "z ? a : b"
}
