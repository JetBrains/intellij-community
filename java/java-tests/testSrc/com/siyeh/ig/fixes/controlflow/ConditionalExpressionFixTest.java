// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.controlflow;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.controlflow.ConditionalExpressionInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class ConditionalExpressionFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new ConditionalExpressionInspection()), "controlflow/conditional_expression",
                        InspectionGadgetsBundle.message("conditional.expression.quickfix"),
                        builder -> builder.setLanguageLevel(LanguageLevel.JDK_14));

  @Test
  public void testThisCall() { ig.get().assertQuickfixNotAvailable(); }
  @Test
  public void testBrokenCode() { ig.get().assertQuickfixNotAvailable(); }
  @Test
  public void testIncompleteCode() { ig.get().doTest(); }
  @Test
  public void testIncompleteCode2() { ig.get().doTest(); }
  @Test
  public void testField() { ig.get().doTest(); }
  @Test
  public void testNonDenotableVar() { ig.get().assertQuickfixNotAvailable(); }

  @Test
  public void testArrayInitializer() { ig.get().doTest(); }
  @Test
  public void testExpandVar() { ig.get().doTest(); }
  @Test
  public void testCastNeeded() { ig.get().doTest(); }
  @Test
  public void testComment() { ig.get().doTest(); }
  @Test
  public void testCommentWithDeclaration() { ig.get().doTest(); }
  @Test
  public void testConditionalAsArgument() { ig.get().doTest(); }
  @Test
  public void testConditionalInBinaryExpression() { ig.get().doTest(); }
  @Test
  public void testConditionalInIf() { ig.get().doTest(); }
  @Test
  public void testConditionalInIfBranch() { ig.get().doTest(); }
  @Test
  public void testConditionalInIfBranch2() { ig.get().doTest(); }
  @Test
  public void testInsideExprLambda() { ig.get().doTest(); }
  @Test
  public void testInsideExprLambdaWithParams() { ig.get().doTest(); }
  @Test
  public void testParentheses() { ig.get().doTest(); }
  @Test
  public void testNestedConditional() { ig.get().doTest(); }
  @Test
  public void testInsideSwitchExpression() { ig.get().doTest(); }
  @Test
  public void testInsideThrow() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
    CodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject());
    CommonCodeStyleSettings javaSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    javaSettings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;
    ig.get().doTest();
  }
  @Test
  public void testNestedConditionalOuter() { ig.get().doTest(); }

  @Test
  public void testSimpleOption() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
    final ConditionalExpressionInspection inspection = new ConditionalExpressionInspection();
    inspection.ignoreSimpleAssignmentsAndReturns = true;
    myFixture.enableInspections(inspection);
    ig.get().doTest();
  }
  
  @Test
  public void testSwitchExpressionInside() { ig.get().doTest(); }
  @Test
  public void testNullType() { ig.get().assertQuickfixNotAvailable(); }
  @Test
  public void testNullType2() { ig.get().assertQuickfixNotAvailable(); }
  @Test
  public void testRemoveParentheses() { ig.get().doTest(); }

}