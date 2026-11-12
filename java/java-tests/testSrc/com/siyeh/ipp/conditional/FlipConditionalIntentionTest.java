// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.conditional;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

@TestApplication
public class FlipConditionalIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp = ippFixture(projectFixture);

  @Test
  public void testComments() {
    ipp.get().doTest("class X {" +
           "    void test(boolean foo, boolean bar) {" +
           "        boolean c = false;" +
           "        boolean b = /*_Flip '?:'*/foo ? true : c || bar;//comment at the end\n" +
           "    }" +
           "}",
           "class X {    void test(boolean foo, boolean bar) {        boolean c = false;        boolean b = !foo ? c || bar : true;//comment at the end\n" +
           "    }}");
  }

  @Test
  public void testIncomplete() {
    ipp.get().doTest("""
             class X {
               void test() {
                 System.out.println(/*_Flip '?:'*/!()?"foo":"bar");
               }
             }""",
           """
             class X {
               void test() {
                 System.out.println(() ? "bar" : "foo");
               }
             }""");
  }

  @Test
  public void testIncomplete2() {
    ipp.get().doTestIntentionNotAvailable("""
                                  class X {
                                    void test(int a, int b) {
                                      int x = /*_Flip '?:'*/a ? 1 : b ? 2 : ;
                                    }
                                  }""");
  }
}
