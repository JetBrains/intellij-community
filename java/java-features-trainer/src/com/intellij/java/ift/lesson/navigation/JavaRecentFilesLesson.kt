// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift.lesson.navigation

import training.dsl.LessonContext
import training.dsl.LessonUtil
import training.learn.LessonsBundle
import training.learn.lesson.general.navigation.RecentFilesLesson

class JavaRecentFilesLesson : RecentFilesLesson() {
  override val sampleFilePath: String = "src/RecentFilesDemo.java"

  override val transitionMethodName: String = "println"
  override val transitionFileName: String = "PrintStream"
  override val stringForRecentFilesSearch: String = "print"

  override fun LessonContext.setInitialPosition() = caret("println")

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(LessonsBundle.message("recent.files.locations.help.link"),
         LessonUtil.getHelpLink("idea", "discover-intellij-idea.html#recent-files")),
  )
}