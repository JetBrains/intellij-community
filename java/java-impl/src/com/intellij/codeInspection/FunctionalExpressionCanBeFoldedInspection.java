// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.types.DfReferenceType;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.LambdaUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiFunctionalExpression;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiSuperExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public final class FunctionalExpressionCanBeFoldedInspection extends AbstractBaseJavaLocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression expression) {
        final PsiExpression qualifierExpression = expression.getQualifierExpression();
        final PsiElement referenceNameElement = expression.getReferenceNameElement();
        doCheckCall(expression, () -> expression.resolve(), qualifierExpression, referenceNameElement,
                    InspectionGadgetsBundle.message("replace.method.ref.with.qualifier.problem.method"));
      }

      @Override
      public void visitLambdaExpression(@NotNull PsiLambdaExpression lambdaExpression) {
        PsiElement body = lambdaExpression.getBody();
        PsiExpression asMethodReference = LambdaCanBeMethodReferenceInspection
          .canBeMethodReferenceProblem(body, lambdaExpression.getParameterList().getParameters(), lambdaExpression.getFunctionalInterfaceType(), null);
        if (asMethodReference instanceof PsiMethodCallExpression) {
          PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)asMethodReference).getMethodExpression();
          PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
          doCheckCall(lambdaExpression, () -> ((PsiMethodCallExpression)asMethodReference).resolveMethod(), qualifierExpression, asMethodReference,
                    InspectionGadgetsBundle.message("replace.method.ref.with.qualifier.problem.lambda"));
        }
      }

      private void doCheckCall(PsiFunctionalExpression expression,
                               Supplier<? extends PsiElement> resolver,
                               PsiExpression qualifierExpression,
                               PsiElement referenceNameElement,
                               final @InspectionMessage String errorMessage) {
        if (qualifierExpression != null && referenceNameElement != null && !(qualifierExpression instanceof PsiSuperExpression)) {
          final PsiType qualifierType = qualifierExpression.getType();
          if (qualifierType != null) {
            //don't get the ground type as check is required over the expected type instead
            final PsiType functionalInterfaceType = LambdaUtil.getFunctionalInterfaceType(expression, true);
            final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
            if (interfaceMethod != null) {
              final PsiElement resolve = resolver.get();
              if (resolve instanceof PsiMethod &&
                  (interfaceMethod == resolve || MethodSignatureUtil.isSuperMethod(interfaceMethod, (PsiMethod)resolve)) &&
                  TypeConversionUtil.isAssignable(functionalInterfaceType, qualifierType) &&
                  !mayOverrideDefaultMethods(qualifierExpression, functionalInterfaceType)) {
                holder.registerProblem(referenceNameElement, errorMessage, new ReplaceMethodRefWithQualifierFix());
              }
            }
          }
        }
      }

      private static boolean mayOverrideDefaultMethods(PsiExpression expression, PsiType functionalInterfaceType) {
        PsiClass functionalInterface = PsiUtil.resolveClassInClassTypeOnly(functionalInterfaceType);
        if (functionalInterface == null || !functionalInterface.isInterface()) return true;
        if (!ContainerUtil.exists(functionalInterface.getAllMethods(), m -> m.hasModifierProperty(PsiModifier.DEFAULT))) {
          return false;
        }
        PsiType qualifierType = null;
        if (CommonDataflow.getDfType(expression) instanceof DfReferenceType refType) {
          qualifierType = refType.getConstraint().getPsiType(functionalInterface.getProject());
        }
        if (qualifierType == null) {
          qualifierType = expression.getType();
        }
        PsiClass qualifierClass = PsiUtil.resolveClassInClassTypeOnly(qualifierType);
        if (qualifierClass == null) return true;
        if (qualifierClass.isEquivalentTo(functionalInterface)) return false;
        for (PsiMethod method : qualifierClass.getAllMethods()) {
          for (PsiMethod superMethod : method.findSuperMethods(functionalInterface)) {
            if (superMethod.hasModifierProperty(PsiModifier.DEFAULT)) {
              return true;
            }
          }
        }
        return false;
      }
    };
  }

  private static class ReplaceMethodRefWithQualifierFix extends PsiUpdateModCommandQuickFix {
    @Override
    public @Nls @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("replace.method.ref.with.qualifier.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiMethodReferenceExpression) {
        final PsiExpression qualifierExpression = ((PsiMethodReferenceExpression)parent).getQualifierExpression();
        if (qualifierExpression != null) {
          parent.replace(qualifierExpression);
        }
      }
      if (parent instanceof PsiReturnStatement || parent instanceof PsiExpressionStatement) {
        parent = PsiTreeUtil.getParentOfType(parent, PsiLambdaExpression.class);
      }
      if (parent instanceof PsiLambdaExpression) {
        PsiExpression expression = LambdaUtil.extractSingleExpressionFromBody(((PsiLambdaExpression)parent).getBody());
        if (expression instanceof PsiMethodCallExpression) {
          PsiExpression qualifierExpression = ((PsiMethodCallExpression)expression).getMethodExpression().getQualifierExpression();
          if (qualifierExpression != null) {
            parent.replace(qualifierExpression);
          }
        }
      }
    }
  }
}
