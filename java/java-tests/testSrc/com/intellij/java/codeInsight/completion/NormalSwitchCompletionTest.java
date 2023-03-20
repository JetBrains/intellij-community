// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.NeedsIndex;
import org.jetbrains.annotations.NotNull;

public class NormalSwitchCompletionTest extends NormalCompletionTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_17;
  }

  public void testDefaultInRuleSwitch() { doTest(); }

  public void testLabelInRuleSwitch() { doTest(); }

  public void testSecondLabelInOldSwitch() { doTest(); }

  public void testSecondLabelInRuleSwitch() { doTest(); }

  public void testSwitchExpressionStart() { doTest(); }

  public void testYieldInSwitchExpression() { doTest(); }

  public void testInsideYieldInSwitchExpression() { doTest(); }

  public void testInsideRuleInSwitchExpression() { doTest(); }

  public void testBreakDeepInsideSwitchExpression() { doTest(); }

  @NeedsIndex.Full
  public void testCompletePatternVariableInSwitchExpr() { doTest(); }

  @NeedsIndex.Full
  public void testCompletePatternVariableInSwitchStmt() { doTest(); }

  public void testCompleteReturnInSwitch() { doTest(); }

  @NeedsIndex.Full
  public void testCompleteConstantInSwitchExpr() { doTest("\n"); }

  @NeedsIndex.Full
  public void testCompleteConstantInSwitchStmt() { doTest("\n"); }

  public void testCompleteNullInSwitchStmt() { doTest(); }

  public void testCompleteNullInSwitchExpr() { doTest(); }

  public void testCompleteDefaultInSwitchStmt() { doTest(); }

  public void testCompleteDefaultInSwitchExpr() { doTest(); }

  public void testCompletePatternVariableSwitchStmt() { doTest(); }

  public void testCompletePatternVariableSwitchExpr() { doTest(); }

  public void testCompleteSealedLabelSwitch() { doTest(); }

  public void testCompleteReturnInSwitchRule() { doTest(); }

  public void testCompleteReturnInSwitchRule2() { doTest(); }

  public void testCompleteThrowInSwitchRule() { doTest(); }

  @NeedsIndex.Full(reason = "Other options do not appear if there's no index. As a result, '\n' goes right into the editor")
  public void testCompleteIfInSwitchRule() { doTest("\n"); }

  public void testCompleteSwitchObjectSelectorPostfix() { doTestPostfixCompletion(); }

  public void testCompleteSwitchSealedSelectorPostfix() { doTestPostfixCompletion(); }

  @NeedsIndex.ForStandardLibrary
  public void testCompleteReferencesInExpressionSwitch() {
    doTest();
  }

  @NeedsIndex.ForStandardLibrary
  public void testCompleteFinalInsideSwitch() { doTest("\n"); }

  public void testCompleteYieldInsideSwitch() { doTest(); }

  private void doTestPostfixCompletion() {
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.getTestRootDisposable());
    configure();
    myFixture.type('\t');
    checkResult();
  }
}
