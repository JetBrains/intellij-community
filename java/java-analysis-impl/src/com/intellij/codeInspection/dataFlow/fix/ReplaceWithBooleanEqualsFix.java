// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.fix;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

public class ReplaceWithBooleanEqualsFix implements LocalQuickFix {
  private final String myNewExprText;
  private final boolean myFalseIsAcceptable;
  private final SmartPsiElementPointer<PsiExpression> myExprToReplacePointer;

  public ReplaceWithBooleanEqualsFix(@NotNull PsiExpression exprToReplace) {
    myNewExprText = exprToReplace.getText();
    PsiPrefixExpression parent = ObjectUtils.tryCast(exprToReplace.getParent(), PsiPrefixExpression.class);
    myFalseIsAcceptable = parent != null && parent.getOperationTokenType() == JavaTokenType.EXCL;
    myExprToReplacePointer = SmartPointerManager.getInstance(exprToReplace.getProject())
      .createSmartPsiElementPointer(myFalseIsAcceptable ? parent : exprToReplace);
  }

  @Override
  public @NotNull String getName() {
    return CommonQuickFixBundle.message("fix.replace.with.x", createNewExprText());
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("replace.with.boolean.equals");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiExpression exprToReplace = myExprToReplacePointer.getElement();
    if (exprToReplace == null) return;
    PsiExpression newExpr = JavaPsiFacade.getElementFactory(project).createExpressionFromText(createNewExprText(), exprToReplace);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(exprToReplace.replace(newExpr));
  }

  @NotNull
  private String createNewExprText() {
    return "Boolean." + (myFalseIsAcceptable ? "FALSE" : "TRUE") + ".equals(" + myNewExprText + ")";
  }
}
