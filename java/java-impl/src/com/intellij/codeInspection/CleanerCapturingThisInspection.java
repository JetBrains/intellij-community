// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.lang.jvm.JvmModifier;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.tryCast;

public class CleanerCapturingThisInspection extends AbstractBaseJavaLocalInspectionTool {

  private static final CallMatcher CLEANER_REGISTER = CallMatcher.instanceCall(
    "java.lang.ref.Cleaner", "register"
  ).parameterCount(2);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel9OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        if (!CLEANER_REGISTER.test(call)) return;
        PsiExpression[] expressions = call.getArgumentList().getExpressions();
        if (!(expressions[0] instanceof PsiThisExpression)) return;
        PsiExpression runnableExpr = expressions[1];
        PsiClass callContainingClass = PsiTreeUtil.getParentOfType(call, PsiClass.class);
        if (callContainingClass == null) return;
        PsiElement referenceNameElement = call.getMethodExpression().getReferenceNameElement();
        if (referenceNameElement == null) return;
        if (runnableExpr instanceof PsiMethodReferenceExpression) {
          if (PsiMethodReferenceUtil.isStaticallyReferenced((PsiMethodReferenceExpression)runnableExpr)) return;
        }
        else if (runnableExpr instanceof PsiLambdaExpression) {
          PsiLambdaExpression lambda = (PsiLambdaExpression)runnableExpr;
          if (lambda.getParameterList().getParametersCount() != 0) return;
          PsiElement lambdaBody = lambda.getBody();
          if (lambdaBody == null) return;
          if (!capturesThis(lambdaBody, callContainingClass)) return;
        } else if (runnableExpr instanceof PsiNewExpression) {
          PsiNewExpression newExpression = (PsiNewExpression)runnableExpr;
          if (newExpression.getAnonymousClass() == null) {
            PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
            if(classReference == null) return;
            PsiClass aClass = tryCast(classReference.resolve(), PsiClass.class);
            if(aClass == null) return;
            if(aClass.getContainingClass() != callContainingClass) return;
            if(aClass.hasModifier(JvmModifier.STATIC)) return;
          }
        }
        holder.registerProblem(runnableExpr, InspectionsBundle.message("inspection.cleaner.capturing.this"));
      }
    };
  }

  private static boolean capturesThis(@NotNull PsiElement lambdaBody, @NotNull PsiClass containingClass) {
    return StreamEx.ofTree(lambdaBody, el -> StreamEx.of(el.getChildren()))
      .anyMatch(element -> isThisCapturingElement(containingClass, element));
  }

  private static boolean isThisCapturingElement(@NotNull PsiClass containingClass, PsiElement element) {
    if (element instanceof PsiThisExpression) {
      return true;
    }
    else if (element instanceof PsiReferenceExpression) {
      PsiMember member = tryCast(((PsiReferenceExpression)element).resolve(), PsiMember.class);
      if (member == null) return false;
      PsiClass memberContainingClass = member.getContainingClass();
      if (memberContainingClass == null) return false;
      if (!InheritanceUtil.isInheritorOrSelf(containingClass, memberContainingClass, true)) return false;
      return !member.hasModifierProperty(PsiModifier.STATIC);
    }
    return false;
  }
}
