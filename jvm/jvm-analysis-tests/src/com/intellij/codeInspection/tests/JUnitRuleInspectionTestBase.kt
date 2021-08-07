package com.intellij.codeInspection.tests

import com.intellij.codeInspection.JUnitRuleInspection

abstract class JUnitRuleInspectionTestBase : UastInspectionTestBase(inspection) {
  override fun setUp() {
    super.setUp()

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

  companion object {
    private val inspection = JUnitRuleInspection()
  }
}