// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.dataflow.UnnecessaryLocalVariableInspection;

public class InlineVariableFixTest extends IGQuickFixesTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new UnnecessaryLocalVariableInspection());
    myRelativePath = "inline";
    myDefaultHint = InspectionGadgetsBundle.message("inline.variable.quickfix");
  }

  @Override
  protected void tuneFixture(final JavaModuleFixtureBuilder builder) throws Exception {
    builder.setLanguageLevel(LanguageLevel.JDK_21);
  }

  public void testResourceVar() { doTest(); }
  public void testResourceVarInMiddle() { doTest(); }
  public void testSingleResourceVar() { doTest(); }
  public void testCastNeeded() { doTest(); }
  public void testArrayInitializer() { doTest(); }
  public void testCastForOverloads() { assertQuickfixNotAvailable(); }
  public void testComment() { doTest(); }
  public void testNewLineInInitializer() { doTest(); }
  public void testInlineInsideSwitchExpression() { doTest(); }
}
