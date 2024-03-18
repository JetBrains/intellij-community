/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.encapsulation;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.EncapsulateVariableFix;
import org.jetbrains.annotations.NotNull;

public final class ProtectedFieldInspection extends BaseInspection {

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    final PsiField field = (PsiField)infos[0];
    return new EncapsulateVariableFix(field.getName());
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "protected.field.problem.descriptor");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ProtectedFieldVisitor();
  }

  private static class ProtectedFieldVisitor extends BaseInspectionVisitor {

    @Override
    public void visitField(@NotNull PsiField field) {
      if (!field.hasModifierProperty(PsiModifier.PROTECTED)) {
        return;
      }
      if (field.hasModifierProperty(PsiModifier.STATIC) &&
          field.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      registerFieldError(field, field);
    }
  }
}