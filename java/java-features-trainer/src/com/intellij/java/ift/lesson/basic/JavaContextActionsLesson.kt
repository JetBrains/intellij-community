// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift.lesson.basic

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.siyeh.IntentionPowerPackBundle
import training.learn.interfaces.Module
import training.learn.lesson.general.ContextActionsLesson
import training.learn.lesson.kimpl.LessonSample
import training.learn.lesson.kimpl.parseLessonSample

class JavaContextActionsLesson(module: Module) : ContextActionsLesson(module, "JAVA") {
  override val sample: LessonSample = parseLessonSample("""
    class Scratch {
      public static void main(String[] args) {
        methodWithUnusedParameter("first", "second");
        methodWithUnusedParameter("used", "unused");
      }

      private static void methodWithUnusedParameter(String used, String redundant) {
        System.err.println("It is used parameter: " + used);
      }
      
      public int intentionExample(boolean z, boolean a, boolean b) {
        if (!(z ? a : b)) return 1;
        return 2;
      }
    }
  """.trimIndent())

  override val warningQuickFix: String = QuickFixBundle.message("safe.delete.text", "redundant")
  override val warningCaret: String = "redundant)"

  override val generalIntention: String = IntentionPowerPackBundle.message("negate.conditional.intention.name")
  override val generalIntentionCaret: String = "? a : b"
}
