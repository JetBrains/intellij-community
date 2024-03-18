// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.visibility;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AmbiguousFieldAccessInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiClass fieldClass = (PsiClass)infos[0];
    final PsiVariable variable = (PsiVariable)infos[1];
    if (variable instanceof PsiLocalVariable) {
      return InspectionGadgetsBundle.message("ambiguous.field.access.hides.local.variable.problem.descriptor", fieldClass.getName());
    }
    else if (variable instanceof PsiParameter) {
      return InspectionGadgetsBundle.message("ambiguous.field.access.hides.parameter.problem.descriptor", fieldClass.getName());
    }
    else {
      return InspectionGadgetsBundle.message("ambiguous.field.access.hides.field.problem.descriptor", fieldClass.getName());
    }
  }

  @Override
  public @NotNull BaseInspectionVisitor buildVisitor() {
    return new AmbiguousFieldAccessVisitor();
  }

  private static class AmbiguousFieldAccessVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      if (expression.isQualified()) {
        return;
      }
      final PsiElement target = expression.resolve();
      if (target == null) {
        return;
      }
      if (!(target instanceof PsiField field)) {
        return;
      }
      final PsiClass fieldClass = field.getContainingClass();
      if (fieldClass == null) {
        return;
      }
      PsiClass containingClass = ClassUtils.getContainingClass(expression);
      if (containingClass == null) {
        return;
      }
      if (!containingClass.isInheritor(fieldClass, true)) {
        return;
      }
      final PsiElement parent = containingClass.getParent();
      if (parent instanceof PsiFile) {
        return;
      }
      final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(expression.getProject()).getResolveHelper();
      final String referenceText = expression.getText();
      final PsiVariable variable = resolveHelper.resolveAccessibleReferencedVariable(referenceText, parent);
      if (variable == null || field == variable) {
        return;
      }
      if (expression.advancedResolve(false).getCurrentFileResolveScope() instanceof PsiImportStaticStatement) {
        // is statically imported
        return;
      }
      registerError(expression, fieldClass, variable, isOnTheFly());
    }
  }

  @Override
  protected @Nullable LocalQuickFix buildFix(Object... infos) {
    return new AmbiguousFieldAccessFix();
  }

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    boolean isOnTheFly = (boolean)infos[2];
    final PsiVariable variable = (PsiVariable)infos[1];
    if (isOnTheFly) {
      return new LocalQuickFix[] { new NavigateToApparentlyAccessedElementFix(variable), new AmbiguousFieldAccessFix() };
    }
    else {
      return new LocalQuickFix[] { new AmbiguousFieldAccessFix() };
    }
  }

  private static class AmbiguousFieldAccessFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("ambiguous.field.access.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof PsiReferenceExpression referenceExpression)) {
        return;
      }
      final String newExpressionText = "super." + referenceExpression.getText();
      PsiReplacementUtil.replaceExpression(referenceExpression, newExpressionText);
    }
  }

  private static class NavigateToApparentlyAccessedElementFix extends ModCommandQuickFix {

    private final int type;

    NavigateToApparentlyAccessedElementFix(PsiVariable variable) {
      if (variable instanceof PsiLocalVariable) type = 1;
      else if (variable instanceof PsiParameter) type = 2;
      else type = 3;
    }

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("ambiguous.field.access.navigate.quickfix", type);
    }

    @Override
    public @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiVariable variable = getApparentlyAccessedVariable(project, element);
      if (variable != null) {
        PsiIdentifier identifier = variable.getNameIdentifier();
        if (identifier != null) {
          return ModCommand.select(identifier);
        }
      }
      return ModCommand.nop();
    }

    private static @Nullable PsiVariable getApparentlyAccessedVariable(Project project, PsiElement element) {
      PsiClass containingClass = ClassUtils.getContainingClass(element);
      if (containingClass == null) return null;
      final PsiElement parent = containingClass.getParent();
      final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(project).getResolveHelper();
      final String referenceText = element.getText();
      return resolveHelper.resolveAccessibleReferencedVariable(referenceText, parent);
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      PsiVariable variable = getApparentlyAccessedVariable(project, previewDescriptor.getPsiElement());
      NavigatablePsiElement element = ObjectUtils.tryCast(variable, NavigatablePsiElement.class);
      if (element == null) return IntentionPreviewInfo.EMPTY;
      return IntentionPreviewInfo.navigate(element);
    }
  }
}