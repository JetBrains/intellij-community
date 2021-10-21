// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift.lesson.completion

import com.intellij.java.ift.JavaLessonsBundle
import training.dsl.LessonContext
import training.dsl.LessonUtil
import training.dsl.LessonUtil.checkExpectedStateOfEditor
import training.dsl.LessonUtil.restoreIfModifiedOrMoved
import training.dsl.LessonUtil.restoreIfModifiedOrMovedIncorrectly
import training.dsl.TaskTestContext
import training.dsl.parseLessonSample
import training.learn.LessonsBundle
import training.learn.course.KLesson

class JavaBasicCompletionLesson : KLesson("Basic completion", LessonsBundle.message("basic.completion.lesson.name")) {
  val sample = parseLessonSample("""
    import java.lang.*;
    import java.util.*;
    
    import static java.lang.Byte.MAX_VALUE;
    
    class BasicCompletionDemo {
    
        private int i = 0;
    
        public void systemProcess(){
            System.out.println(i++);
        }
    
        public BasicCompletionDemo() {
            byte b = MAX_VALUE;
        }
    
        public void random() {
            Random random = new <caret>
        }
    }
  """.trimIndent())

  override val lessonContent: LessonContext.() -> Unit = {
    prepareSample(sample)
    task {
      text(LessonsBundle.message("basic.completion.start.typing", code("Ran")) +
           " " + JavaLessonsBundle.message("java.basic.completion.choose.first", action("EditorChooseLookupItem")))
      stateCheck {
        editor.document.charsSequence.contains("Random()")
      }
      proposeRestore {
        checkExpectedStateOfEditor(previous.sample) { typedString -> "Random".startsWith(typedString) }
      }
      test {
        type("Ran")
        doubleClickListItem("Random")
      }
    }
    caret(19, 36)
    task("CodeCompletion") {
      text(JavaLessonsBundle.message("java.basic.completion.activate", action(it)))
      triggerByListItemAndHighlight(false, false) { item -> item.toString() == "i" }
      restoreIfModifiedOrMoved()
      test {
        invokeCompletion()
      }
    }
    task {
      text(JavaLessonsBundle.message("java.basic.completion.choose.item",
                                     code("i"), action("EditorChooseLookupItem")))
      stateCheck {
        editor.document.charsSequence.contains("Random(i)")
      }
      restoreByUi()
      test {
        doubleClickListItem("i")
      }
    }
    actionTask("EditorCompleteStatement") {
      restoreIfModifiedOrMoved()
      JavaLessonsBundle.message("java.basic.completion.complete", action(it))
    }
    waitBeforeContinue(500)
    caret(15, 23)
    task("CodeCompletion") {
      text(JavaLessonsBundle.message("java.basic.completion.deeper.level", action(it)))
      triggers(it, it)
      restoreIfModifiedOrMovedIncorrectly(" MAX_VALUE")
      test {
        invokeCompletion()
        invokeCompletion()
      }
    }
    text(JavaLessonsBundle.message("java.basic.completion.module.promotion", strong(LessonsBundle.message("refactorings.module.name"))))
  }

  private fun TaskTestContext.doubleClickListItem(itemText: String) {
    ideFrame {
      jList(itemText).item(itemText).doubleClick()
    }
  }

  private fun TaskTestContext.invokeCompletion() = invokeActionViaShortcut("CTRL SPACE")

  override val suitableTips = listOf("CodeCompletion")

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(LessonsBundle.message("basic.completion.help.code.completion"),
         LessonUtil.getHelpLink("auto-completing-code.html#basic_completion")),
  )
}
