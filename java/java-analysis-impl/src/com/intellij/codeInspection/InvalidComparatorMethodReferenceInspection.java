// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InvalidComparatorMethodReferenceInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
        PsiElement referenceNameElement = expression.getReferenceNameElement();
        if(referenceNameElement == null) return;
        String name = referenceNameElement.getText();
        if (!name.equals("min") && !name.equals("max")) return;

        String className = getMethodReferenceClassName(expression);
        if (!CommonClassNames.JAVA_LANG_INTEGER.equals(className) && !CommonClassNames.JAVA_LANG_MATH
          .equals(className)) return;

        String functionalInterface = getFunctionalInterfaceClassName(expression);
        if (!CommonClassNames.JAVA_UTIL_COMPARATOR.equals(functionalInterface)) return;

        //noinspection DialogTitleCapitalization
        holder
          .registerProblem(expression,
                           "Method reference mapped to Comparator interface does not fulfill the Comparator contract",
                           new ReplaceWithComparatorQuickFix(name.equals("min")));
      }
    };
  }

  static @Nullable String getFunctionalInterfaceClassName(PsiMethodReferenceExpression expression) {
    PsiType functionalInterfaceType = expression.getFunctionalInterfaceType();
    if (!(functionalInterfaceType instanceof PsiClassType)) return null;
    PsiClass targetType = ((PsiClassType)functionalInterfaceType).resolve();
    if (targetType == null) return null;
    return targetType.getQualifiedName();
  }

  static @Nullable String getMethodReferenceClassName(PsiMethodReferenceExpression expression) {
    PsiExpression qualifierExpression = expression.getQualifierExpression();
    if (!(qualifierExpression instanceof PsiReferenceExpression)) return null;
    PsiElement refType = ((PsiReference)qualifierExpression).resolve();
    if (!(refType instanceof PsiClass)) return null;
    return ((PsiClass)refType).getQualifiedName();
  }

  private static class ReplaceWithComparatorQuickFix implements LocalQuickFix {
    private final boolean reverse;

    public ReplaceWithComparatorQuickFix(boolean reverse) {
      this.reverse = reverse;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return "Replace with " + (reverse ? "Comparator.reverseOrder()" : "Comparator.naturalOrder()");
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with comparator";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      PsiElement parent = element.getParent();
      if (parent != null) {
        PsiExpression newMethodExpression = JavaPsiFacade.getElementFactory(project)
          .createExpressionFromText(CommonClassNames.JAVA_UTIL_COMPARATOR + "." + (reverse ? "reverseOrder()" : "naturalOrder()"), parent);
        element.replace(newMethodExpression);
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(parent);
      }
    }
  }
}
