// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ImportUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class UnnecessarilyQualifiedStaticallyImportedElementInspection extends BaseInspection implements CleanupLocalInspectionTool{

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    final PsiMember member = (PsiMember)infos[0];
    return InspectionGadgetsBundle.message("unnecessarily.qualified.statically.imported.element.problem.descriptor", member.getName());
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new UnnecessarilyQualifiedStaticallyImportedElementFix();
  }

  private static class UnnecessarilyQualifiedStaticallyImportedElementFix extends PsiUpdateModCommandQuickFix {

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("unnecessarily.qualified.statically.imported.element.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      element.delete();
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessarilyQualifiedStaticallyImportedElementVisitor();
  }

  private static class UnnecessarilyQualifiedStaticallyImportedElementVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }

    @Override
    public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
      super.visitReferenceElement(reference);
      if (JavaKeywords.YIELD.equals(reference.getReferenceName()) && reference.getParent() instanceof PsiMethodCallExpression) {
        // Qualifier might be required since Java 14, so don't warn
        return;
      }
      if (ImportUtils.isAlreadyStaticallyImported(reference)) {
        registerError(Objects.requireNonNull(reference.getQualifier()), reference.resolve());
      }
    }
  }
}
