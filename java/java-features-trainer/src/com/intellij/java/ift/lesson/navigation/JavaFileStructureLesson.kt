// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift.lesson.navigation

import training.learn.interfaces.Module
import training.learn.lesson.general.navigation.FileStructureLesson

class JavaFileStructureLesson(module: Module) : FileStructureLesson(module, "JAVA") {
  override val searchSubstring: String = "hosa"
  override val firstWord: String = "homo"
  override val secondWord: String = "sapience"
  override val existedFile: String = "src/FileStructureDemo.java"
}
