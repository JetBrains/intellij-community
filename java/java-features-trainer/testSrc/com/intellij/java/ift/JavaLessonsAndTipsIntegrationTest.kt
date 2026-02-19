// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ift

import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import training.simple.LessonsAndTipsIntegrationTest

@RunWith(JUnit4::class)
internal class JavaLessonsAndTipsIntegrationTest : LessonsAndTipsIntegrationTest() {
  override val languageId = "JAVA"
  override val languageSupport = JavaLangSupport()
  override val learningCourse = JavaLearningCourse()
}