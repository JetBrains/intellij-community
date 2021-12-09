// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import training.project.ReadMeCreator
import training.util.getFeedbackLink

class JavaLangSupport : JavaBasedLangSupport() {
  override val contentRootDirectoryName = "IdeaLearningProject"
  override val projectResourcePath = "learnProjects/java/LearnProject"

  override val primaryLanguage: String = "JAVA"

  override val defaultProductName: String = "IDEA"

  override val filename: String = "Learning.java"

  override val projectSandboxRelativePath: String = "Sample.java"

  override val langCourseFeedback get() = getFeedbackLink(this, false)

  override val readMeCreator = ReadMeCreator()

  override fun blockProjectFileModification(project: Project, file: VirtualFile): Boolean {
    return file.name != projectSandboxRelativePath
  }
}
