// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public final class InnerClassReferencedViaSubclassInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiClass declaringClass = (PsiClass)infos[0];
    final PsiClass referencedClass = (PsiClass)infos[1];
    return InspectionGadgetsBundle.message("inner.class.referenced.via.subclass.problem.descriptor",
                                           declaringClass.getQualifiedName(), referencedClass.getQualifiedName());
  }

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new InnerClassReferencedViaSubclassFix();
  }

  private static class InnerClassReferencedViaSubclassFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("inner.class.referenced.via.subclass.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      final PsiIdentifier name = (PsiIdentifier)startElement;
      final PsiJavaCodeReferenceElement reference = (PsiJavaCodeReferenceElement)name.getParent();
      final PsiClass aClass = (PsiClass)reference.resolve();
      if (aClass == null) {
        return;
      }
      final PsiClass containingClass = aClass.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(reference.getProject());
      final PsiJavaCodeReferenceElement newReferenceElement;
      if (reference instanceof PsiReferenceExpression) {
        newReferenceElement = factory.createReferenceExpression(containingClass);
      } else {
        newReferenceElement = factory.createClassReferenceElement(containingClass);
      }
      final PsiElement qualifier = reference.getQualifier();
      if (qualifier == null) {
        return;
      }
      qualifier.replace(newReferenceElement);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new InnerClassReferencedViaSubclassVisitor();
  }

  private static class InnerClassReferencedViaSubclassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
      super.visitReferenceElement(reference);
      final PsiElement qualifier = reference.getQualifier();
      if (!(qualifier instanceof PsiJavaCodeReferenceElement qualifierReference)) {
        return;
      }
      final PsiElement qualifierTarget = qualifierReference.resolve();
      if (!(qualifierTarget instanceof PsiClass qualifierClass)) {
        return;
      }
      final PsiElement target = reference.resolve();
      if (!(target instanceof PsiClass aClass)) {
        return;
      }
      final PsiClass containingClass = aClass.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (!qualifierClass.isInheritor(containingClass, true)) {
        return;
      }
      final PsiElement identifier = reference.getReferenceNameElement();
      if (identifier == null) {
        return;
      }
      registerError(identifier, containingClass, qualifierClass);
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }
  }
}
