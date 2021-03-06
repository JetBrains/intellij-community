// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift.lesson.completion

import com.intellij.java.ift.JavaLessonsBundle
import training.dsl.LessonContext
import training.dsl.LessonUtil.restoreIfModifiedOrMoved
import training.dsl.TaskTestContext
import training.dsl.parseLessonSample
import training.learn.course.KLesson

class JavaSmartTypeCompletionLesson : KLesson("Smart type completion", JavaLessonsBundle.message("java.smart.type.completion.lesson.name")) {

  override val testScriptProperties = TaskTestContext.TestScriptProperties(skipTesting = true)

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
            strings =
            arrayBlockingQueue = new ArrayBlockingQueue<String>(hashSet.size());
            for (String s : hashSet)
                arrayBlockingQueue.add(s);
        }
    
        private String[] toArray() {
            return <caret>
        }
    
    }
  """.trimIndent())

  override val lessonContent: LessonContext.() -> Unit = {
    prepareSample(sample)
    caret(13, 19)
    task {
      text(JavaLessonsBundle.message("java.smart.type.completion.apply", action("SmartTypeCompletion"), action("EditorChooseLookupItem")))
      trigger("SmartTypeCompletion")
      trigger("EditorChooseLookupItem")
      restoreIfModifiedOrMoved()
    }
    caret(20, 16)
    task {
      text(JavaLessonsBundle.message("java.smart.type.completion.return", action("SmartTypeCompletion"), action("EditorChooseLookupItem")))
      triggers("SmartTypeCompletion")
      trigger("EditorChooseLookupItem")
      restoreIfModifiedOrMoved()
    }
  }
}