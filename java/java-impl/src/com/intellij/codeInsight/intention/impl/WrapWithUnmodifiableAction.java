// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.CommonClassNames.*;
import static com.intellij.util.ObjectUtils.tryCast;

/**
 * @author Pavel.Dolgov
 */
public class WrapWithUnmodifiableAction extends PsiElementBaseIntentionAction {
  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    PsiExpression expression = getParentExpression(element);
    if (expression != null) {
      PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(expression.getType());
      if (InheritanceUtil.isInheritor(psiClass, JAVA_UTIL_LIST)) {
        wrapWith(expression, "unmodifiableList");
      }
      else if (InheritanceUtil.isInheritor(psiClass, "java.util.SortedSet")) {
        wrapWith(expression, "unmodifiableSortedSet");
      }
      else if (InheritanceUtil.isInheritor(psiClass, JAVA_UTIL_SET)) {
        wrapWith(expression, "unmodifiableSet");
      }
      else if (InheritanceUtil.isInheritor(psiClass, "java.util.SortedMap")) {
        wrapWith(expression, "unmodifiableSortedMap");
      }
      else if (InheritanceUtil.isInheritor(psiClass, JAVA_UTIL_MAP)) {
        wrapWith(expression, "unmodifiableMap");
      }
    }
  }

  private static PsiExpression getParentExpression(@NotNull PsiElement element) {
    PsiExpression expression = PsiTreeUtil.getNonStrictParentOfType(element, PsiExpression.class);
    if (expression != null) {
      PsiMethodCallExpression methodCall = tryCast(expression.getParent(), PsiMethodCallExpression.class);
      if (methodCall != null && methodCall.getMethodExpression() == expression) {
        return methodCall;
      }
    }
    return expression;
  }

  private static void wrapWith(PsiExpression expression, String methodName) {
    CommentTracker tracker = new CommentTracker();
    String text = JAVA_UTIL_COLLECTIONS + '.' + methodName + '(' + tracker.text(expression) + ')';
    PsiElement result = tracker.replaceAndRestoreComments(expression, text);
    JavaCodeStyleManager.getInstance(result.getProject()).shortenClassReferences(result);
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    PsiExpression expression = getParentExpression(element);
    if (expression != null) {
      if (isUnmodifiable(expression)) {
        return false;
      }
      PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(expression.getType());
      if (psiClass != null) {
        if (InheritanceUtil.isInheritor(psiClass, JAVA_UTIL_LIST)) {
          setText(CodeInsightBundle.message("intention.wrap.with.unmodifiable.list"));
          return true;
        }
        if (InheritanceUtil.isInheritor(psiClass, JAVA_UTIL_SET)) {
          setText(CodeInsightBundle.message("intention.wrap.with.unmodifiable.set"));
          return true;
        }
        if (InheritanceUtil.isInheritor(psiClass, JAVA_UTIL_MAP)) {
          setText(CodeInsightBundle.message("intention.wrap.with.unmodifiable.map"));
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isUnmodifiable(PsiExpression expression) {
    PsiMethodCallExpression methodCall = tryCast(expression, PsiMethodCallExpression.class);
    if (isUnmodifiableCall(methodCall)) {
      return true;
    }

    PsiExpressionList expressionList = tryCast(PsiUtil.skipParenthesizedExprUp(expression.getParent()), PsiExpressionList.class);
    if (expressionList != null && expressionList.getExpressionCount() == 1) {
      methodCall = tryCast(expressionList.getParent(), PsiMethodCallExpression.class);
      if (isUnmodifiableCall(methodCall)) {
        return true;
      }
    }

    return false;
  }

  private static boolean isUnmodifiableCall(@Nullable PsiMethodCallExpression methodCall) {
    if (methodCall != null) {
      String name = methodCall.getMethodExpression().getReferenceName();
      if (name != null && name.startsWith("unmodifiable")) {
        PsiMethod method = methodCall.resolveMethod();
        if (method != null && method.hasModifierProperty(PsiModifier.STATIC)) {
          PsiClass psiClass = method.getContainingClass();
          if (psiClass != null && JAVA_UTIL_COLLECTIONS.equals(psiClass.getQualifiedName())) {
            return true;
          }
        }
      }
    }
    return false;
  }


  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.wrap.with.unmodifiable");
  }
}
