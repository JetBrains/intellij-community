package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInspection.deprecation.MarkedForRemovalInspection
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase

abstract class KotlinMarkedForRemovalInspectionTest : JvmInspectionTestBase() {
  override val inspection = MarkedForRemovalInspection()

  
}