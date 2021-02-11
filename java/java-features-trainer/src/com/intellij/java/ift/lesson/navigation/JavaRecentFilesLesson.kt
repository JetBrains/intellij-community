// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift.lesson.navigation

import training.learn.lesson.general.navigation.RecentFilesLesson
import training.learn.lesson.kimpl.LessonContext

class JavaRecentFilesLesson : RecentFilesLesson("JAVA") {
  override val existedFile: String = "src/RecentFilesDemo.java"

  override val transitionMethodName: String = "println"
  override val transitionFileName: String = "PrintStream"
  override val stringForRecentFilesSearch: String = "print"

  override fun LessonContext.setInitialPosition() = caret("println")
}