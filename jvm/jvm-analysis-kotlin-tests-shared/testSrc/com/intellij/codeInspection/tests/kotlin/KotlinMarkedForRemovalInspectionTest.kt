package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInspection.deprecation.MarkedForRemovalInspection
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

abstract class KotlinMarkedForRemovalInspectionTest : JvmInspectionTestBase(), ExpectedPluginModeProvider {
  override val inspection = MarkedForRemovalInspection()

  override fun setUp() {
    setUpWithKotlinPlugin(testRootDisposable) { super.setUp() }
  }
}