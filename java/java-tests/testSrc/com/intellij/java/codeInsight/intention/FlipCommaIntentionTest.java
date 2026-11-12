// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.codeInsight.intention;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class FlipCommaIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp = ippFixture(projectFixture);

  @Test
  public void testFlipParameters() {
    ipp.get().doTest("class C {" +
           "  void foo(int a,/*_Flip ','*/ String b) {}" +
           "}",
           "class C {" +
           "  void foo(String b, int a) {}" +
           "}");
  }

  @Test
  public void testMultipleFieldsSingleDeclaration() {
    ipp.get().doTest("""
             class C {
                 int a,/*_Flip ','*/ b;
             }""",

           """
             class C {
                 int b, a;
             }""");
  }

  @Test
  public void testMultipleFieldsSingleDeclaration2() {
    ipp.get().doTest("""
             class C {
               int a, b,/*_Flip ','*/ c;
             }""",

           """
             class C {
               int a, c, b;
             }""");
  }

  @Test
  public void testMultipleFieldsSingleDeclaration3() {
     ipp.get().doTest("""
              class C {
                String one = "one",/*_Flip ','*/ two ="two", three;
              }""",

            """
              class C {
                String two ="two", one = "one", three;
              }""");
  }

  @Test
  public void testIncomplete() {
    ipp.get().doTest("""
             class C {
               int a,/*_Flip ','*/ b =;
             }""",

           """
             class C {
               int b =, a;
             }""");
  }

  @Test
  public void testFlippingBrokenEnumConstantDoesNotCrashWithStubTextMismatch() {
    ipp.get().doTest("""
             enum E {
               A(1)/*_Flip ','*/,
               B(C(2),\s
               D(5);
             }
             """,

           """
             enum E {
               B(C(2),\s
               D(5),
               A(1);
             }
             """);
  }

  @Test
  public void testUnavailableForDangling() {
    ipp.get().doTestIntentionNotAvailable("class C {\n" +
                                "    int a[] = new int[]{1,2,/*_Flip ','*/};" +
                                "}");
  }

  @Test
  public void testLeftAndRightIdentical() {
    ipp.get().doTestIntentionNotAvailable("class C {" +
                                "  int[] ones = {1/*_Flip ','*/,1,1};" +
                                "}");
  }

  @Test
  public void testRecordComponent() {
    ipp.get().doTest("record A(String s,/*_Flip ','*/ int i) { }",
           "record A(int i, String s) { }");
  }
}
