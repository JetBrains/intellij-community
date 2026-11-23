package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInspection.deprecation.MarkedForRemovalInspection
import com.intellij.jvm.analysis.testFramework.jvmInspectionFixture
import com.intellij.jvm.analysis.testFramework.jvmProjectFixture
import com.intellij.testFramework.junit5.TestApplication

@TestApplication
abstract class KotlinMarkedForRemovalInspectionTest {
  companion object {
    private val projectFixture = jvmProjectFixture()
  }

  protected val myFixture by jvmInspectionFixture(projectFixture) { MarkedForRemovalInspection() }
}