// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.dataflow;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.dataflow.TooBroadScopeInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class TooBroadScopeInspectionFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new TooBroadScopeInspection()), "dataflow/too_broad_scope", null,
                        builder -> builder.setLanguageLevel(LanguageLevel.JDK_10));

  @Test
  public void testForStatement() { ig.get().doTest(InspectionGadgetsBundle.message("too.broad.scope.narrow.quickfix", "i")); }
  @Test
  public void testForStatement2() { ig.get().doTest(InspectionGadgetsBundle.message("too.broad.scope.narrow.quickfix", "i")); }
  @Test
  public void testForStatement3() { ig.get().doTest(InspectionGadgetsBundle.message("too.broad.scope.narrow.quickfix", "i")); }
  @Test
  public void testTryResourceReference() { ig.get().doTest(InspectionGadgetsBundle.message("too.broad.scope.narrow.quickfix", "t")); }
  @Test
  public void testComments() { ig.get().doTest(InspectionGadgetsBundle.message("too.broad.scope.narrow.quickfix", "s")); }
  @Test
  public void testComments2() { ig.get().doTest(InspectionGadgetsBundle.message("too.broad.scope.narrow.quickfix", "alpha")); }
  @Test
  public void testVarDeclarationUntouched() { ig.get().doTest(InspectionGadgetsBundle.message("too.broad.scope.narrow.quickfix", "i")); }
  @Test
  public void testMayChangeSemantics() { ig.get().doTest(InspectionGadgetsBundle.message("too.broad.scope.narrow.may.change.semantics.quickfix", "value")); }
  @Test
  public void testUnmovableStringBuilder() { ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("too.broad.scope.narrow.quickfix", "sb"));}
}
