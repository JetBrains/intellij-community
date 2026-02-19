// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SwitchStatementsWithPatternsInspectionTest extends LightJavaInspectionTestCase {
  private final SwitchStatementsWithoutDefaultInspection myInspection = new SwitchStatementsWithoutDefaultInspection();

  @Override
  protected String getBasePath() {
    return LightJavaInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/controlflow/switch_statements_with_patterns";
  }

  public void testIgnoreExhaustiveSwitchStatementsTrue() {
    myInspection.m_ignoreFullyCoveredEnums = true;
    doTest();
  }

  public void testIgnoreExhaustiveSwitchStatementsFalse() {
    myInspection.m_ignoreFullyCoveredEnums = false;
    doTest();
  }

  public void testTrueFalsePatterns() {
    String name = getTestName(false);
    myFixture.configureByFile(name + ".java");
    myFixture.testHighlighting(true, true, true);
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return myInspection;
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_23;
  }
}