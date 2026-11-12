// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
 * @author Bas Leijdekkers
 */
@TestApplication
public class ReplaceOperatorAssignmentWithPostfixExpressionIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp =
    ippFixture(projectFixture, "opassign/assignment", CommonQuickFixBundle.message("fix.replace.x.with.y", "+=", "++"));

  @Test
  public void testSimple() {
    ipp.get().doTest("""
             class X {{
                 int i = 10;
                 i /*_*/+= (1);
             }}""",

           """
             class X {{
                 int i = 10;
                 i++;
             }}""");
  }

  @Test
  public void testBoxed() {
    ipp.get().doTest("""
             class X {{
                 Integer i = 10;
                 i /*_*/+= 1;
             }}""",

           """
             class X {{
                 Integer i = 10;
                 i++;
             }}""");
  }

  @Test
  public void testString() {
    ipp.get().doTestIntentionNotAvailable("class X {{" +
                                "  String s = \"zz\";" +
                                "  s /*_*/+= 1;" +
                                "}}");
  }
}