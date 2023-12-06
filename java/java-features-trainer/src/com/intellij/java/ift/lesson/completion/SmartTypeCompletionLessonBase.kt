// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ift.lesson.completion

import com.intellij.java.ift.JavaLessonsBundle
import training.dsl.LessonContext
import training.dsl.LessonSample
import training.dsl.LessonUtil
import training.dsl.LessonUtil.restoreIfModifiedOrMoved
import training.dsl.TaskContext
import training.learn.LessonsBundle
import training.learn.course.KLesson
import training.util.isToStringContains

abstract class SmartTypeCompletionLessonBase : KLesson("Smart type completion", LessonsBundle.message("smart.completion.lesson.name")) {
  abstract val sample: LessonSample

  abstract val firstCompletionItem: String
  abstract val firstCompletionCheck: String

  abstract val secondCompletionItem: String
  abstract val secondCompletionCheck: String

  abstract fun LessonContext.setCaretForSecondItem()

  override val lessonContent: LessonContext.() -> Unit = {
    prepareSample(sample)
    task {
      text(JavaLessonsBundle.message("java.smart.type.completion.apply", action("SmartTypeCompletion"), action("EditorChooseLookupItem")))
      trigger("SmartTypeCompletion")
      triggerAndBorderHighlight().listItem {
        it.isToStringContains(firstCompletionItem)
      }
      stateCheck {
        val text = editor.document.text
        text.contains(firstCompletionCheck)
      }
      restoreIfModifiedOrMoved(sample)
      testSmartCompletion(firstCompletionItem)
    }
    setCaretForSecondItem()
    task {
      text(JavaLessonsBundle.message("java.smart.type.completion.return", action("SmartTypeCompletion"), action("EditorChooseLookupItem")))
      trigger("SmartTypeCompletion")
      triggerAndBorderHighlight().listItem {
        it.isToStringContains(secondCompletionItem)
      }
      stateCheck {
        val text = editor.document.text
        text.contains(secondCompletionCheck)
      }
      restoreIfModifiedOrMoved()
      testSmartCompletion(secondCompletionItem)
    }
  }

  private fun TaskContext.testSmartCompletion(item: String) {
    test {
      invokeActionViaShortcut("CTRL SHIFT SPACE")
      ideFrame {
        jListContains(item).item(0).doubleClick()
      }
    }
  }

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(LessonsBundle.message("help.code.completion"),
         LessonUtil.getHelpLink("auto-completing-code.html")),
  )
}