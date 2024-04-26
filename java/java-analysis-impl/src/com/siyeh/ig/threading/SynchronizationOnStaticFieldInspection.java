// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.threading;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public final class SynchronizationOnStaticFieldInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "synchronization.on.static.field.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SynchronizationOnStaticFieldVisitor();
  }

  private static class SynchronizationOnStaticFieldVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitSynchronizedStatement(
      @NotNull PsiSynchronizedStatement statement) {
      super.visitSynchronizedStatement(statement);
      final PsiExpression lockExpression = statement.getLockExpression();
      if (!(lockExpression instanceof PsiReferenceExpression expression)) {
        return;
      }
      final PsiElement target = expression.resolve();
      if (!(target instanceof PsiField field)) {
        return;
      }
      if (!field.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      registerError(lockExpression);
    }
  }
}
