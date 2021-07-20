package com.intellij.codeInspection.tests

import com.intellij.codeInspection.JUnitRuleInspection
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase

abstract class JUnitRuleInspectionTestBase : JavaCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(inspection)

    myFixture.addClass("""
  package org.junit.rules;
  public interface TestRule {}
  """.trimIndent())

    myFixture.addClass("""
  package org.junit.rules;
  public @interface MethodRule {}
  """.trimIndent())

    myFixture.addClass("""
  package org.junit;
  
  import java.lang.annotation.ElementType;
  import java.lang.annotation.Retention;
  import java.lang.annotation.RetentionPolicy;
  import java.lang.annotation.Target;
  
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.FIELD, ElementType.METHOD})
  public @interface Rule {}
  """.trimIndent())

    myFixture.addClass("""
  package org.junit;
  
  import java.lang.annotation.ElementType;
  import java.lang.annotation.Retention;
  import java.lang.annotation.RetentionPolicy;
  import java.lang.annotation.Target;  
  
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.FIELD, ElementType.METHOD})
  public @interface ClassRule {}
  """.trimIndent())

    myFixture.addClass("""
  package test;
  
  import org.junit.rules.TestRule
  import org.junit.ClassRule

  class SomeTestRule implements TestRule { }
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
    private val inspection = JUnitRuleInspection()
  }
}