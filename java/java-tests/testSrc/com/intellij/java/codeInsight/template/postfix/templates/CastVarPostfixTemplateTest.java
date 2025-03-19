// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.refactoring.JavaRefactoringSettings;
import org.jetbrains.annotations.NotNull;

public class CastVarPostfixTemplateTest extends PostfixTemplateTestCase {

  @NotNull
  @Override
  protected String getSuffix() {
    return "castvar";
  }

  public void testSingleExpression() {
    doTest();
  }

  public void testAssigned() {
    doTest();
  }
  
  public void testTypeAnnotations() {
    doTest();
  }

  public void testFinalSingleExpression() {
    JavaCodeStyleSettings customSettings = JavaCodeStyleSettings.getInstance(getProject());
    customSettings.GENERATE_FINAL_LOCALS = true;

    doTest();
  }

  public void testVarSingleExpression() {
    JavaRefactoringSettings instance = JavaRefactoringSettings.getInstance();
    instance.INTRODUCE_LOCAL_CREATE_VAR_TYPE = true;
    try {
      doTest();
    }
    finally {
      instance.INTRODUCE_LOCAL_CREATE_VAR_TYPE = false;
    }
  }
}
