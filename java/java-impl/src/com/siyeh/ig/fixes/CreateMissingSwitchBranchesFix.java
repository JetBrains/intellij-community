// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.*;
import com.siyeh.ig.psiutils.CreateSwitchBranchesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

public abstract class CreateMissingSwitchBranchesFix extends BaseSwitchFix {
  protected final @Unmodifiable @NotNull Set<String> myNames;

  public CreateMissingSwitchBranchesFix(@NotNull PsiSwitchBlock block, @NotNull @Unmodifiable Set<String> names) {
    super(block);
    myNames = names;
  }

  @Override
  protected String getText(@NotNull PsiSwitchBlock switchBlock) {
    return CreateSwitchBranchesUtil.getActionName(myNames);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiSwitchBlock switchBlock, @NotNull ModPsiUpdater updater) {
    final PsiExpression selector = switchBlock.getExpression();
    if (selector == null) return;
    PsiType selectorType = selector.getType();
    if(selectorType instanceof PsiPrimitiveType primitiveType){
      selectorType = primitiveType.getBoxedType(selector);
    }
    final PsiClassType switchType = (PsiClassType)selectorType;
    if (switchType == null) return;
    final PsiClass psiClass = switchType.resolve();
    if (psiClass == null) return;
    List<PsiSwitchLabelStatementBase> addedLabels = CreateSwitchBranchesUtil
      .createMissingBranches(switchBlock, getAllNames(psiClass, switchBlock), getNames(switchBlock), getCaseExtractor());
    CreateSwitchBranchesUtil.createTemplate(switchBlock, addedLabels, updater);
  }

  protected @Unmodifiable @NotNull Set<String> getNames(@NotNull PsiSwitchBlock switchBlock) {
    return myNames;
  }

  protected abstract @Unmodifiable @NotNull List<String> getAllNames(@NotNull PsiClass aClass, @NotNull PsiSwitchBlock switchBlock);
  protected abstract @NotNull Function<PsiSwitchLabelStatementBase, @Unmodifiable List<String>> getCaseExtractor();
}
