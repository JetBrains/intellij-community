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
        PsiClass containingClass = PsiTreeUtil.getParentOfType(call, PsiClass.class);
        if (containingClass == null) return;
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
          if (!capturesThis(lambdaBody, containingClass)) {
          }
        } else if (runnableExpr instanceof PsiNewExpression) {
          PsiNewExpression newExpression = (PsiNewExpression)runnableExpr;
          if (newExpression.getAnonymousClass() == null) {
            PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
            if(classReference == null) return;
            PsiClass aClass = tryCast(classReference.resolve(), PsiClass.class);
            if(aClass == null) return;
            if(!aClass.hasModifier(JvmModifier.STATIC)) return;
          }
        }
        holder.registerProblem(runnableExpr, "Cleanable captures 'this' reference");
      }
    };
  }

  private static boolean capturesThis(@NotNull PsiElement lambdaBody, @NotNull PsiClass containingClass) {
    return StreamEx.ofTree(lambdaBody, el -> StreamEx.of(lambdaBody.getChildren()))
      .noneMatch(element -> {
        if (element instanceof PsiThisExpression) {
          return true;
        }
        else if (element instanceof PsiMethodCallExpression) {
          PsiMethod method = tryCast(((PsiMethodCallExpression)element).getMethodExpression().resolve(), PsiMethod.class);
          return method == null || method.hasModifierProperty(PsiModifier.STATIC);
        }
        else if (element instanceof PsiReferenceExpression) {
          PsiField field = tryCast(((PsiReferenceExpression)element).resolve(), PsiField.class);
          if (field == null) return false;
          PsiClass fieldContainingClass = field.getContainingClass();
          if (fieldContainingClass == null) return false;
          return InheritanceUtil.isInheritorOrSelf(containingClass, fieldContainingClass, true);
        }
        return false;
      });
  }

}
