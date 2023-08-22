// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix.makefinal;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiVariable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MakeVarEffectivelyFinalFix extends PsiUpdateModCommandAction<PsiLocalVariable> {
  private final @NotNull EffectivelyFinalFixer myFixer;

  private MakeVarEffectivelyFinalFix(@NotNull PsiLocalVariable variable, @NotNull EffectivelyFinalFixer fixer) {
    super(variable);
    myFixer = fixer;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiLocalVariable local, @NotNull ModPsiUpdater updater) {
    myFixer.fix(local);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiLocalVariable local) {
    if (!myFixer.isAvailable(local)) return null;
    return Presentation.of(myFixer.getText(local)).withPriority(PriorityAction.Priority.HIGH);
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("intention.name.make.variable.effectively.final");
  }

  public static @Nullable IntentionAction createFix(@NotNull PsiVariable variable) {
    if (!(variable instanceof PsiLocalVariable local)) return null;
    EffectivelyFinalFixer fixer = ContainerUtil.find(EffectivelyFinalFixer.EP_NAME.getExtensionList(), f -> f.isAvailable(local));
    if (fixer == null) return null;
    return new MakeVarEffectivelyFinalFix(local, fixer).asIntention();
  }
}