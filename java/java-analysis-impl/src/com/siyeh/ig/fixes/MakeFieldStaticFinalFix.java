/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.fixes;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.FinalUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MakeFieldStaticFinalFix extends PsiUpdateModCommandQuickFix {

  private final String fieldName;

  private MakeFieldStaticFinalFix(String fieldName) {
    this.fieldName = fieldName;
  }

  public static @NotNull LocalQuickFix buildFixUnconditional(
    @NotNull PsiField field) {
    return new MakeFieldStaticFinalFix(field.getName());
  }

  public static @Nullable LocalQuickFix buildFix(PsiField field) {
    final PsiExpression initializer = field.getInitializer();
    if (initializer == null) {
      return null;
    }
    if (!FinalUtils.canBeFinal(field)) {
      return null;
    }
    return new MakeFieldStaticFinalFix(field.getName());
  }

  @Override
  public @NotNull String getName() {
    return InspectionGadgetsBundle.message(
      "make.static.final.quickfix", fieldName);
  }

  @Override
  public @NotNull String getFamilyName() {
    return InspectionGadgetsBundle.message("make.field.static.final.fix.family.name");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiField field)) {
      return;
    }
    final PsiModifierList modifierList = field.getModifierList();
    if (modifierList == null) {
      return;
    }
    modifierList.setModifierProperty(PsiModifier.FINAL, true);
    modifierList.setModifierProperty(PsiModifier.STATIC, true);
  }
}