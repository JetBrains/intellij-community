// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift.lesson.navigation

import training.learn.LessonsBundle
import training.learn.interfaces.Module
import training.learn.lesson.general.navigation.SearchEverywhereLesson
import training.learn.lesson.kimpl.LessonContext

class JavaSearchEverywhereLesson(module: Module) : SearchEverywhereLesson(module, "JAVA") {
  override val existedFile = "src/RecentFilesDemo.java"
  override val resultFileName: String = "QuadraticEquationsSolver.java"

  override fun LessonContext.epilogue() {
    text(LessonsBundle.message("search.everywhere.navigation.promotion", strong(LessonsBundle.message("navigation.module.name"))))
  }
}
