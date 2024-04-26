/*
 * Copyright 2003-2022 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RemoveModifierFix;
import com.siyeh.ig.psiutils.SealedUtils;
import org.jetbrains.annotations.NotNull;

public final class FinalClassInspection extends BaseInspection {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[1];
    return InspectionGadgetsBundle.message("final.class.problem.descriptor", aClass.getName());
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new FinalClassVisitor();
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new RemoveModifierFix((String)infos[0]);
  }

  private static class FinalClassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (!aClass.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      if (SealedUtils.hasSealedParent(aClass)) {
        // removing `final` in this case would make the code uncompilable
        return;
      }
      registerModifierError(PsiModifier.FINAL, aClass, PsiModifier.FINAL, aClass);
    }
  }
}