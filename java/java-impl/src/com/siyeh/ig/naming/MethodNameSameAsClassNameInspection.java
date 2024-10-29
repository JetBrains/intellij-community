/*
 * Copyright 2003-2009 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.naming;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.RenameFix;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class MethodNameSameAsClassNameInspection extends BaseInspection {
  private static final Set<String> MODIFIERS_ALLOWED_ON_CONSTRUCTORS = Set.of(
    // JLS 8.8.3
    PsiModifier.PUBLIC, PsiModifier.PROTECTED, PsiModifier.PRIVATE
  );

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    final Boolean onTheFly = (Boolean)infos[0];
    final Boolean canBeConvertedToConstructor = (Boolean)infos[1];
    List<LocalQuickFix> fixes = new ArrayList<>();
    if (onTheFly) {
      fixes.add(new RenameFix());
    }
    if (canBeConvertedToConstructor) {
      fixes.add(new MethodNameSameAsClassNameFix());
    }
    return fixes.toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "method.name.same.as.class.name.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MethodNameSameAsClassNameVisitor();
  }

  private static class MethodNameSameAsClassNameFix
    extends PsiUpdateModCommandQuickFix {

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("make.method.ctr.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiMethod method = ObjectUtils.tryCast(element.getParent(), PsiMethod.class);
      if (method == null) return;
      final PsiTypeElement returnTypeElement = method.getReturnTypeElement();
      if (returnTypeElement == null) return;
      PsiModifierList modifiers = method.getModifierList();
      for (String modifier : PsiModifier.MODIFIERS) {
        if (!MODIFIERS_ALLOWED_ON_CONSTRUCTORS.contains(modifier)) {
          modifiers.setModifierProperty(modifier, false);
        }
      }
      returnTypeElement.delete();
    }
  }

  private static class MethodNameSameAsClassNameVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      // no call to super, so it doesn't drill down into inner classes
      if (method.isConstructor()) return;
      final String methodName = method.getName();
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) return;
      if (containingClass instanceof PsiImplicitClass) return;
      final String className = containingClass.getName();
      if (!methodName.equals(className)) return;

      MethodSignature signature = method.getSignature(PsiSubstitutor.EMPTY);
      boolean canReplaceWithConstructor =
        method.getBody() != null && !containingClass.isInterface() &&
        !ContainerUtil.exists(containingClass.getConstructors(), ctor -> MethodSignatureUtil.areErasedParametersEqual(signature, ctor.getSignature(PsiSubstitutor.EMPTY)));
      registerMethodError(method, isOnTheFly(), canReplaceWithConstructor);
    }
  }
}