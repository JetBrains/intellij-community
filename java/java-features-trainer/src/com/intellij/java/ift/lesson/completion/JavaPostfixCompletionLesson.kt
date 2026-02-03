// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift.lesson.completion

import com.intellij.java.ift.JavaLessonsBundle
import training.dsl.LearningDslBase
import training.dsl.LessonSample
import training.dsl.parseLessonSample
import training.learn.lesson.general.completion.PostfixCompletionLesson

class JavaPostfixCompletionLesson : PostfixCompletionLesson() {
  override val sample: LessonSample = parseLessonSample("""
    class PostfixCompletionDemo {
        public void demonstrate(int show_times) {
            (show_times == 10)<caret>
        }
    }
  """.trimIndent())

  override val result: String = parseLessonSample("""
    class PostfixCompletionDemo {
        public void demonstrate(int show_times) {
            if (show_times == 10) {
                
            }
        }
    }
  """.trimIndent()).text

  override val completionSuffix: String = "."
  override val completionItem: String = "if"

  override fun LearningDslBase.getTypeTaskText(): String {
    return JavaLessonsBundle.message("java.postfix.completion.type", code(completionSuffix))
  }

  override fun LearningDslBase.getCompleteTaskText(): String {
    return JavaLessonsBundle.message("java.postfix.completion.complete", code(completionItem), action("EditorChooseLookupItem"))
  }
}