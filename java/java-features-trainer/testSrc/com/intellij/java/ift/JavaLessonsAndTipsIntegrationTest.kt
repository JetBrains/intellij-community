// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ift

import com.intellij.testFramework.junit5.TestApplication
import training.simple.LessonsAndTipsIntegrationTest

@TestApplication
internal class JavaLessonsAndTipsIntegrationTest : LessonsAndTipsIntegrationTest() {
  override val languageId = "JAVA"
  override val languageSupport = JavaLangSupport()
  override val learningCourse = JavaLearningCourse()
}