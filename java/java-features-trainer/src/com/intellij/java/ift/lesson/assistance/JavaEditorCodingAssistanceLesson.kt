// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift.lesson.assistance

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.testGuiFramework.fixtures.IdeFrameFixture
import com.intellij.testGuiFramework.impl.jList
import training.learn.interfaces.Module
import training.learn.lesson.general.assistance.EditorCodingAssistanceLesson
import training.learn.lesson.kimpl.LessonSample

class JavaEditorCodingAssistanceLesson(module: Module, lang: String, sample: LessonSample) :
  EditorCodingAssistanceLesson(module, lang, sample) {

  override fun IdeFrameFixture.simulateErrorFixing() {
    jList(intentionDisplayName).clickItem(intentionDisplayName)
  }

  override val fixedText: String = "throws IOException"

  override val intentionDisplayName: String
    get() = QuickFixBundle.message("add.exception.to.throws.family")

  override val variableNameToHighlight: String = "lines"
}