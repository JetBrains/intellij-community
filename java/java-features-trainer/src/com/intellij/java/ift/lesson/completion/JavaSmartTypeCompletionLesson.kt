// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift.lesson.completion

import training.dsl.*

class JavaSmartTypeCompletionLesson : SmartTypeCompletionLessonBase() {
  override val sample: LessonSample = parseLessonSample("""
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

  override val firstCompletionItem: String = "linkedList"
  override val firstCompletionCheck: String = "strings = linkedList;"

  override val secondCompletionItem: String = "arrayBlockingQueue"
  override val secondCompletionCheck: String = "return arrayBlockingQueue.toArray(new String[0]);"

  override fun LessonContext.setCaretForSecondItem() {
    caret(20, 16)
  }
}