// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift.lesson.completion

import com.intellij.java.ift.JavaLangSupport
import com.intellij.java.ift.JavaLessonsBundle
import training.learn.LessonsBundle
import training.learn.interfaces.Module
import training.learn.lesson.kimpl.KLesson
import training.learn.lesson.kimpl.LessonContext
import training.learn.lesson.kimpl.parseLessonSample

class JavaBasicCompletionLesson(module: Module)
  : KLesson("Basic completion", LessonsBundle.message("basic.completion.lesson.name"), module, JavaLangSupport.lang) {

  val sample = parseLessonSample("""
    import java.lang.*;
    import java.util.*;
    
    class BasicCompletionDemo implements Runnable{
    
        private int i = 0;
    
        public void systemProcess(){
            System.out.println(i++);
        }
    
        public BasicCompletionDemo() {
            byte b = MAX_VALUE
        }
    
        @Override
        public void run() {
            Random random = new <caret>
        }
    }
  """.trimIndent())

  override val lessonContent: LessonContext.() -> Unit = {
    prepareSample(sample)
    actionTask("EditorChooseLookupItem") {
      LessonsBundle.message("basic.completion.start.typing", code("Ran")) +
      " " + JavaLessonsBundle.message("java.basic.completion.choose.first", action(it))
    }
    caret(18, 36)
    actionTask("CodeCompletion") {
      JavaLessonsBundle.message("java.basic.completion.activate", action(it))
    }
    actionTask("EditorChooseLookupItem") {
      JavaLessonsBundle.message("java.basic.completion.choose.item", code("i"), action(it))
    }
    actionTask("EditorCompleteStatement") {
      JavaLessonsBundle.message("java.basic.completion.complete", action(it))
    }
    task("CodeCompletion") {
      caret(13, 27)
      text(JavaLessonsBundle.message("java.basic.completion.deeper.level", action(it)))
      triggers(it, it)
    }
    text(JavaLessonsBundle.message("java.basic.completion.module.promotion", strong(LessonsBundle.message("refactorings.module.name"))))
  }
}
