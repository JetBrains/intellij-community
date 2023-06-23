// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.PsiUpdateModCommandAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class SealClassFromPermitsListAction extends PsiUpdateModCommandAction<PsiClass> {

  public SealClassFromPermitsListAction(@NotNull PsiClass element) {
    super(element);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiClass psiClass) {
    return DirectClassInheritorsSearch.search(psiClass).findFirst() != null ?
           Presentation.of(getFamilyName()) : null;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiClass psiClass, @NotNull ModPsiUpdater updater) {
    if (psiClass.hasModifierProperty(PsiModifier.SEALED)) return;
    SealClassAction.sealClass(context.project(), updater, psiClass);
  }

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return QuickFixBundle.message("seal.class.from.permits.list.fix");
  }
}
