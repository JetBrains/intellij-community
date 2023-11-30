// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.migration;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.migration.WhileCanBeForeachInspection;

public class WhileCanBeForeachFixTest extends IGQuickFixesTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new WhileCanBeForeachInspection());
    myDefaultHint = InspectionGadgetsBundle.message("foreach.replace.quickfix");
  }

  public void testThis() { doTest(); }
  public void testCast() { doTest(); }
  public void testNakedNext() { doTest(); }
  public void testUnboundWildcard() { doTest(); }
  public void testVarWithoutValidInitializer() { doTest(); }
  public void testVarWithValidInitializer() { doTest(); }
  public void testRawIterator() { doTest(); }
  public void testUnboxing() { doTest(); }
  public void testParentheses() { doTest(); }
  public void testEmptyLoop() { doTest(); }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder builder) {
    builder.setLanguageLevel(LanguageLevel.HIGHEST);
  }

  @Override
  protected String getRelativePath() {
    return "migration/while_can_be_foreach";
  }
}
