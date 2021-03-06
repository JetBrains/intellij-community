// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift.lesson.completion

import training.dsl.parseLessonSample
import training.learn.lesson.general.CompletionWithTabLesson

class JavaCompletionWithTabLesson :
  CompletionWithTabLesson("DO_NOTHING_ON_CLOSE") {
  override val sample = parseLessonSample("""import javax.swing.*;

class FrameDemo {

    public static void main(String[] args) {
        JFrame frame = new JFrame("FrameDemo");
        frame.setSize(175, 100);

        frame.setDefaultCloseOperation(WindowConstants.<caret>DISPOSE_ON_CLOSE);
        frame.setVisible(true);
    }
}""".trimIndent())
}