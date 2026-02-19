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

import com.intellij.lang.ASTNode;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class RemoveModifierFix extends PsiUpdateModCommandQuickFix {

  private final String modifierText;

  public RemoveModifierFix(String modifierText) {
    this.modifierText = modifierText;
  }

  @Override
  public @NotNull String getFamilyName() {
    return InspectionGadgetsBundle.message("remove.modifier.fix.family.name");
  }

  @Override
  public @NotNull String getName() {
    return InspectionGadgetsBundle.message("remove.modifier.quickfix",
                                           modifierText);
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement modifierElement, @NotNull ModPsiUpdater updater) {
    PsiElement modifierElementParent = modifierElement.getParent();
    if (modifierElementParent instanceof PsiModifierList &&
        modifierElementParent.getParent() instanceof PsiMethod method) {
      ASTNode node = method.getParameterList().getNode();
      if (node != null) {
        //align method parameters
        CodeEditUtil.markToReformat(node, true);
      }
    }
    modifierElement.delete();
  }
}