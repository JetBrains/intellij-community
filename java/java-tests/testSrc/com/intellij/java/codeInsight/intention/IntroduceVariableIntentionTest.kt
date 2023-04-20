// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.intention

import com.siyeh.ipp.IPPTestCase

class IntroduceVariableIntentionTest : IPPTestCase() {
  fun `test create local variable from qualified call`() {
    doTestWithPreview("""
      class X {
          public static void foo(int i) {
              String./*_Introduce local variable*/valueOf(i);
          }
      }
    """.trimIndent(), """
      class X {
          public static void foo(int i) {
              String s = String.valueOf(i);
          }
      }
    """.trimIndent())
  }

  fun `test create local variable from non filled argument`() {
    doTestWithPreview("""
      class X {
          public static void bar(int i) { }
      
          public static void foo(String foo) {
              bar(/*_Introduce local variable*/);
          }
      }
    """.trimIndent(), """
      class X {
          public static void bar(int i) { }
      
          public static void foo(String foo) {
              int i = ;
              bar(i);
          }
      }
    """.trimIndent())
  }

  fun `test create local variable in if without braces`() {
    doTestWithPreview("""
      class X {
          public static void bar(int i) { }
      
          public static void foo(String foo) {
              if (true) bar(/*_Introduce local variable*/);
          }
      }
    """.trimIndent(), """
      class X {
          public static void bar(int i) { }
      
          public static void foo(String foo) {
              if (true) {
                  int i = ;
                  bar(i);
              }
          }
      }
    """.trimIndent())
  }
}