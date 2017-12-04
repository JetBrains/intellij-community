// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.lang.jvm.JvmModifier;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
        PsiExpression trackedObject = ExpressionUtils.resolveExpression(expressions[0]);
        PsiExpression runnableExpr = ExpressionUtils.resolveExpression(expressions[1]);
        if (trackedObject == null || runnableExpr == null) return;

        final String referenceName;
        if (trackedObject instanceof PsiThisExpression) {
          PsiClassType classType = tryCast(trackedObject.getType(), PsiClassType.class);
          if (classType == null) return;
          PsiClass trackedClass = classType.resolve();
          if (trackedClass == null) return;
          if (!capturesThis(runnableExpr, trackedClass)) return;
          referenceName = "this";
        }
        else if (trackedObject instanceof PsiReferenceExpression) {
          PsiVariable variable = tryCast(((PsiReferenceExpression)trackedObject).resolve(), PsiVariable.class);
          if (variable == null) return;
          if (variable instanceof PsiField) return;
          if (!VariableAccessUtils.variableIsUsed(variable, runnableExpr)) return;
          String variableName = variable.getName();
          if (variableName == null) return;
          referenceName = variableName;
        }
        else {
          return;
        }
        holder.registerProblem(runnableExpr, InspectionsBundle.message("inspection.cleaner.capturing.this", referenceName));
      }

      private boolean capturesThis(PsiExpression runnableExpr, PsiClass trackedClass) {
        if (runnableExpr instanceof PsiMethodReferenceExpression) {
          PsiMethodReferenceExpression methodReference = (PsiMethodReferenceExpression)runnableExpr;
          if (PsiMethodReferenceUtil.isStaticallyReferenced(methodReference)) return false;

          PsiThisExpression thisExpression = tryCast(methodReference.getQualifier(), PsiThisExpression.class);
          if (thisExpression == null) return false;
          PsiClass thisClass = resolveThis(thisExpression);
          if (thisClass != trackedClass) return false;
        }
        else if (runnableExpr instanceof PsiLambdaExpression) {
          PsiLambdaExpression lambda = (PsiLambdaExpression)runnableExpr;
          if (lambda.getParameterList().getParametersCount() != 0) return false;
          PsiElement lambdaBody = lambda.getBody();
          if (lambdaBody == null) return false;
          if (!lambdaCapturesThis(lambdaBody, trackedClass)) return false;
        }
        else if (runnableExpr instanceof PsiNewExpression) {
          PsiNewExpression newExpression = (PsiNewExpression)runnableExpr;
          if (newExpression.getAnonymousClass() == null) {
            PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
            if (classReference == null) return false;
            PsiClass aClass = tryCast(classReference.resolve(), PsiClass.class);
            if (aClass == null) return false;
            if (aClass.getContainingClass() != trackedClass) return false;
            if (aClass.hasModifier(JvmModifier.STATIC)) return false;
          }
        }
        return true;
      }
    };
  }

  @Contract("null -> null")
  @Nullable
  static PsiClass resolveThis(@Nullable PsiThisExpression thisExpression) {
    if (thisExpression == null) return null;
    PsiClassType classType = tryCast(thisExpression.getType(), PsiClassType.class);
    if (classType == null) return null;
    return classType.resolve();
  }

  private static boolean lambdaCapturesThis(@NotNull PsiElement lambdaBody, @NotNull PsiClass containingClass) {
    return StreamEx.ofTree(lambdaBody, el -> StreamEx.of(el.getChildren()))
      .anyMatch(element -> isThisCapturingElement(containingClass, element));
  }

  private static boolean isThisCapturingElement(@NotNull PsiClass containingClass, PsiElement element) {
    if (element instanceof PsiThisExpression) {
      return resolveThis((PsiThisExpression)element) == containingClass;
    }
    else if (element instanceof PsiReferenceExpression) {
      PsiMember member = tryCast(((PsiReferenceExpression)element).resolve(), PsiMember.class);
      if (member == null) return false;
      PsiClass memberContainingClass = member.getContainingClass();
      if (memberContainingClass == null) return false;
      if (!InheritanceUtil.isInheritorOrSelf(containingClass, memberContainingClass, true) &&
          !isInnerClassOf(containingClass, memberContainingClass)) {
        return false;
      }
      return !member.hasModifierProperty(PsiModifier.STATIC);
    }
    return false;
  }

  @Contract("_, null -> false")
  private static boolean isInnerClassOf(@Nullable PsiClass inner, @Nullable PsiClass outer) {
    if (outer == null) return false;
    PsiClass current = inner;
    while (current != null) {
      if (current == outer) return true;
      current = current.getContainingClass();
    }
    return false;
  }
}
