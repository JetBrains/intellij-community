// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.quickFix.ActionHint;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.unneededThrows.RedundantThrowsDeclarationInspection;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class RedundantThrowsDeclarationGlobalFixTest extends LightQuickFixParameterizedTestCase {

  @Override
  protected void doAction(@NotNull ActionHint actionHint, @NotNull String testFullPath, @NotNull String testName) {
    final LocalQuickFix fix = (LocalQuickFix)new RedundantThrowsDeclarationInspection().getQuickFix(actionHint.getExpectedText());

    final int offset = getEditor().getCaretModel().getOffset();
    final PsiElement psiElement = getFile().findElementAt(offset);
    assert psiElement != null;

    final ProblemDescriptor descriptor = InspectionManager.getInstance(getProject())
      .createProblemDescriptor(psiElement.getParent(),
                               "",
                               fix,
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                               true);

    fix.applyFix(getProject(), descriptor);

    final String expectedFilePath = getBasePath() + "/after" + testName;
    checkResultByFile("In file: " + expectedFilePath, expectedFilePath, false);
  }

  @Override
  protected @NotNull String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  @Override
  protected @NonNls String getBasePath() {
    return "/redundantThrowsGlobalFix";
  }
}
