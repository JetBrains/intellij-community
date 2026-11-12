// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.opassign;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

/**
 * @see ReplacePostfixExpressionWithAssignmentIntention
 */
@TestApplication
public class ReplacePostfixExpressionWithAssignmentIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp =
    ippFixture(projectFixture, "", CommonQuickFixBundle.message("fix.replace.x.with.y", "++", "="));

  @Test
  public void testIntIncrement() {
    ipp.get().doTest("""
             class X {{
                 int i = 10;
                 i/*_*/++;
             }}""",

           """
             class X {{
                 int i = 10;
                 i = i + 1;
             }}""");
  }

  @Test
  public void testLongIncrement() {
    ipp.get().doTest("""
             class X {{
                 long i = 10;
                 i/*_*/++;
             }}""",

           """
             class X {{
                 long i = 10;
                 i = i + 1;
             }}""");
  }

  @Test
  public void testByteIncrement() {
    ipp.get().doTestIntentionNotAvailable("""
                                  class X {{
                                      byte i = 10;
                                      i/*_*/++;
                                  }}""");
  }

  @Test
  public void testCharIncrement() {
    ipp.get().doTestIntentionNotAvailable("""
                                  class X {{
                                      char i = 10;
                                      i/*_*/++;
                                  }}""");
  }

  @Test
  public void testShortIncrement() {
    ipp.get().doTestIntentionNotAvailable("""
                                  class X {{
                                      short i = 10;
                                      i/*_*/++;
                                  }}""");
  }
}
