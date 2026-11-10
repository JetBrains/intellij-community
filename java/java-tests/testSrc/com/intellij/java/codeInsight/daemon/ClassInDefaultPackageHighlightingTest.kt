// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon

import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class ClassInDefaultPackageHighlightingTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUp() {
    myFixture.addClass("""
        public class conflict { }""".trimIndent())
    myFixture.addClass("""
        package conflict;
        public class C { }""".trimIndent())
    myFixture.addClass("""
        public class MyConstants {
          public static final int CONSTANT = 1;
          public static class Inner {
            public static final String INNER_CONSTANT = "const";
          }""".trimIndent())
  }

  @Test
  fun testClassPackageConflictInImport() {
    doTest("""
        package conflict;
        import conflict.C;
        class Test {
          C c = new C();
        }""".trimIndent())
  }

  @Test
  fun testClassPackageConflictInOnDemandImport() {
    doTest("""
        package conflict;
        import conflict.*;
        class Test {
          C c = new C();
        }""".trimIndent())
  }

  @Test
  fun testClassPackageConflictInFQRefs() {
    doTest("""
        class Test {{
          new conflict();
          new conflict.<error descr="Cannot resolve symbol 'C'">C</error>();
        }}""".trimIndent())
  }

  @Test
  fun testAccessFromDefaultPackage() {
    doTest("""
        class C {
          private int field = MyConstants.CONSTANT;
        }""".trimIndent())
  }

  @Test
  fun testImportsFromDefaultPackage() {
    doTest("""
        import <error descr="Class 'MyConstants' is in the default package">MyConstants</error>;
        import <error descr="Class 'MyConstants' is in the default package">MyConstants</error>.Inner;
        import static <error descr="Class 'MyConstants' is in the default package">MyConstants</error>.*;
        import static <error descr="Class 'MyConstants' is in the default package">MyConstants</error>.Inner.*;
        import static <error descr="Class 'MyConstants' is in the default package">MyConstants</error>.Inner.INNER_CONSTANT;
        """.trimIndent())
  }

  @Test
  fun testAccessFromNormalCode() {
    doTest("""
        package pkg;
        import <error descr="Class 'MyConstants' is in the default package">MyConstants</error>;
        import <error descr="Class 'MyConstants' is in the default package">MyConstants</error>.Inner;
        class C {
          <error descr="Class 'MyConstants' is in the default package">MyConstants</error> f = null;
          Object o = new <error descr="Class 'MyConstants' is in the default package">MyConstants</error>.Inner();
          int i = <error descr="Class 'MyConstants' is in the default package">MyConstants</error>.CONSTANT;
        }""".trimIndent())
  }

  private fun doTest(text: String) {
    myFixture.configureByText("test.java", text)
    myFixture.checkHighlighting()
  }
}