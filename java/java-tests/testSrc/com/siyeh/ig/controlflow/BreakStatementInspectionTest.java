// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class BreakStatementInspectionTest extends LightJavaInspectionTestCase {
  public void testSwitchExpression() {
    @Language("TEXT") String expr = "int i = switch (1) { case 1: yield 1; default: yield 2; };";
    //noinspection LanguageMismatch
    doStatementTest(expr);
  }

  public void testSwitchStatement() {
    doStatementTest("switch (1) { case 1: {{ break; }} }");
  }

  public void testWhileLoop() {
    doStatementTest("while (true) { if (1 == 1) /*'break' statement*/break/**/; }");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new BreakStatementInspection();
  }

}