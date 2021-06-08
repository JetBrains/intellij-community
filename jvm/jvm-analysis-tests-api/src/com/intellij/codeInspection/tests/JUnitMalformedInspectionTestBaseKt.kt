package com.intellij.codeInspection.tests

import com.intellij.codeInspection.JUnit5MalformedParameterizedInspection
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase

abstract class JUnitMalformedInspectionTestBaseKt : JavaCodeInsightFixtureTestCase() {

  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
    moduleBuilder.setLanguageLevel(LanguageLevel.JDK_1_8)
  }

  override fun setUp() {
    super.setUp()
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