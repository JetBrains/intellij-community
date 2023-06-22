// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.codeInspection.PsiUpdateModCommandAction;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.scope.processor.VariablesNotProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReuseVariableDeclarationFix extends PsiUpdateModCommandAction<PsiLocalVariable> {
  public ReuseVariableDeclarationFix(@NotNull PsiLocalVariable variable) {
    super(variable);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("reuse.variable.declaration.family");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiLocalVariable variable) {
    final PsiVariable previousVariable = findPreviousVariable(variable);
    if (previousVariable == null || !Comparing.equal(previousVariable.getType(), variable.getType())) return null;
    return Presentation.of(QuickFixBundle.message("reuse.variable.declaration.text", variable.getName()));
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiLocalVariable variable, @NotNull ModPsiUpdater updater) {
    final PsiVariable refVariable = findPreviousVariable(variable);
    if (refVariable == null) return;

    final PsiExpression initializer = variable.getInitializer();
    if (initializer == null) {
      variable.delete();
      return;
    }

    boolean wasFinal = refVariable.hasModifierProperty(PsiModifier.FINAL);
    PsiUtil.setModifierProperty(refVariable, PsiModifier.FINAL, false);
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(variable.getProject());
    final PsiElement statement = factory.createStatementFromText(
      variable.getName() + " = " +
      ExpressionUtils.convertInitializerToExpression(initializer, factory, variable.getType()).getText() + ";", null);
    variable.getParent().replace(statement);
    if (wasFinal &&
        refVariable instanceof PsiLocalVariable &&
        HighlightControlFlowUtil.isEffectivelyFinal(refVariable, initializer, null)) {
      PsiUtil.setModifierProperty(refVariable, PsiModifier.FINAL, true);
    }
  }

  @Nullable
  static PsiVariable findPreviousVariable(PsiLocalVariable variable) {
    PsiElement scope = variable.getParent();
    while (scope != null) {
      if (scope instanceof PsiFile || scope instanceof PsiMethod || scope instanceof PsiClassInitializer) break;
      scope = scope.getParent();
    }
    if (scope == null) return null;

    PsiIdentifier nameIdentifier = variable.getNameIdentifier();
    if (nameIdentifier == null) {
      return null;
    }

    final VariablesNotProcessor processor = new VariablesNotProcessor(variable, false);
    PsiScopesUtil.treeWalkUp(processor, nameIdentifier, scope);
    return processor.size() > 0 ? processor.getResult(0) : null;
  }
}
