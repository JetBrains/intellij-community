// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.refactoring.suggested.PerformSuggestedRefactoringKt;
import com.intellij.refactoring.suggested.SuggestedRefactoringExecution;
import com.siyeh.ig.style.MissortedModifiersInspection;
import com.siyeh.ig.style.UnqualifiedFieldAccessInspection;
import kotlin.jvm.functions.Function1;

public class CreateConstructorParameterFromFieldTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTools(new UnusedDeclarationInspection(), new MissortedModifiersInspection(), new UnqualifiedFieldAccessInspection());
    final JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(getProject());
    if (getTestName(false).contains("SameParameter")) {
      settings.PREFER_LONGER_NAMES = false;
      settings.GENERATE_FINAL_PARAMETERS = true;
    }
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createConstructorParameterFromField";
  }

  @Override
  public void runSingle() throws Throwable {
    Function1<Integer, SuggestedRefactoringExecution.NewParameterValue> defaultValue =
      PerformSuggestedRefactoringKt.get_suggestedChangeSignatureNewParameterValuesForTests();
    try {
      PerformSuggestedRefactoringKt.set_suggestedChangeSignatureNewParameterValuesForTests(
        idx -> new SuggestedRefactoringExecution.NewParameterValue.Expression(
          JavaPsiFacade.getElementFactory(getProject()).createExpressionFromText(String.valueOf(idx), null)));
      super.runSingle();
    }
    finally {
      PerformSuggestedRefactoringKt.set_suggestedChangeSignatureNewParameterValuesForTests(defaultValue);
    }
  }
}
