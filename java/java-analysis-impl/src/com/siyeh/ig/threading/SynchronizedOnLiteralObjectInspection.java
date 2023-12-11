// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.threading;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class SynchronizedOnLiteralObjectInspection extends BaseInspection {

  @SuppressWarnings("PublicField") public boolean warnOnAllPossiblyLiterals = false;

  private static final Set<String> LITERAL_TYPES = Set.of(
    CommonClassNames.JAVA_LANG_STRING,
    CommonClassNames.JAVA_LANG_BOOLEAN,
    CommonClassNames.JAVA_LANG_CHARACTER,
    CommonClassNames.JAVA_LANG_BYTE,
    CommonClassNames.JAVA_LANG_SHORT,
    CommonClassNames.JAVA_LANG_INTEGER,
    CommonClassNames.JAVA_LANG_LONG
  );

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final String typeText = ((PsiType)infos[0]).getPresentableText();
    final int message = ((Integer)infos[1]).intValue();
    return switch (message) {
      case 1 -> InspectionGadgetsBundle.message("synchronized.on.literal.object.problem.descriptor", typeText);
      case 2 -> InspectionGadgetsBundle.message("synchronized.on.direct.literal.object.problem.descriptor", typeText);
      case 3 -> InspectionGadgetsBundle.message("synchronized.on.possibly.literal.object.problem.descriptor", typeText);
      default -> throw new AssertionError();
    };
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("warnOnAllPossiblyLiterals", InspectionGadgetsBundle.message("synchronized.on.literal.object.warn.on.all.option")));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SynchronizeOnLiteralVisitor();
  }

  private class SynchronizeOnLiteralVisitor extends BaseInspectionVisitor {

    @Override
    public void visitSynchronizedStatement(@NotNull PsiSynchronizedStatement statement) {
      super.visitSynchronizedStatement(statement);
      final PsiExpression lockExpression = statement.getLockExpression();
      if (lockExpression == null) {
        return;
      }
      final PsiType type = lockExpression.getType();
      if (type == null) {
        return;
      }
      if (!LITERAL_TYPES.contains(type.getCanonicalText())) {
          return;
      }
      if (!(lockExpression instanceof PsiReferenceExpression referenceExpression)) {
        if (ExpressionUtils.isLiteral(lockExpression)) {
          registerError(lockExpression, type, Integer.valueOf(2));
        }
        else if (warnOnAllPossiblyLiterals) {
          registerError(lockExpression, type, Integer.valueOf(3));
        }
        return;
      }
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiVariable variable)) {
        if (warnOnAllPossiblyLiterals) {
          registerError(lockExpression, type, Integer.valueOf(3));
        }
        return;
      }
      final PsiExpression initializer = variable.getInitializer();
      if (!ExpressionUtils.isLiteral(initializer)) {
        if (warnOnAllPossiblyLiterals) {
          registerError(lockExpression, type, Integer.valueOf(3));
        }
        return;
      }
      registerError(lockExpression, type, Integer.valueOf(1));
    }
  }
}