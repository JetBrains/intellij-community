// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class UnnecessarySuperQualifierInspection extends BaseInspection implements CleanupLocalInspectionTool {
  public boolean ignoreClarification;

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "unnecessary.super.qualifier.problem.descriptor"
    );
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreClarification", JavaAnalysisBundle.message("inspection.unnecessary.super.qualifier.option")));
  }

  @Override
  @Nullable
  protected LocalQuickFix buildFix(Object... infos) {
    return new UnnecessarySuperQualifierFix();
  }

  private static class UnnecessarySuperQualifierFix extends PsiUpdateModCommandQuickFix {
    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "unnecessary.super.qualifier.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      startElement.delete();
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessarySuperQualifierVisitor(ignoreClarification);
  }

  private static class UnnecessarySuperQualifierVisitor extends BaseInspectionVisitor {
    private final boolean myIgnoreClarification;

    UnnecessarySuperQualifierVisitor(boolean ignoreClarification) {
      myIgnoreClarification = ignoreClarification;
    }

    @Override
    public void visitSuperExpression(@NotNull PsiSuperExpression expression) {
      super.visitSuperExpression(expression);
      final PsiJavaCodeReferenceElement qualifier = expression.getQualifier();
      if (qualifier != null) {
        return;
      }
      final PsiElement parent = expression.getParent();
      if (!(parent instanceof PsiReferenceExpression referenceExpression)) {
        return;
      }
      final PsiElement grandParent = referenceExpression.getParent();
      if (grandParent instanceof PsiMethodCallExpression methodCallExpression) {
        if (!hasUnnecessarySuperQualifier(methodCallExpression)) {
          return;
        }

        if (myIgnoreClarification) {
          PsiClass containingClass = ClassUtils.getContainingClass(expression);
          if (containingClass != null) {
            final PsiElement classParent = containingClass.getParent();
            String referenceName = methodCallExpression.getMethodExpression().getReferenceName();
            if (referenceName != null) {
              PsiExpression copyCall = JavaPsiFacade.getElementFactory(expression.getProject())
                .createExpressionFromText(referenceName + methodCallExpression.getArgumentList().getText(), classParent);
              PsiMethod method = ((PsiMethodCallExpression)copyCall).resolveMethod();
              if (method != null && method != referenceExpression.resolve()) {
                return;
              }
            }
          }
        }
      }
      else {
        if (!hasUnnecessarySuperQualifier(referenceExpression)) {
          return;
        }
        if (myIgnoreClarification) {
          PsiClass containingClass = ClassUtils.getContainingClass(expression);
          if (containingClass != null) {
            final PsiElement classParent = containingClass.getParent();
            final String referenceText = referenceExpression.getReferenceName();
            if (referenceText != null) {
              PsiVariable variable = PsiResolveHelper.getInstance(expression.getProject())
                .resolveAccessibleReferencedVariable(referenceText, classParent);
              if (variable != null && variable != referenceExpression.resolve()) {
                return;
              }
            }
          }
        }
      }
      registerError(expression);
    }

    private static boolean hasUnnecessarySuperQualifier(PsiReferenceExpression referenceExpression) {
      final PsiClass parentClass = PsiTreeUtil.getParentOfType(referenceExpression, PsiClass.class);
      if (parentClass == null) {
        return false;
      }
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiField superField)) {
        return false;
      }
      final PsiReferenceExpression copy = (PsiReferenceExpression)referenceExpression.copy();
      final PsiElement qualifier = copy.getQualifier();
      if (qualifier == null) {
        return false;
      }
      qualifier.delete(); // remove super
      final JavaResolveResult resolveResult = copy.advancedResolve(false);
      return resolveResult.isValidResult() && superField == resolveResult.getElement();
    }

    private static boolean hasUnnecessarySuperQualifier(PsiMethodCallExpression methodCallExpression) {
      final PsiMethod superMethod = methodCallExpression.resolveMethod();
      if (superMethod == null) {
        return false;
      }
      final PsiClass aClass = PsiTreeUtil.getParentOfType(methodCallExpression, PsiClass.class);
      if (aClass != null && MethodUtils.isOverriddenInHierarchy(superMethod, aClass)) {
        return false;
      }
      // check that super.m() and m() resolve to the same method
      final PsiMethodCallExpression copy = (PsiMethodCallExpression)methodCallExpression.copy();
      final PsiReferenceExpression methodExpression = copy.getMethodExpression();
      final PsiElement qualifier = methodExpression.getQualifier();
      if (qualifier == null) {
        return false;
      }
      qualifier.delete(); //remove super
      final JavaResolveResult resolveResult = copy.resolveMethodGenerics();
      PsiElement element = resolveResult.getElement();
      return resolveResult.isValidResult() && superMethod.getManager().areElementsEquivalent(superMethod, element);
    }
  }
}