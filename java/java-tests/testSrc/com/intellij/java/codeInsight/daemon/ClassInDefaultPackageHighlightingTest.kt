// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon

import com.intellij.idea.Bombed
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import java.util.*

class ClassInDefaultPackageHighlightingTest : LightCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.addClass("""
        public class MyConstants {
          public static final int CONSTANT = 1;
          public static class Inner {
            public static final String INNER_CONSTANT = "const";
          }""".trimIndent())
  }

  fun testAccessFromDefaultPackage() {
    doTest("""
        class C {
          private int field = MyConstants.CONSTANT;
        }""".trimIndent())
  }

  fun testImportsFromDefaultPackage() {
    doTest("""
        import <error descr="Class 'MyConstants' is in the default package">MyConstants</error>;
        import <error descr="Class 'MyConstants' is in the default package">MyConstants</error>.Inner;
        import static <error descr="Class 'MyConstants' is in the default package">MyConstants</error>.*;
        import static <error descr="Class 'MyConstants' is in the default package">MyConstants</error>.Inner.*;
        import static <error descr="Class 'MyConstants' is in the default package">MyConstants</error>.Inner.INNER_CONSTANT;
        """.trimIndent())
  }

  @Bombed(user = "Roman Shevchenko", day = 1, month = Calendar.DECEMBER)
  fun testAccessFromNormalCode() {
    doTest("""
        package pkg;
        import <error descr="Cannot resolve symbol 'MyConstants'">MyConstants</error>;
        import <error descr="Cannot resolve symbol 'MyConstants'">MyConstants</error>.Inner;
        class C {
          <error descr="Cannot resolve symbol 'MyConstants'">MyConstants</error> f = null;
          Object o = new <error descr="Cannot resolve symbol 'MyConstants'">MyConstants</error>.Inner();
          int i = <error descr="Cannot resolve symbol 'MyConstants'">MyConstants</error>.CONSTANT;
        }""".trimIndent())
  }

  private fun doTest(text: String) {
    myFixture.configureByText("test.java", text)
    myFixture.checkHighlighting()
  }
}