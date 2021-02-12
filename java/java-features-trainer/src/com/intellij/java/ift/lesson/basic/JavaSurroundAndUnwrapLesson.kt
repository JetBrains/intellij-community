// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift.lesson.basic

import com.intellij.java.ift.JavaLessonsBundle
import training.dsl.LessonSample
import training.dsl.parseLessonSample
import training.learn.LessonsBundle
import training.learn.lesson.general.SurroundAndUnwrapLesson

class JavaSurroundAndUnwrapLesson : SurroundAndUnwrapLesson() {
  override val sample: LessonSample = parseLessonSample("""
    class SurroundAndUnwrapDemo {
        public static void main(String[] args) {
            <select>System.out.println("Surround and Unwrap me!");</select>
        }
    }
  """.trimIndent())

  override val surroundItems = arrayOf("try", "catch", "finally")

  override val lineShiftBeforeUnwrap = -2

  override val helpLinks: Map<String, String> = mapOf(
    Pair(LessonsBundle.message("surround.and.unwrap.help.surround.code.fragments"), "https://www.jetbrains.com/help/idea/surrounding-blocks-of-code-with-language-constructs.html"),
    Pair(JavaLessonsBundle.message("java.surround.and.unwrap.help.unwrapping.and.removing.statements"), "https://www.jetbrains.com/help/idea/working-with-source-code.html#editor_statement_select"),
  )
}
