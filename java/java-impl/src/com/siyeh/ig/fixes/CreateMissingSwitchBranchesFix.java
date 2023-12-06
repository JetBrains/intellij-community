// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.*;
import com.siyeh.ig.psiutils.CreateSwitchBranchesUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

public abstract class CreateMissingSwitchBranchesFix extends BaseSwitchFix {
  @NotNull
  protected final Set<String> myNames;

  public CreateMissingSwitchBranchesFix(@NotNull PsiSwitchBlock block, @NotNull Set<String> names) {
    super(block);
    myNames = names;
  }

  @Override
  protected String getText(@NotNull PsiSwitchBlock switchBlock) {
    return CreateSwitchBranchesUtil.getActionName(myNames.stream().sorted().toList());
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiSwitchBlock switchBlock, @NotNull ModPsiUpdater updater) {
    final PsiExpression selector = switchBlock.getExpression();
    if (selector == null) return;
    final PsiClassType switchType = (PsiClassType)selector.getType();
    if (switchType == null) return;
    final PsiClass psiClass = switchType.resolve();
    if (psiClass == null) return;
    List<PsiSwitchLabelStatementBase> addedLabels = CreateSwitchBranchesUtil
      .createMissingBranches(switchBlock, getAllNames(psiClass, switchBlock), getNames(switchBlock), getCaseExtractor());
    CreateSwitchBranchesUtil.createTemplate(switchBlock, addedLabels, updater);
  }

  @NotNull
  protected Set<String> getNames(@NotNull PsiSwitchBlock switchBlock) {
    return myNames;
  }

  abstract protected @NotNull List<String> getAllNames(@NotNull PsiClass aClass, @NotNull PsiSwitchBlock switchBlock);
  abstract protected @NotNull Function<PsiSwitchLabelStatementBase, List<String>> getCaseExtractor();
}
