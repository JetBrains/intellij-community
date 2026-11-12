// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.shift;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

@TestApplication
public class ReplaceMultiplyWithShiftIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp = ippFixture(projectFixture, "shift/replace_multiply_with_shift");

  @Test
  public void testLeftShift() { ipp.get().doTest("Replace '*' with '<<'"); }
  @Test
  public void testLongShift() { ipp.get().doTest("Replace '*' with '<<'"); }
  @Test
  public void testCastedLongShift() { ipp.get().doTest("Replace '*' with '<<'"); }
  @Test
  public void testLeftShiftAssign() { ipp.get().doTest("Replace '*=' with '<<='"); }
  @Test
  public void testParentheses() { ipp.get().doTest("Replace '*' with '<<'"); }
  @Test
  public void testRightShift() { ipp.get().doTest("Replace '/' with '>>'"); }
  @Test
  public void testRightShiftNA() { ipp.get().assertIntentionNotAvailable("Replace '/' with '>>'"); }
}
