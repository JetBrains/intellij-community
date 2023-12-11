// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public final class MergeFilterChainAction extends PsiUpdateModCommandAction<PsiIdentifier> {
  private static final Logger LOG = Logger.getInstance(MergeFilterChainAction.class.getName());
  
  public MergeFilterChainAction() {
    super(PsiIdentifier.class);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiIdentifier identifier) {
    if (!(identifier.getParent() instanceof PsiReferenceExpression ref)) return null;
    if (!(ref.getParent() instanceof PsiMethodCallExpression call)) return null;
    if (!isFilterCall(call) || getFilterToMerge(call) == null) return null;
    return Presentation.of(JavaBundle.message("intention.merge.filter.text"));
  }

  @Nullable
  private static PsiMethodCallExpression getFilterToMerge(PsiMethodCallExpression methodCallExpression) {
    final PsiMethodCallExpression prevCall = MethodCallUtils.getQualifierMethodCall(methodCallExpression);
    if (prevCall != null && isFilterCall(prevCall)) {
      return prevCall;
    }

    final PsiMethodCallExpression nextCall = ExpressionUtils.getCallForQualifier(methodCallExpression);
    if (nextCall != null && isFilterCall(nextCall)) {
      return nextCall;
    }
    return null;
  }

  public static boolean isFilterCall(PsiMethodCallExpression methodCallExpression) {
    String name = methodCallExpression.getMethodExpression().getReferenceName();
    if (!"filter".equals(name) && !"anyMatch".equals(name)) return false;

    final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
    final PsiExpression[] expressions = argumentList.getExpressions();
    if (expressions.length != 1) return false;
    if (!StreamRefactoringUtil.isRefactoringCandidate(PsiUtil.skipParenthesizedExprDown(expressions[0]), true)) {
      return false;
    }

    final PsiMethod method = methodCallExpression.resolveMethod();
    if (method == null) return false;
    final PsiClass containingClass = method.getContainingClass();
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    return parameters.length == 1 &&
           InheritanceUtil.isInheritor(containingClass, false, CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return JavaBundle.message("intention.merge.filter.family");
  }

  @Nullable
  private static PsiLambdaExpression getLambda(PsiMethodCallExpression call) {
    PsiExpression[] expressions = call.getArgumentList().getExpressions();
    if(expressions.length != 1) return null;
    PsiExpression expression = expressions[0];
    if(expression instanceof PsiLambdaExpression) return (PsiLambdaExpression)expression;
    if (expression instanceof PsiMethodReferenceExpression) {
      return LambdaRefactoringUtil
        .convertMethodReferenceToLambda((PsiMethodReferenceExpression)expression, false, true);
    }
    return null;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiIdentifier identifier, @NotNull ModPsiUpdater updater) {
    final PsiMethodCallExpression filterCall = PsiTreeUtil.getParentOfType(identifier, PsiMethodCallExpression.class);
    LOG.assertTrue(filterCall != null);

    final PsiMethodCallExpression filterToMerge = getFilterToMerge(filterCall);
    LOG.assertTrue(filterToMerge != null);

    final PsiMethodCallExpression callToStay = filterCall.getTextLength() < filterToMerge.getTextLength() ? filterCall : filterToMerge;
    final PsiMethodCallExpression callToEliminate = callToStay == filterCall ? filterToMerge : filterCall;

    String resultingOperation = callToEliminate.getMethodExpression().getReferenceName();
    LOG.assertTrue(resultingOperation != null);

    final PsiLambdaExpression targetLambda = getLambda(callToStay);
    LOG.assertTrue(targetLambda != null, callToStay);
    final PsiParameter[] parameters = targetLambda.getParameterList().getParameters();
    final String name = parameters.length > 0 ? parameters[0].getName() : null;

    final PsiLambdaExpression sourceLambda = getLambda(callToEliminate);
    LOG.assertTrue(sourceLambda != null, callToEliminate);
    if (name != null) {
      final PsiParameter[] sourceLambdaParams = sourceLambda.getParameterList().getParameters();
      if (sourceLambdaParams.length > 0 && !name.equals(sourceLambdaParams[0].getName())) {
        for (PsiReference reference : ReferencesSearch.search(sourceLambdaParams[0]).findAll()) {
          final PsiElement referenceElement = reference.getElement();
          if (referenceElement instanceof PsiReferenceExpression) {
            ExpressionUtils.bindReferenceTo((PsiReferenceExpression)referenceElement, name);
          }
        }
      }
    }

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.project());
    PsiElement nameElement = callToStay.getMethodExpression().getReferenceNameElement();
    LOG.assertTrue(nameElement != null);
    if (!resultingOperation.equals(nameElement.getText())) {
      nameElement.replace(factory.createIdentifier(resultingOperation));
    }

    PsiExpression targetBody = LambdaUtil.extractSingleExpressionFromBody(targetLambda.getBody());
    LOG.assertTrue(targetBody != null);
    final PsiExpression sourceLambdaBody = LambdaUtil.extractSingleExpressionFromBody(sourceLambda.getBody());
    LOG.assertTrue(sourceLambdaBody != null);

    String newFilter = ParenthesesUtils.getText(targetBody, ParenthesesUtils.OR_PRECEDENCE) + " && " +
                       ParenthesesUtils.getText(sourceLambdaBody, ParenthesesUtils.OR_PRECEDENCE);
    final PsiExpression compoundExpression = factory.createExpressionFromText(newFilter, sourceLambda);
    targetBody = (PsiExpression)targetBody.replace(compoundExpression);
    CodeStyleManager.getInstance(context.project()).reformat(targetBody);

    final PsiExpression qualifierExpression = callToEliminate.getMethodExpression().getQualifierExpression();
    LOG.assertTrue(qualifierExpression != null, callToEliminate);
    final Collection<PsiComment> comments = PsiTreeUtil.findChildrenOfType(callToEliminate, PsiComment.class);
    for (PsiComment comment : comments) {
      final TextRange commentRange = comment.getTextRange();
      if (!sourceLambdaBody.getTextRange().contains(commentRange) &&
          !qualifierExpression.getTextRange().contains(commentRange)) {
        targetBody.add(comment);
      }
    }
    callToEliminate.replace(qualifierExpression);
  }

}
