// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ChangeModifierFix extends PsiUpdateModCommandQuickFix {

  @PsiModifier.ModifierConstant private final String modifierText;

  public ChangeModifierFix(@NonNls @PsiModifier.ModifierConstant String modifierText) {
    this.modifierText = modifierText;
  }

  @Override
  public @NotNull String getName() {
    return PsiModifier.PACKAGE_LOCAL.equals(modifierText)
           ? InspectionGadgetsBundle.message("change.modifier.package.private.quickfix")
           : InspectionGadgetsBundle.message("change.modifier.quickfix", modifierText);
  }

  @Override
  public @NotNull String getFamilyName() {
    return InspectionGadgetsBundle.message("change.modifier.fix.family.name");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    final PsiModifierListOwner modifierListOwner = PsiTreeUtil.getParentOfType(element, PsiModifierListOwner.class);
    if (modifierListOwner == null) {
      return;
    }
    final PsiModifierList modifiers = modifierListOwner.getModifierList();
    if (modifiers == null) {
      return;
    }
    modifiers.setModifierProperty(modifierText, true);
  }
}