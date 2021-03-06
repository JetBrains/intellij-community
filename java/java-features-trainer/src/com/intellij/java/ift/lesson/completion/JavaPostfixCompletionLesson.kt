// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift.lesson.completion

import com.intellij.java.ift.JavaLessonsBundle
import training.dsl.LessonContext
import training.dsl.parseLessonSample
import training.learn.LessonsBundle
import training.learn.course.KLesson

class JavaPostfixCompletionLesson
  : KLesson("Postfix completion", LessonsBundle.message("postfix.completion.lesson.name")) {

  val sample = parseLessonSample("""
    class PostfixCompletionDemo{
        public void demonstrate(int show_times){
            (show_times == 10)<caret>
        }
    }
  """.trimIndent())

  override val lessonContent: LessonContext.() -> Unit = {
    prepareSample(sample)
    actionTask("EditorChooseLookupItem") {
      JavaLessonsBundle.message("java.postfix.completion.apply", code("."), code("if"), action("EditorChooseLookupItem"))
    }
  }
}