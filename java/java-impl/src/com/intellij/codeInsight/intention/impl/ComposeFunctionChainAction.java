// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class ComposeFunctionChainAction extends PsiElementBaseIntentionAction {
  private static final Logger LOG = Logger.getInstance(ComposeFunctionChainAction.class.getName());

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull final PsiElement element) {
    PsiMethodCallExpression call =
      PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class, false, PsiStatement.class, PsiLambdaExpression.class);
    if(call == null) return false;
    if(!"apply".equals(call.getMethodExpression().getReferenceName())) return false;
    PsiMethod method = call.resolveMethod();
    if(method == null) return false;
    PsiClass aClass = method.getContainingClass();
    if(aClass == null) return false;
    if(!CommonClassNames.JAVA_UTIL_FUNCTION_FUNCTION.equals(aClass.getQualifiedName()) &&
       !CommonClassNames.JAVA_UTIL_FUNCTION_BI_FUNCTION.equals(aClass.getQualifiedName())) {
      return false;
    }
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(call.getParent());
    if (!(parent instanceof PsiExpressionList) || ((PsiExpressionList)parent).getExpressions().length != 1) return false;

    PsiElement gParent = parent.getParent();
    if (!(gParent instanceof PsiMethodCallExpression)) return false;

    PsiMethod outerMethod = ((PsiMethodCallExpression)gParent).resolveMethod();
    if (outerMethod == null ||
        !Arrays.stream(outerMethod.getThrowsList().getReferencedTypes()).allMatch(ExceptionUtil::isUncheckedException)) {
      return false;
    }
    return true;
  }

  @NotNull
  @Override
  public String getText() {
    return CodeInsightBundle.message("intention.compose.function.text");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.compose.function.family");
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    PsiMethodCallExpression call =
      PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class, false, PsiStatement.class, PsiLambdaExpression.class);
    if(call == null) return;

    PsiElement outer = call.getParent().getParent();
    if(!(outer instanceof PsiMethodCallExpression)) return;
    PsiMethodCallExpression outerCall = (PsiMethodCallExpression)outer;
    PsiMethod outerMethod = outerCall.resolveMethod();
    if(outerMethod == null) return;
    PsiClass outerClass = outerMethod.getContainingClass();
    if(outerClass == null) return;
    String outerClassName = outerClass.getQualifiedName();

    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    PsiExpression outerQualifier = outerCall.getMethodExpression().getQualifierExpression();
    CommentTracker ct = new CommentTracker();

    String reference;
    if(outerMethod.getName().equals("apply") && CommonClassNames.JAVA_UTIL_FUNCTION_FUNCTION.equals(outerClassName)) {
      reference = outerQualifier == null ? "this" : ct.text(outerQualifier);
    } else if(outerMethod.hasModifierProperty(PsiModifier.STATIC)) {
      reference = outerClassName + "::" + outerMethod.getName();
    } else {
      reference = outerQualifier == null ? "this" : ct.text(outerQualifier)+"::"+outerMethod.getName();
    }
    String resultQualifier = qualifier != null ? ct.text(qualifier) + "." : "";

    String replacement = resultQualifier + "andThen(" + reference + ").apply" + ct.text(call.getArgumentList());

    PsiElement result = ct.replaceAndRestoreComments(outer, replacement);
    result = CodeStyleManager.getInstance(project).reformat(result);
    PsiElement applyElement = ((PsiMethodCallExpression)result).getMethodExpression().getReferenceNameElement();
    if(applyElement != null) {
      editor.getCaretModel().moveToOffset(applyElement.getTextOffset() + applyElement.getTextLength());
    }
  }

}
