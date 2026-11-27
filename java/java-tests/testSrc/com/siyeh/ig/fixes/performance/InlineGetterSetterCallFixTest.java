// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.performance;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.performance.CallToSimpleGetterInClassInspection;
import com.siyeh.ig.performance.CallToSimpleSetterInClassInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class InlineGetterSetterCallFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new CallToSimpleSetterInClassInspection(), new CallToSimpleGetterInClassInspection()),
                        "performance/simple_setter");

  @Test
  public void testParentheses() { ig.get().doTest(InspectionGadgetsBundle.message("call.to.simple.setter.in.class.inline.quickfix")); }
  @Test
  public void testQualifiedCall() { ig.get().doTest(InspectionGadgetsBundle.message("call.to.simple.getter.in.class.inline.quickfix")); }
  @Test
  public void testSimpleField() { ig.get().doTest(InspectionGadgetsBundle.message("call.to.simple.getter.in.class.inline.quickfix")); }
  @Test
  public void testNameClash() { ig.get().doTest(InspectionGadgetsBundle.message("call.to.simple.getter.in.class.inline.quickfix")); }
  @Test
  public void testTopLevelGetterCall() { ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("call.to.simple.getter.in.class.inline.quickfix")); }
}