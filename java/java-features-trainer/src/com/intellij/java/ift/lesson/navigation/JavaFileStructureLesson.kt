// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift.lesson.navigation

import com.intellij.openapi.editor.LogicalPosition
import training.learn.lesson.general.navigation.FileStructureLesson

class JavaFileStructureLesson : FileStructureLesson() {
  override val sampleFilePath: String = "src/FileStructureDemo.java"
  override val methodToFindPosition: LogicalPosition = LogicalPosition(74, 16)
}
