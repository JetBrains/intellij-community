// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.controlflow;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.controlflow.DefaultNotLastCaseInSwitchInspection;

public class MakeDefaultCaseLastFixTest extends IGQuickFixesTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new DefaultNotLastCaseInSwitchInspection());
    myRelativePath = "controlflow/makeDefaultLast";
    myDefaultHint = "Make 'default' the last case";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder builder) throws Exception {
    super.tuneFixture(builder);
    builder.setLanguageLevel(LanguageLevel.JDK_14);
  }

  public void testLabeledSwitchRule() { doTest(); }
  public void testOldStyleSwitchStatement() { doTest(); }
  public void testOldStyleSwitchStatementNoBreakThrough() { assertQuickfixNotAvailable(); }
  public void testOldStyleSwitchStatementNoBreakThrough1() { assertQuickfixNotAvailable(); }
}