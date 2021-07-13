// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion

import com.intellij.testFramework.LightProjectDescriptor
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

  void testCompletePatternVariableInSwitchExpr() { doTest() }
  void testCompletePatternVariableInSwitchStmt() { doTest() }

  void testCompleteConstantInSwitchExpr() { doTest() }
  void testCompleteConstantInSwitchStmt() { doTest() }

  void testCompleteNullInSwitchStmt() { doTest() }
  void testCompleteNullInSwitchExpr() { doTest() }

  void testCompletePatternVariableSwitchStmt() { doTest() }
  void testCompletePatternVariableSwitchExpr() { doTest() }
}