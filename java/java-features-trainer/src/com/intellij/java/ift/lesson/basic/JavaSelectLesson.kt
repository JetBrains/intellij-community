// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift.lesson.basic

import training.dsl.LessonSample
import training.dsl.parseLessonSample
import training.learn.lesson.general.NewSelectLesson

class JavaSelectLesson : NewSelectLesson() {
  override val selectArgument = "\"$selectString\""
  override val selectCall = """someMethod("$firstString", $selectArgument, "$thirdString")"""

  override val numberOfSelectsForWholeCall = 4

  override val sample: LessonSample = parseLessonSample("""
    abstract class Scratch {
        abstract void someMethod(String string1, String string2, String string3);

        void exampleMethod(boolean condition) {
            <select id=1>if (condition) {
                System.err.println("$beginString");
                $selectCall;
                System.err.println("$endString");
            }</select>
        }
    }
  """.trimIndent())
  override val selectIf = sample.getPosition(1).selection!!.let { sample.text.substring(it.first, it.second) }
}