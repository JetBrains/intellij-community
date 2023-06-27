// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.fix;

import com.intellij.codeInspection.PsiUpdateModCommandAction;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

public class RedundantInstanceofFix extends PsiUpdateModCommandAction<PsiElement> {
  public RedundantInstanceofFix(@NotNull PsiElement element) {
    super(element);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return JavaAnalysisBundle.message("inspection.data.flow.redundant.instanceof.quickfix");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PsiElement psiElement = element;
    CommentTracker ct = new CommentTracker();
    JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(context.project());
    if (psiElement instanceof PsiMethodReferenceExpression) {
      String replacement = CommonClassNames.JAVA_UTIL_OBJECTS + "::nonNull";
      javaCodeStyleManager.shortenClassReferences(ct.replaceAndRestoreComments(psiElement, replacement));
      return;
    }
    String nonNullExpression = null;
    if (psiElement instanceof PsiInstanceOfExpression instanceOf) {
      nonNullExpression = ct.text(instanceOf.getOperand());
    }
    else if (psiElement instanceof PsiMethodCallExpression call) {
      PsiExpression arg = ArrayUtil.getFirstElement(call.getArgumentList().getExpressions());
      if (arg == null) return;
      nonNullExpression = ct.text(arg);
    }
    if (nonNullExpression == null) return;
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(psiElement.getParent());
    String replacement;
    if (parent instanceof PsiExpression expression && BoolUtils.isNegation(expression)) {
      replacement = nonNullExpression + "==null";
      psiElement = parent;
    } else {
      replacement = nonNullExpression + "!=null";
    }
    javaCodeStyleManager.shortenClassReferences(ct.replaceAndRestoreComments(psiElement, replacement));
  }
}
