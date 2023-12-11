// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.chainCall.ChainCallExtractor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.tryCast;

public final class ExtractChainedMapAction extends PsiUpdateModCommandAction<PsiElement> {
  public ExtractChainedMapAction() {
    super(PsiElement.class);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    PsiLocalVariable variable =
      PsiTreeUtil.getParentOfType(element, PsiLocalVariable.class, false, PsiStatement.class, PsiLambdaExpression.class);
    if (variable == null) {
      return null;
    }
    PsiExpression initializer = variable.getInitializer();
    if (initializer == null) return null;
    PsiDeclarationStatement declaration = tryCast(variable.getParent(), PsiDeclarationStatement.class);
    if (declaration == null || declaration.getDeclaredElements().length != 1) return null;
    PsiCodeBlock block = tryCast(declaration.getParent(), PsiCodeBlock.class);
    if (block == null || ArrayUtil.getFirstElement(block.getStatements()) != declaration) return null;
    PsiLambdaExpression lambda = tryCast(block.getParent(), PsiLambdaExpression.class);
    ChainCallExtractor extractor = ChainCallExtractor.findExtractor(lambda, initializer, variable.getType());
    if (extractor == null) return null;
    PsiParameter parameter = lambda.getParameterList().getParameters()[0];
    if (ContainerUtil.and(VariableAccessUtils.getVariableReferences(parameter, lambda), 
                          ref -> PsiTreeUtil.isAncestor(initializer, ref, false))) {
      return Presentation.of(JavaBundle.message("intention.extract.map.step.text", variable.getName(),
                                                extractor.getMethodName(parameter, initializer, variable.getType())));
    }
    return null;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PsiLocalVariable variable =
      PsiTreeUtil.getParentOfType(element, PsiLocalVariable.class, false, PsiStatement.class, PsiLambdaExpression.class);
    ChainCallExtractor.extractMappingStep(context.project(), variable);
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("intention.extract.map.step.family");
  }
}
