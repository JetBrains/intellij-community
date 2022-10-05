// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix.makefinal;

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MakeVarEffectivelyFinalFix extends LocalQuickFixAndIntentionActionOnPsiElement implements HighPriorityAction {
  private MakeVarEffectivelyFinalFix(@NotNull PsiLocalVariable variable) {
    super(variable);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    if (!(startElement instanceof PsiLocalVariable local)) return;
    EffectivelyFinalFixer fixer = ContainerUtil.find(FIXERS, f -> f.isAvailable(local));
    if (fixer == null) return;
    fixer.fix(local);
  }

  @Override
  public @NotNull String getText() {
    return JavaAnalysisBundle.message("intention.name.make.variable.effectively.final");
  }

  @Override
  public @NotNull String getFamilyName() {
    return getText();
  }

  public static @Nullable MakeVarEffectivelyFinalFix createFix(@NotNull PsiVariable variable) {
    if (!(variable instanceof PsiLocalVariable local)) return null;
    if (!ContainerUtil.exists(FIXERS, f -> f.isAvailable(local))) return null;
    return new MakeVarEffectivelyFinalFix(local);
  }

  static final EffectivelyFinalFixer[] FIXERS = {
    new MoveInitializerToIfBranchFixer()
  };

  sealed interface EffectivelyFinalFixer permits MoveInitializerToIfBranchFixer {
    boolean isAvailable(@NotNull PsiLocalVariable var);

    void fix(@NotNull PsiLocalVariable var);
  }
}