// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.threading;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiSynchronizedStatement;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class SynchronizationOnGetClassInspection extends BaseInspection {

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("synchronization.on.get.class.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SynchronizationOnGetClassVisitor();
  }

  private static class SynchronizationOnGetClassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitSynchronizedStatement(@NotNull PsiSynchronizedStatement statement) {
      super.visitSynchronizedStatement(statement);
      final PsiExpression lockExpression = PsiUtil.skipParenthesizedExprDown(statement.getLockExpression());
      if (!(lockExpression instanceof PsiMethodCallExpression methodCallExpression)) {
        return;
      }
      if (!MethodCallUtils.isCallToMethod(methodCallExpression, CommonClassNames.JAVA_LANG_OBJECT, null, "getClass")) {
        return;
      }
      registerMethodCallError(methodCallExpression);
    }
  }
}
