// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ift

import training.project.ReadMeCreator
import training.util.getFeedbackLink

internal class JavaLangSupport : JavaBasedLangSupport() {
  override val contentRootDirectoryName = "IdeaLearningProject"
  override val projectResourcePath = "learnProjects/java/LearnProject"

  override val primaryLanguage: String = "JAVA"

  override val defaultProductName: String = "IDEA"

  override val scratchFileName: String = "Learning.java"

  override val sampleFilePath: String = "src/Sample.java"

  override val langCourseFeedback
    get() = getFeedbackLink(this, false)

  override val readMeCreator by lazy { ReadMeCreator() }
}
