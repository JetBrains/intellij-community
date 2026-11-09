// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.controlflow;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class BreakStatementInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new BreakStatementInspection());

  @Test
  public void testSwitchExpression() {
    @Language("TEXT") String expr = "int i = switch (1) { case 1: yield 1; default: yield 2; };";
    //noinspection LanguageMismatch
    inspectionFixture.get().doStatementTest(expr);
  }

  @Test
  public void testSwitchStatement() {
    inspectionFixture.get().doStatementTest("switch (1) { case 1: {{ break; }} }");
  }

  @Test
  public void testWhileLoop() {
    inspectionFixture.get().doStatementTest("while (true) { if (1 == 1) /*'break' statement*/break/**/; }");
  }

}