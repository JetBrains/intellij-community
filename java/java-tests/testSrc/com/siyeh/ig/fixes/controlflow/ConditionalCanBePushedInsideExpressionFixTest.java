// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.controlflow;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.controlflow.ConditionalCanBePushedInsideExpressionInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class ConditionalCanBePushedInsideExpressionFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new ConditionalCanBePushedInsideExpressionInspection()));


  @Test
  public void testSideEffects1() {
    ig.get().doTest(InspectionGadgetsBundle.message("conditional.can.be.pushed.inside.expression.quickfix"),
           """
             class X {
                 String foo(boolean b) {
                     return b/*c0*/ ? String.v<caret>alueOf(new Double(0)) //c1
                             : String.valueOf(new Double(1.2))//c2
                             ;
                 }
             }""",
           """
             class X {
                 String foo(boolean b) {
                     /*c0*/
                     //c1
                     return String.valueOf(new Double(b ? 0 : 1.2))//c2
                             ;
                 }
             }""");
  }
}
