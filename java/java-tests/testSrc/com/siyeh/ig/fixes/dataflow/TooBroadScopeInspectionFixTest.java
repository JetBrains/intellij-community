// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.dataflow;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.dataflow.TooBroadScopeInspection;

/**
 * @author Bas Leijdekkers
 */
public class TooBroadScopeInspectionFixTest extends IGQuickFixesTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new TooBroadScopeInspection());
    myRelativePath = "dataflow/too_broad_scope";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder builder) {
    builder.setLanguageLevel(LanguageLevel.JDK_10);
  }

  public void testForStatement() { doTest(InspectionGadgetsBundle.message("too.broad.scope.narrow.quickfix", "i")); }
  public void testForStatement2() { doTest(InspectionGadgetsBundle.message("too.broad.scope.narrow.quickfix", "i")); }
  public void testForStatement3() { doTest(InspectionGadgetsBundle.message("too.broad.scope.narrow.quickfix", "i")); }
  public void testTryResourceReference() { doTest(InspectionGadgetsBundle.message("too.broad.scope.narrow.quickfix", "t")); }
  public void testComments() { doTest(InspectionGadgetsBundle.message("too.broad.scope.narrow.quickfix", "s")); }
  public void testComments2() { doTest(InspectionGadgetsBundle.message("too.broad.scope.narrow.quickfix", "alpha")); }
  public void testVarDeclarationUntouched() { doTest(InspectionGadgetsBundle.message("too.broad.scope.narrow.quickfix", "i")); }
  public void testUnmovableStringBuilder() { assertQuickfixNotAvailable(InspectionGadgetsBundle.message("too.broad.scope.narrow.quickfix", "sb"));}
}
