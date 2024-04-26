// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ImportUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

/**
 * @author Bas Leijdekkers
 */
public final class UnnecessarilyQualifiedInnerClassAccessInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @SuppressWarnings("PublicField")
  public boolean ignoreReferencesNeedingImport = false;

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    return InspectionGadgetsBundle.message(
      "unnecessarily.qualified.inner.class.access.problem.descriptor",
      aClass.getName());
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreReferencesNeedingImport", InspectionGadgetsBundle.message(
        "unnecessarily.qualified.inner.class.access.option")));
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new UnnecessarilyQualifiedInnerClassAccessFix();
  }

  private static class UnnecessarilyQualifiedInnerClassAccessFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "unnecessarily.qualified.inner.class.access.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiJavaCodeReferenceElement referenceElement)) {
        return;
      }
      final PsiElement target = referenceElement.resolve();
      if (!(target instanceof PsiClass aClass)) {
        return;
      }
      ImportUtils.addImportIfNeeded(aClass, element);
      final String shortName = aClass.getName();
      if (isReferenceToTarget(shortName, aClass, parent)) {
        new CommentTracker().deleteAndRestoreComments(element);
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessarilyQualifiedInnerClassAccessVisitor();
  }

  private static boolean isReferenceToTarget(String referenceText, @NotNull PsiClass target, PsiElement context) {
    final PsiJavaCodeReferenceElement reference =
      JavaPsiFacade.getElementFactory(target.getProject()).createReferenceFromText(referenceText, context);
    final JavaResolveResult[] results = reference.multiResolve(false);
    if (results.length == 0) {
      return true;
    }
    if (results.length > 1) {
      return false;
    }
    final JavaResolveResult result = results[0];
    return result.isAccessible() && target.isEquivalentTo(result.getElement());
  }

  private class UnnecessarilyQualifiedInnerClassAccessVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
      super.visitReferenceElement(reference);
      final PsiElement qualifier = reference.getQualifier();
      if (!(qualifier instanceof PsiJavaCodeReferenceElement referenceElement)) {
        return;
      }
      if (PsiTreeUtil.getParentOfType(reference, PsiImportStatementBase.class, PsiPackageStatement.class) != null) {
        return;
      }
      final PsiReferenceParameterList parameterList = referenceElement.getParameterList();
      if (parameterList != null && parameterList.getTypeParameterElements().length > 0) {
        return;
      }
      final PsiElement qualifierTarget = referenceElement.resolve();
      if (!(qualifierTarget instanceof PsiClass)) {
        return;
      }
      final PsiClass referenceClass = PsiTreeUtil.getParentOfType(reference, PsiClass.class);
      if (referenceClass == null) {
        return;
      }
      final PsiElement target = reference.resolve();
      if (!(target instanceof PsiClass aClass) || !PsiUtil.isAccessible(aClass, referenceClass, null)) {
        return;
      }
      ProblemHighlightType highlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
      final boolean needsImport =
        (!referenceClass.equals(qualifierTarget) || !ClassUtils.isInsideClassBody(reference, referenceClass)) &&
        (PsiTreeUtil.isAncestor(referenceClass, qualifierTarget, true) || !PsiTreeUtil.isAncestor(qualifierTarget, referenceClass, true));
      if (needsImport) {
        if (ignoreReferencesNeedingImport) {
          if (!isOnTheFly()) return;
          highlightType = ProblemHighlightType.INFORMATION;
        }
        if (!canBeImported(aClass)) {
          return;
        }
      }
      final PsiClass containingClass = aClass.getContainingClass();
      if (containingClass == null || !containingClass.equals(qualifierTarget)) {
        return;
      }
      final String shortName = aClass.getName();
      if (!isReferenceToTarget(shortName, aClass, reference)) {
        return;
      }
      registerError(qualifier, highlightType, aClass);
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }
  }

  private static boolean canBeImported(@NotNull PsiClass aClass) {
    while (true) {
      if (aClass.hasModifierProperty(PsiModifier.PRIVATE)) {
        return false;
      }
      PsiElement parent = aClass.getParent();
      if (parent == null || parent instanceof PsiDeclarationStatement) {
        return false;
      }
      if (parent instanceof PsiJavaFile) {
        return true;
      }
      if (!(parent instanceof PsiClass)) {
        return false;
      }
      aClass = (PsiClass)parent;
    }
  }
}
