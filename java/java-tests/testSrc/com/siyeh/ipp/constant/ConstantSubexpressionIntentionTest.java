// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.constant;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

@TestApplication
public class ConstantSubexpressionIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp = ippFixture(projectFixture);

  @Test
  public void testPlus() {
    ipp.get().doTest("""
             class X {
               void test(int a) { int res = a + 1 /*_Compute constant value of '1 + 2'*/+ 2 + 3; }
             }""",
           """
             class X {
               void test(int a) { int res = a + 3 + 3; }
             }""");
  }

  @Test
  public void testMinus() {
    ipp.get().doTestIntentionNotAvailable("""
                                  class X {
                                    void test(int a) { int res = a - 1 /*_Compute constant value of '1 - 2'*/- 2 - 3; }
                                  }""");
  }
}
