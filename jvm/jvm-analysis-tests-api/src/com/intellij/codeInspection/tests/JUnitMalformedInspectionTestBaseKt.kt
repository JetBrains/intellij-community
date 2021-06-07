package com.intellij.codeInspection.tests

import com.intellij.codeInspection.JUnit5MalformedParameterizedInspection
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.roots.JavaProjectModelModificationService
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase

abstract class JUnitMalformedInspectionTestBaseKt : JavaCodeInsightFixtureTestCase() {

  private fun setLanguageLevel(level: LanguageLevel) {
    invokeAndWaitIfNeeded {
      runWriteAction {
        JavaProjectModelModificationService.getInstance(project).changeLanguageLevel(module, level)
      }
    }
  }

  override fun setUp() {
    super.setUp()
    setLanguageLevel(LanguageLevel.JDK_1_8)
    myFixture.enableInspections(inspection)

    myFixture.addFileToProject("kotlin/jvm/JvmStatic.kt",
                               "package kotlin.jvm public annotation class JvmStatic")

    myFixture.addClass("""
    package java.util.stream;
    public interface Stream {}
    """.trimIndent())
  }

  override fun tearDown() {
    try {
      myFixture.disableInspections(inspection)
    }
    finally {
      super.tearDown()
    }
  }

  companion object {
    private val inspection = JUnit5MalformedParameterizedInspection()
  }
}