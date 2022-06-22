// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift.lesson.completion

import com.intellij.java.ift.JavaLessonsBundle
import training.dsl.LessonContext
import training.dsl.LessonUtil
import training.dsl.LessonUtil.restoreIfModifiedOrMoved
import training.dsl.TaskContext
import training.dsl.parseLessonSample
import training.learn.LessonsBundle
import training.learn.course.KLesson

class JavaSmartTypeCompletionLesson : KLesson("Smart type completion", LessonsBundle.message("smart.completion.lesson.name")) {
  val sample = parseLessonSample("""
    import java.lang.String;
    import java.util.HashSet;
    import java.util.LinkedList;
    import java.util.Queue;
    import java.util.concurrent.ArrayBlockingQueue;
    
    class SmartCompletionDemo{
    
        private Queue<String> strings;
        private ArrayBlockingQueue<String> arrayBlockingQueue;
    
        public SmartCompletionDemo(LinkedList<String> linkedList, HashSet<String> hashSet) {
            strings = <caret>
            arrayBlockingQueue = new ArrayBlockingQueue<String>(hashSet.size());
            for (String s : hashSet)
                arrayBlockingQueue.add(s);
        }
    
        private String[] toArray() {
            return 
        }
    
    }
  """.trimIndent())

  override val lessonContent: LessonContext.() -> Unit = {
    prepareSample(sample)
    task {
      text(JavaLessonsBundle.message("java.smart.type.completion.apply", action("SmartTypeCompletion"), action("EditorChooseLookupItem")))
      trigger("SmartTypeCompletion")
      stateCheck {
        val text = editor.document.text
        text.contains("strings = arrayBlockingQueue;") || text.contains("strings = linkedList;")
      }
      restoreIfModifiedOrMoved(sample)
      testSmartCompletion()
    }
    caret(20, 16)
    task {
      text(JavaLessonsBundle.message("java.smart.type.completion.return", action("SmartTypeCompletion"), action("EditorChooseLookupItem")))
      trigger("SmartTypeCompletion")
      stateCheck {
        val text = editor.document.text
        text.contains("return arrayBlockingQueue.toArray(new String[0]);")
        || text.contains("return strings.toArray(new String[0]);")
      }
      restoreIfModifiedOrMoved()
      testSmartCompletion()
    }
  }

  private fun TaskContext.testSmartCompletion() {
    test {
      invokeActionViaShortcut("CTRL SHIFT SPACE")
      ideFrame {
        jListContains("arrayBlockingQueue").item(0).doubleClick()
      }
    }
  }

  override val suitableTips = listOf("SmartTypeCompletion", "SmartTypeAfterNew", "SecondSmartCompletionToar")

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(LessonsBundle.message("help.code.completion"),
         LessonUtil.getHelpLink("auto-completing-code.html")),
  )
}