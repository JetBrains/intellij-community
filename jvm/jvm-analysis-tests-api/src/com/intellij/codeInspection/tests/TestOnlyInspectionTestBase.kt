package com.intellij.codeInspection.tests

import com.intellij.codeInspection.TestOnlyInspection
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase

abstract class TestOnlyInspectionTestBase : JavaCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(inspection)
    myFixture.addClass("""
  package org.jetbrains.annotations;
  
  import java.lang.annotation.*;
  
  @Documented
  @Retention(RetentionPolicy.CLASS)
  @Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.TYPE})
  public @interface VisibleForTesting { }
  """.trimIndent())

    myFixture.addClass("""
  package org.jetbrains.annotations;
  
  import java.lang.annotation.*;
  
  @Documented
  @Retention(RetentionPolicy.CLASS)
  @Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.TYPE})
  public @interface TestOnly { }
  """.trimIndent())
  }

  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
    moduleBuilder.addJdk(IdeaTestUtil.getMockJdk18Path().path)
    moduleBuilder.setLanguageLevel(LanguageLevel.JDK_1_8)
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
    private val inspection = TestOnlyInspection()
  }
}