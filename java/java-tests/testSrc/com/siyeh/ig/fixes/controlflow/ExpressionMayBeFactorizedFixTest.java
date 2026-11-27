// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.controlflow;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.controlflow.ExpressionMayBeFactorizedInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Fabrice TIERCELIN
 */
@TestApplication
public class ExpressionMayBeFactorizedFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new ExpressionMayBeFactorizedInspection()));

  @Test
  public void testSimple() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                 """
                   boolean test(boolean duplicateExpression, boolean thenExpression, boolean elseExpression) {
                       return (duplicateExpression &&/**/ thenExpression) || (duplicateExpression && elseExpression);
                   }""",
                 """
                   boolean test(boolean duplicateExpression, boolean thenExpression, boolean elseExpression) {
                       return duplicateExpression && (thenExpression || elseExpression);
                   }"""
    );
  }

  @Test
  public void testFactorInControlWorkflow() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                 """
                   char test(boolean duplicateExpression, boolean thenExpression, boolean elseExpression) {
                       if ((duplicateExpression &&/**/ thenExpression) || (duplicateExpression && elseExpression)) {
                           return 'a';
                       } else {
                           return 'b';
                       }
                   }""",
                 """
                   char test(boolean duplicateExpression, boolean thenExpression, boolean elseExpression) {
                       if (duplicateExpression && (thenExpression || elseExpression)) {
                           return 'a';
                       } else {
                           return 'b';
                       }
                   }"""
    );
  }

  @Test
  public void testFactorLast() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                 """
                   boolean test(int duplicateExpression, int thenExpression, int elseExpression) {
                       return (thenExpression == 2 &&/**/ duplicateExpression == 1) || (elseExpression == 3 && duplicateExpression == 1);
                   }""",
                 """
                   boolean test(int duplicateExpression, int thenExpression, int elseExpression) {
                       return (thenExpression == 2 || elseExpression == 3) && duplicateExpression == 1;
                   }"""
    );
  }

  @Test
  public void testFactorInMiddle() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                 """
                   boolean test(boolean duplicateExpression, boolean payload) {
                       boolean reassigned = false;
                       return (true &/**/ duplicateExpression) | duplicateExpression & ((reassigned = payload));
                   }""",
                 """
                   boolean test(boolean duplicateExpression, boolean payload) {
                       boolean reassigned = false;
                       return duplicateExpression & (true | (reassigned = payload));
                   }"""
    );
  }

  @Test
  public void testOrInsideAnd() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                 """
                   boolean test(boolean duplicateExpression, boolean thenExpression, boolean elseExpression) {
                       return (duplicateExpression ||/**/ thenExpression) && (duplicateExpression || elseExpression);
                   }""",
                 """
                   boolean test(boolean duplicateExpression, boolean thenExpression, boolean elseExpression) {
                       return duplicateExpression || (thenExpression && elseExpression);
                   }"""
    );
  }

  @Test
  public void testEagerOperators() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                 """
                   boolean test(boolean duplicateExpression, boolean payload) {
                       boolean reassigned = false;
                       return ((reassigned = payload)/**/ & duplicateExpression) | (true & duplicateExpression);
                   }""",
                 """
                   boolean test(boolean duplicateExpression, boolean payload) {
                       boolean reassigned = false;
                       return ((reassigned = payload) | true) & duplicateExpression;
                   }"""
    );
  }

  @Test
  public void testNumericExpression() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                 """
                   int test(int duplicateExpression, int payload) {
                       int reassigned = 1;
                       return ((reassigned = payload)/**/ & duplicateExpression) | (2 & duplicateExpression);
                   }""",
                 """
                   int test(int duplicateExpression, int payload) {
                       int reassigned = 1;
                       return ((reassigned = payload) | 2) & duplicateExpression;
                   }"""
    );
  }

  @Test
  public void testNumericOperation() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                 """
                   int fullHeight(int rowHeight, int rowCount, int rowMargin) {
                     return rowHeight * rowCount + /**/rowMargin * rowCount;
                   }""",
                 """
                   int fullHeight(int rowHeight, int rowCount, int rowMargin) {
                     return (rowHeight + rowMargin) * rowCount;
                   }""");
  }

  @Test
  public void testNumericOperation2() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                               """
                                 class X {
                                   int fullHeight(int rowHeight, int rowCount, int rowMargin) {
                                     return rowHeight + rowCount * /**/rowMargin + rowCount;
                                   }}""");
  }

  @Test
  public void testFinalEagerActiveExpression() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                 """
                   boolean test(int x, int y, int updatedState, int newState) {
                       return (x > y & updatedState == -1) | /**/(y < x & (updatedState = newState) == 0);
                     }""",
                 """
                   boolean test(int x, int y, int updatedState, int newState) {
                       return y < x & (updatedState == -1 | (updatedState = newState) == 0);
                     }""");
  }

  @Test
  public void testComparison() {
    ig.get().doTest(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
           """
             class X {
               boolean test(int x, int y, int z) {
                 return (x > y && z == 1) || /**/(y < x && z == 2);
               }
             }""",
           """
             class X {
               boolean test(int x, int y, int z) {
                 return y < x && (z == 1 || z == 2);
               }
             }""");
  }

  @Test
  public void testDoNotCleanupOnExpressionWithSideEffects() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                               """
                                 class X {
                                   boolean elseExpression = true;
                                   boolean test(boolean thenExpression) {
                                     return activeMethod() && thenExpression ||/**/ activeMethod() && elseExpression;
                                   }
                                   boolean activeMethod() {
                                     elseExpression = !elseExpression;
                                     return true;
                                   }
                                 }
                                 """);
  }

  @Test
  public void testDoNotCleanupWithXOrOperator() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                               """
                                 class X {
                                   boolean test() {
                                     boolean duplicateExpression = true;
                                     boolean thenExpression = false;
                                     boolean elseExpression = true;
                                     return (duplicateExpression ^ thenExpression) &&/**/ (duplicateExpression ^ elseExpression);
                                   }
                                 }
                                 """);
  }

  @Test
  public void testDoNotCleanupWithFinalLazyActiveExpression() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                               """
                                 class X {
                                   boolean test() {
                                     boolean duplicateExpression = true;
                                     boolean thenExpression = true;
                                     boolean updatedExpression = true;
                                     boolean newValue = false;
                                     boolean evaluation = (duplicateExpression || thenExpression) &&/**/ (duplicateExpression || (updatedExpression = newValue));
                                     return updatedExpression;
                                   }
                                 }
                                 """);
  }

  @Test
  public void testDoNotCleanupWithNotFinalEagerActiveExpression() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                               """
                                 class X {
                                   boolean test() {
                                     boolean duplicateExpression = true;
                                     boolean newValue = false;
                                     boolean elseExpression = false;
                                     return (duplicateExpression | (duplicateExpression = newValue)) &/**/ (duplicateExpression | elseExpression);
                                   }
                                 }
                                 """);
  }

  @Test
  public void testDoNotCleanupWithTrailingOperands() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                               """
                                 class X {
                                   boolean test(boolean evilExpression) {
                                     boolean duplicateExpression = true;
                                     boolean thenExpression = false;
                                     boolean elseExpression = true;
                                     return (duplicateExpression || thenExpression) &&/**/ (duplicateExpression || elseExpression) && evilExpression;
                                   }
                                 }
                                 """);
  }

  @Test
  public void testDoNotCleanupOnIncrementedDuplicateExpression() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                               """
                                 class X {
                                   int test() {
                                     int duplicateExpression = 0;
                                     boolean thenExpression = false;
                                     boolean elseExpression = false;
                                     boolean dummy = thenExpression && (duplicateExpression++ < 100) ||/**/ elseExpression && (duplicateExpression++ < 100);
                                     return duplicateExpression;
                                   }
                                 }
                                 """);
  }

  @Test
  public void testDoNotCleanupOperandWithSideEffect() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                               """
                                 class X {
                                   boolean test() {
                                     boolean duplicateExpression = true;
                                     boolean expressionWithSideEffect = false;
                                     boolean elseExpression = false;
                                     boolean dummy = (duplicateExpression || (expressionWithSideEffect = true)) &&/**/ (elseExpression || duplicateExpression);
                                     return expressionWithSideEffect;
                                   }
                                 }
                                 """);
  }

  @Test
  public void testDoNotCleanupOperandWithSideEffectAtTheEnd() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                               """
                                 class X {
                                   boolean test() {
                                     boolean duplicateExpression = false;
                                     boolean thenExpression = true;
                                     boolean expressionWithSideEffect = false;
                                     boolean dummy = (thenExpression || duplicateExpression) &&/**/ (duplicateExpression || (expressionWithSideEffect = true));
                                     return expressionWithSideEffect;
                                   }
                                 }
                                 """);
  }

  @Test
  public void testDoNotCleanupWithSkippedThrowingExceptionOperand() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                               """
                                 class X {
                                   boolean test() {
                                     boolean duplicateExpression = false;
                                     boolean thenExpression = true;
                                     return (duplicateExpression && thenExpression) ||/**/ (duplicateExpression && throwingException());
                                   }
                                   boolean throwingException() {
                                     throw null;
                                   }
                                 }
                                 """);
  }

}
