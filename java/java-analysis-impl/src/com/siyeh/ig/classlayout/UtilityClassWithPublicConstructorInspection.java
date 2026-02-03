/*
 * Copyright 2003-2025 Dave Griffith, Bas Leijdekkers
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
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.UtilityClassUtil;
import org.jetbrains.annotations.NotNull;

public final class UtilityClassWithPublicConstructorInspection extends BaseInspection {

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("utility.class.with.public.constructor.problem.descriptor");
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    final PsiClass psiClass = (PsiClass)infos[0];
    final boolean hasInheritors = (Boolean)infos[1];
    return new UtilityClassWithPublicConstructorFix(psiClass.getConstructors().length > 1, hasInheritors);
  }

  private static class UtilityClassWithPublicConstructorFix extends PsiUpdateModCommandQuickFix {

    private final boolean m_multipleConstructors;
    private final boolean m_hasInheritors;

    UtilityClassWithPublicConstructorFix(boolean multipleConstructors, boolean hasInheritors) {
      m_multipleConstructors = multipleConstructors;
      m_hasInheritors = hasInheritors;
    }

    @Override
    public @NotNull String getName() {
      return m_hasInheritors ?
        InspectionGadgetsBundle.message( "utility.class.with.public.constructor.make.protected.quickfix",
                                         Integer.valueOf(m_multipleConstructors ? 2 : 1)) :
        InspectionGadgetsBundle.message( "utility.class.with.public.constructor.make.private.quickfix",
                                         Integer.valueOf(m_multipleConstructors ? 2 : 1));
    }

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("utility.class.with.public.constructor.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement classNameIdentifier, @NotNull ModPsiUpdater updater) {
      final PsiClass psiClass = (PsiClass)classNameIdentifier.getParent();
      if (psiClass == null) {
        return;
      }

      String modifier = m_hasInheritors ? PsiModifier.PROTECTED : PsiModifier.PRIVATE;
      for (PsiMethod constructor : psiClass.getConstructors()) {
        constructor.getModifierList().setModifierProperty(modifier, true);
      }
    }
  }

  @Override
  public @NotNull BaseInspectionVisitor buildVisitor() {
    return new StaticClassWithPublicConstructorVisitor();
  }

  private static class StaticClassWithPublicConstructorVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (!UtilityClassUtil.isUtilityClass(aClass) || !hasPublicConstructor(aClass)) {
        return;
      }

      boolean hasInheritors = ClassInheritorsSearch.search(aClass, aClass.getUseScope(), false).findFirst() != null;
      registerClassError(aClass, aClass, hasInheritors);
    }

    private static boolean hasPublicConstructor(PsiClass aClass) {
      for (PsiMethod constructor : aClass.getConstructors()) {
        if (constructor.hasModifierProperty(PsiModifier.PUBLIC)) {
          return true;
        }
      }
      return false;
    }
  }
}