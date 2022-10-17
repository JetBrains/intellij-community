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
  @SafeFieldForPreview private final @NotNull EffectivelyFinalFixer myFixer;

  private MakeVarEffectivelyFinalFix(@NotNull PsiLocalVariable variable, @NotNull EffectivelyFinalFixer fixer) {
    super(variable);
    myFixer = fixer;
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    if (!(startElement instanceof PsiLocalVariable local)) return;
    if (!myFixer.isAvailable(local)) return;
    myFixer.fix(local);
  }

  @Override
  public @NotNull String getText() {
    if (!(getStartElement() instanceof PsiLocalVariable local)) return getFamilyName();
    return myFixer.getText(local);
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("intention.name.make.variable.effectively.final");
  }

  public static @Nullable MakeVarEffectivelyFinalFix createFix(@NotNull PsiVariable variable) {
    if (!(variable instanceof PsiLocalVariable local)) return null;
    EffectivelyFinalFixer fixer = ContainerUtil.find(EffectivelyFinalFixer.EP_NAME.getExtensionList(), f -> f.isAvailable(local));
    if (fixer == null) return null;
    return new MakeVarEffectivelyFinalFix(local, fixer);
  }
}