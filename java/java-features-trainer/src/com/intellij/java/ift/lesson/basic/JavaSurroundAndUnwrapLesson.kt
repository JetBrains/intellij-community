// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift.lesson.basic

import com.intellij.codeInsight.CodeInsightBundle
import org.jetbrains.annotations.Nls
import training.dsl.LessonSample
import training.dsl.parseLessonSample
import training.learn.lesson.general.SurroundAndUnwrapLesson

class JavaSurroundAndUnwrapLesson : SurroundAndUnwrapLesson() {
  override val sample: LessonSample = parseLessonSample("""
    class SurroundAndUnwrapDemo {
        public static void main(String[] args) {
            <select>System.out.println("Surround and Unwrap me!");</select>
        }
    }
  """.trimIndent())

  override val surroundItems: Array<String> = arrayOf("try", "catch", "finally")

  override val lineShiftBeforeUnwrap: Int = -2

  override val unwrapTryText: @Nls String = CodeInsightBundle.message("unwrap.try")
}
