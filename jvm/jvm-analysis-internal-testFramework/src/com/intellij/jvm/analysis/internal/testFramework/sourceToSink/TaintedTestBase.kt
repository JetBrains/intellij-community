package com.intellij.jvm.analysis.internal.testFramework.sourceToSink

import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase

abstract class TaintedTestBase : JvmInspectionTestBase() {
  fun prepareCheckFramework() {
    myFixture.addClass("""
                               package org.checkerframework.checker.tainting.qual;
                               import java.lang.annotation.ElementType;
                               import java.lang.annotation.Target;
                               @Target({ElementType.LOCAL_VARIABLE, ElementType.FIELD, ElementType.METHOD, ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
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
                               public @interface Tainted {
                               }
                               """.trimIndent())
    myFixture.addClass("""
                               package javax.annotation;
                               public @interface Untainted {
                               }
                               """.trimIndent())
  }

}