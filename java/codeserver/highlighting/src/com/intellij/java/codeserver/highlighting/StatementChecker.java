// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.java.codeserver.highlighting.errors.JavaIncompatibleTypeErrorContext;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

final class StatementChecker {
  private final @NotNull JavaErrorVisitor myVisitor;

  StatementChecker(@NotNull JavaErrorVisitor visitor) { myVisitor = visitor; }

  void checkCaseStatement(@NotNull PsiSwitchLabelStatementBase statement) {
    PsiSwitchBlock switchBlock = statement.getEnclosingSwitchBlock();
    if (switchBlock == null) {
      myVisitor.report(JavaErrorKinds.STATEMENT_CASE_OUTSIDE_SWITCH.create(statement));
    }
  }

  void checkGuard(@NotNull PsiSwitchLabelStatementBase statement) {
    PsiExpression guardingExpr = statement.getGuardExpression();
    if (guardingExpr == null) return;
    myVisitor.checkFeature(guardingExpr, JavaFeature.PATTERN_GUARDS_AND_RECORD_PATTERNS);
    if (myVisitor.hasErrorResults()) return;
    PsiCaseLabelElementList list = statement.getCaseLabelElementList();
    if (list != null) {
      if (!ContainerUtil.exists(list.getElements(), e -> e instanceof PsiPattern)) {
        myVisitor.report(JavaErrorKinds.GUARD_MISPLACED.create(guardingExpr));
        return;
      }
    }
    if (!TypeConversionUtil.isBooleanType(guardingExpr.getType())) {
      myVisitor.report(JavaErrorKinds.TYPE_INCOMPATIBLE.create(
        guardingExpr, new JavaIncompatibleTypeErrorContext(PsiTypes.booleanType(), guardingExpr.getType())));
      return;
    }
    Object constVal = JavaPsiFacade.getInstance(myVisitor.project()).getConstantEvaluationHelper().computeConstantExpression(guardingExpr);
    if (Boolean.FALSE.equals(constVal)) {
      myVisitor.report(JavaErrorKinds.GUARD_EVALUATED_TO_FALSE.create(guardingExpr));
    }
  }
}
