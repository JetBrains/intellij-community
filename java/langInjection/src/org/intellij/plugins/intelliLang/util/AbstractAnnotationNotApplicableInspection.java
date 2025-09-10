// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang.util;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.RemoveAnnotationQuickFix;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_EXTERNAL;

public abstract class AbstractAnnotationNotApplicableInspection extends LocalInspectionTool {
  protected abstract String getAnnotationName(Project project);
  protected abstract boolean isTypeApplicable(PsiType type);
  protected abstract @InspectionMessage String getDescriptionTemplate();


  @Override
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      final String annotationName = getAnnotationName(holder.getProject());

      @Override
      public void visitAnnotation(@NotNull PsiAnnotation annotation) {
        final String name = annotation.getQualifiedName();
        if (annotationName.equals(name)) {
          checkAnnotation(annotation, holder);
        }
        else if (name != null) {
          final PsiClass psiClass = JavaPsiFacade.getInstance(annotation.getProject()).findClass(name, annotation.getResolveScope());
          if (psiClass != null && AnnotationUtil.isAnnotated(psiClass, annotationName, CHECK_EXTERNAL)) {
            checkAnnotation(annotation, holder);
          }
        }
      }
    };
  }

  private void checkAnnotation(PsiAnnotation annotation, ProblemsHolder holder) {
    final PsiModifierListOwner owner = PsiTreeUtil.getParentOfType(annotation, PsiModifierListOwner.class);
    if (owner instanceof PsiVariable) {
      final PsiType type = ((PsiVariable)owner).getType();
      if (isTypeApplicable(type)) {
        registerProblem(annotation, holder);
      }
    }
    else if (owner instanceof PsiMethod) {
      final PsiType type = ((PsiMethod)owner).getReturnType();
      if (isTypeApplicable(type)) {
        registerProblem(annotation, holder);
      }
    }
  }

  private void registerProblem(PsiAnnotation annotation, ProblemsHolder holder) {
    holder.registerProblem(annotation, getDescriptionTemplate(), new RemoveAnnotationQuickFix(annotation, null));
  }
}
