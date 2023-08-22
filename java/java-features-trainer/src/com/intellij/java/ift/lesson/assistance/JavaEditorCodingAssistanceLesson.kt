// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift.lesson.assistance

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.daemon.impl.runActionCustomShortcutSet
import com.intellij.java.ift.JavaLessonsBundle
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.siyeh.InspectionGadgetsBundle
import training.dsl.LessonSample
import training.dsl.LessonUtil
import training.learn.lesson.general.assistance.EditorCodingAssistanceLesson

class JavaEditorCodingAssistanceLesson(sample: LessonSample) :
  EditorCodingAssistanceLesson(sample) {
  override val errorIntentionText: String
    get() = QuickFixBundle.message("add.exception.to.throws.text", 1)
  override val warningIntentionText: String
    get() = InspectionGadgetsBundle.message("to.array.call.style.quickfix.make.zero")

  override val errorFixedText: String = "throws IOException"
  override val warningFixedText: String = "new String[0]"

  override val variableNameToHighlight: String = "lines"

  override fun getFixWarningText(): String {
    val shortcut = runActionCustomShortcutSet.shortcuts.first() as KeyboardShortcut
    return JavaLessonsBundle.message("java.editor.coding.assistance.press.to.fix", LessonUtil.rawKeyStroke(shortcut.firstKeyStroke))
  }
}