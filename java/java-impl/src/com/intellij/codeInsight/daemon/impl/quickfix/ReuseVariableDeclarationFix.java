// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.scope.processor.VariablesNotProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReuseVariableDeclarationFix implements IntentionAction {
  private final PsiLocalVariable myVariable;

  public ReuseVariableDeclarationFix(@NotNull PsiLocalVariable variable) {
    this.myVariable = variable;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("reuse.variable.declaration.family");
  }

  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message("reuse.variable.declaration.text", myVariable.getName());
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    if (!myVariable.isValid()) {
      return false;
    }
    final PsiVariable previousVariable = findPreviousVariable(myVariable);
    return previousVariable != null &&
           Comparing.equal(previousVariable.getType(), myVariable.getType()) &&
           BaseIntentionAction.canModify(myVariable);
  }

  @NotNull
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return myVariable;
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    final PsiVariable refVariable = findPreviousVariable(myVariable);
    if (refVariable == null) return;

    final PsiExpression initializer = myVariable.getInitializer();
    if (initializer == null) {
      myVariable.delete();
      return;
    }

    boolean wasFinal = refVariable.hasModifierProperty(PsiModifier.FINAL);
    PsiUtil.setModifierProperty(refVariable, PsiModifier.FINAL, false);
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(myVariable.getProject());
    final PsiElement statement = factory.createStatementFromText(
      myVariable.getName() + " = " +
      ExpressionUtils.convertInitializerToExpression(initializer, factory, myVariable.getType()).getText() + ";", null);
    myVariable.getParent().replace(statement);
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

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    return new ReuseVariableDeclarationFix(PsiTreeUtil.findSameElementInCopy(myVariable, target));
  }
}
