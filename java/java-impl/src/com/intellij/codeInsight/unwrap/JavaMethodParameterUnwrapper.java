// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class JavaMethodParameterUnwrapper extends JavaUnwrapper {
  private static final Logger LOG = Logger.getInstance(JavaMethodParameterUnwrapper.class);

  public JavaMethodParameterUnwrapper() {
    super("");
  }

  private static PsiElement adjustElementToTheLeft(PsiElement element) {
    if (PsiUtil.isJavaToken(element, JavaTokenType.RPARENTH)) {
      PsiElement prevSibling = element.getPrevSibling();
      if (prevSibling != null) {
        return prevSibling;
      }
    }
    return element;
  }

  @Override
  public @NotNull String getDescription(@NotNull PsiElement e) {
    String text = adjustElementToTheLeft(e).getText();
    if (text.length() > 20) text = text.substring(0, 17) + "...";
    return CodeInsightBundle.message("unwrap.with.placeholder", text);
  }

  @Override
  public boolean isApplicableTo(@NotNull PsiElement e) {
    e = adjustElementToTheLeft(e);
    final PsiElement parent = e.getParent();
    if (e instanceof PsiExpression){
      if (parent instanceof PsiExpressionList) {
        return true;
      }
      if (e instanceof PsiReferenceExpression && parent instanceof PsiCallExpression) {
        final PsiExpressionList argumentList = ((PsiCall)parent).getArgumentList();
        if (argumentList != null && argumentList.getExpressionCount() == 1) {
          return true;
        }
      }
    } else if (e instanceof PsiJavaCodeReferenceElement) {
      if (parent instanceof PsiCall) {
        final PsiExpressionList argumentList = ((PsiCall)parent).getArgumentList();
        if (argumentList != null && argumentList.getExpressionCount() == 1) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public PsiElement collectAffectedElements(@NotNull PsiElement e, @NotNull List<? super PsiElement> toExtract) {
    e = adjustElementToTheLeft(e);
    super.collectAffectedElements(e, toExtract);
    return isTopLevelCall(e) ? e.getParent() : e.getParent().getParent();
  }

  private static boolean isTopLevelCall(PsiElement e) {
    if (e instanceof PsiReferenceExpression && e.getParent() instanceof PsiCallExpression) return true;
    return e instanceof PsiJavaCodeReferenceElement && !(e instanceof PsiExpression);
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    element = adjustElementToTheLeft(element);
    PsiElement parent = element.getParent();
    if (parent == null) return;
    PsiElement methodCall = isTopLevelCall(element) ? parent : parent.getParent();
    final PsiElement extractedElement = isTopLevelCall(element) ? getArg(element) : element;
    context.extractElement(extractedElement, methodCall);
    if (methodCall.getParent() instanceof PsiExpressionList) {
      context.delete(methodCall);
    }
    else {
      context.deleteExactly(methodCall);
    }
  }

  private static PsiExpression getArg(PsiElement element) {
    final PsiExpressionList argumentList = ((PsiCall)element.getParent()).getArgumentList();
    LOG.assertTrue(argumentList != null);
    final PsiExpression[] args = argumentList.getExpressions();
    LOG.assertTrue(args.length == 1);
    return args[0];
  }
}
