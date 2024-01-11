// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.NeedsIndex;
import org.jetbrains.annotations.NotNull;

public class CastVarPostfixTemplateTest extends PostfixTemplateTestCase {

  @NotNull
  @Override
  protected String getSuffix() {
    return "castvar";
  }

  @NeedsIndex.SmartMode(reason = "DFA is necessary for smart-cast")
  public void testSingleExpression() {
    doTest();
  }

  public void testAssigned() {
    doTest();
  }

  @NeedsIndex.SmartMode(reason = "DFA is necessary for smart-cast")
  public void testFinalSingleExpression() {
    JavaCodeStyleSettings customSettings = JavaCodeStyleSettings.getInstance(getProject());
    customSettings.GENERATE_FINAL_LOCALS = true;

    doTest();
  }
}
