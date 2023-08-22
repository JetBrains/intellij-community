package com.intellij.codeInspection.tests.performance

import com.intellij.codeInspection.performance.UrlHashCodeInspection
import com.intellij.codeInspection.tests.JvmInspectionTestBase

abstract class UrlHashCodeInspectionTestBase : JvmInspectionTestBase() {
  override val inspection = UrlHashCodeInspection()

  override fun setUp() {
    super.setUp()
    // java.net is not present in Mock JDK
    myFixture.addClass("""
      package java.net;
      
      public class URL {
          public URL(String url) { }
          
          public int hashCode() { return 0; }
          
          public boolean equals(Object other) { return other instanceof URL && hashCode() == other.hashCode(); }
      }
    """.trimIndent())
  }
}