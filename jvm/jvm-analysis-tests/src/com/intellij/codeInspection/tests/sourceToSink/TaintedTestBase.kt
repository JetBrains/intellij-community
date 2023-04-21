package com.intellij.codeInspection.tests.sourceToSink

import com.intellij.codeInspection.tests.JvmInspectionTestBase

abstract class TaintedTestBase : JvmInspectionTestBase() {
  fun prepareCheckFramework() {
    myFixture.addClass("""
                               package org.checkerframework.checker.tainting.qual;
                               import java.lang.annotation.ElementType;
                               import java.lang.annotation.Target;
                               @Target({ElementType.LOCAL_VARIABLE, ElementType.FIELD, ElementType.METHOD})
                               public @interface Tainted {
                               }
                               """.trimIndent())
    myFixture.addClass("""
                               package org.checkerframework.checker.tainting.qual;
                               import java.lang.annotation.ElementType;
                               import java.lang.annotation.Target;
                               @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
                               public @interface Untainted {
                               }
                               """.trimIndent())
  }

  fun prepareJsr() {
    myFixture.addClass("""
                               package javax.annotation;
                               import java.lang.annotation.ElementType;
                               import java.lang.annotation.Target;
                               @Target({ElementType.LOCAL_VARIABLE, ElementType.FIELD, ElementType.METHOD})
                               public @interface Tainted {
                               }
                               """.trimIndent())
    myFixture.addClass("""
                               package javax.annotation;
                               import java.lang.annotation.ElementType;
                               import java.lang.annotation.Target;
                               @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
                               public @interface Untainted {
                               }
                               """.trimIndent())
  }

}