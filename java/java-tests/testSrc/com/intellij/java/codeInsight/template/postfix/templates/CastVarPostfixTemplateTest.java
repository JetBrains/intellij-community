// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.application.options.CodeStyle;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

public class CastVarPostfixTemplateTest extends PostfixTemplateTestCase {

  @NotNull
  @Override
  protected String getSuffix() {
    return "castvar";
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      CodeStyle.dropTemporarySettings(myFixture.getProject());
    }
    finally {
      super.tearDown();
    }
  }

  public void testSingleExpression() {
    doTest();
  }

  public void testFinalSingleExpression() {
    CodeStyleSettings codeStyleSettings = CodeStyle.getSettings(myFixture.getProject());
    CodeStyleSettings clone = codeStyleSettings.clone();
    CodeStyle.setTemporarySettings(myFixture.getProject(), clone);

    JavaCodeStyleSettings customSettings = clone.getCustomSettings(JavaCodeStyleSettings.class);
    customSettings.GENERATE_FINAL_LOCALS = true;

    doTest();
  }
}
