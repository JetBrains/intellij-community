// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion

import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.NeedsIndex
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull

@CompileStatic
class NormalSwitchCompletionTest extends NormalCompletionTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_17
  }

  void testDefaultInRuleSwitch() { doTest() }
  void testLabelInRuleSwitch() { doTest() }
  void testSecondLabelInOldSwitch() { doTest() }
  void testSecondLabelInRuleSwitch() { doTest() }
  void testSwitchExpressionStart() { doTest() }
  void testYieldInSwitchExpression() { doTest() }
  void testInsideYieldInSwitchExpression() { doTest() }
  void testInsideRuleInSwitchExpression() { doTest() }
  void testBreakDeepInsideSwitchExpression() { doTest() }

  @NeedsIndex.Full
  void testCompletePatternVariableInSwitchExpr() { doTest() }
  @NeedsIndex.Full
  void testCompletePatternVariableInSwitchStmt() { doTest() }

  void testCompleteReturnInSwitch() { doTest() }

  @NeedsIndex.Full
  void testCompleteConstantInSwitchExpr() { doTest() }
  @NeedsIndex.Full
  void testCompleteConstantInSwitchStmt() { doTest() }

  void testCompleteNullInSwitchStmt() { doTest() }
  void testCompleteNullInSwitchExpr() { doTest() }

  void testCompleteDefaultInSwitchStmt() { doTest() }
  void testCompleteDefaultInSwitchExpr() { doTest() }

  void testCompletePatternVariableSwitchStmt() { doTest() }
  void testCompletePatternVariableSwitchExpr() { doTest() }

  void testCompleteSealedLabelSwitch() { doTest() }

  void testCompleteSwitchObjectSelectorPostfix() { doTestPostfixCompletion() }
  void testCompleteSwitchSealedSelectorPostfix() { doTestPostfixCompletion() }

  private void doTestPostfixCompletion() {
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.testRootDisposable)
    configure()
    myFixture.type('\t' as char)
    checkResult()
  }
}