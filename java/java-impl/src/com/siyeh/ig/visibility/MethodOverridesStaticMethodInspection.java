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
package com.siyeh.ig.visibility;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import org.jetbrains.annotations.NotNull;

public final class MethodOverridesStaticMethodInspection extends BaseInspection {
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RenameFix();
  }

  @Override
  @NotNull
  public String getID() {
    return "MethodOverridesStaticMethodOfSuperclass";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "method.overrides.static.problem.descriptor");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MethodOverridesStaticMethodVisitor();
  }

  private static class MethodOverridesStaticMethodVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      if (method.getNameIdentifier() == null || !method.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      final PsiClass ancestorClass = aClass.getSuperClass();
      if (ancestorClass == null) {
        return;
      }
      final PsiMethod[] methods = ancestorClass.findMethodsBySignature(method, true);
      for (final PsiMethod testMethod : methods) {
        if (testMethod.hasModifierProperty(PsiModifier.STATIC) && !testMethod.hasModifierProperty(PsiModifier.PRIVATE)) {
          registerMethodError(method);
          return;
        }
      }
    }
  }
}