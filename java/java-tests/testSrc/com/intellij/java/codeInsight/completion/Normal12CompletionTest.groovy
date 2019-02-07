// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion


import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull

@CompileStatic
class Normal12CompletionTest extends NormalCompletionTestCase {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_12
  }

  void testDefaultInRuleSwitch() throws Throwable { doTest() }
  void testLabelInRuleSwitch() throws Throwable { doTest() }
  void testSecondLabelInOldSwitch() throws Throwable { doTest() }
  void testSecondLabelInRuleSwitch() throws Throwable { doTest() }
  void testSwitchExpressionStart() throws Throwable { doTest() }
  void testBreakInSwitchExpression() throws Throwable { doTest() }
  void testInsideBreakInSwitchExpression() throws Throwable { doTest() }
  void testInsideRuleInSwitchExpression() throws Throwable { doTest() }
}
