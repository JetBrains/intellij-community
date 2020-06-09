// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull

@CompileStatic
class NormalSwitchCompletionTest extends NormalCompletionTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_14
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
}