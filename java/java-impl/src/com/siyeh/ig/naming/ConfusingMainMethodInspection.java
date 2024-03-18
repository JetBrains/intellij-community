/*
 * Copyright 2003-2023 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiMethodUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;

public final class ConfusingMainMethodInspection extends BaseInspection {

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RenameFix();
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return infos.length > 0
           ? InspectionGadgetsBundle.message("unrunnable.main.method.problem.descriptor")
           : InspectionGadgetsBundle.message("confusing.main.method.problem.descriptor");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ConfusingMainMethodVisitor();
  }

  private static class ConfusingMainMethodVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      if (!HardcodedMethodConstants.MAIN.equals(method.getName()) ||
          method.hasModifierProperty(PsiModifier.ABSTRACT) ||
          MethodUtils.hasSuper(method)) {
        return;
      }
      if (!PsiMethodUtil.isMainMethod(method)) {
        registerMethodError(method);
        return;
      }
      PsiClass containingClass = method.getContainingClass();
      if (!(containingClass instanceof PsiImplicitClass) && containingClass != null && containingClass.getQualifiedName() == null) {
        registerMethodError(method, Boolean.TRUE); // main method not runnable
      }
    }
  }
}