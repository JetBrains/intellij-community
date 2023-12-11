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
package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.RemoveModifierFix;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class PublicConstructorInNonPublicClassInspection extends BaseInspection {

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiMethod method = (PsiMethod)infos[0];
    return InspectionGadgetsBundle.message("public.constructor.in.non.public.class.problem.descriptor",
      method.getName());
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PublicConstructorInNonPublicClassVisitor();
  }

  @Override
  public LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    final List<LocalQuickFix> fixes = new ArrayList<>();
    final PsiMethod constructor = (PsiMethod)infos[0];
    final PsiClass aClass = constructor.getContainingClass();
    if (aClass != null && aClass.hasModifierProperty(PsiModifier.PRIVATE)) {
      fixes.add(new MakeConstructorPrivateFix());
    }
    fixes.add(new RemoveModifierFix(PsiModifier.PUBLIC));
    return fixes.toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  private static class MakeConstructorPrivateFix extends PsiUpdateModCommandQuickFix {

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("public.constructor.in.non.public.class.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      ((PsiModifierList)element.getParent()).setModifierProperty(PsiModifier.PRIVATE, true);
    }
  }

  private static class PublicConstructorInNonPublicClassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      //no call to super, so we don't drill into anonymous classes
      if (!method.isConstructor()) {
        return;
      }
      if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (containingClass.hasModifierProperty(PsiModifier.PUBLIC) ||
        containingClass.hasModifierProperty(PsiModifier.PROTECTED)) {
        return;
      }
      if (SerializationUtils.isExternalizable(containingClass)) {
        final PsiParameterList parameterList = method.getParameterList();
        if (parameterList.isEmpty()) {
          return;
        }
      }
      registerModifierError(PsiModifier.PUBLIC, method, method);
    }
  }
}