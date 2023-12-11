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
package com.siyeh.ig.serialization;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.PsiModifier.*;

public final class SerialVersionUIDNotStaticFinalInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "SerialVersionUIDWithWrongSignature";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "serialversionuid.private.static.final.long.problem.descriptor");
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    boolean needToFix = ((Boolean)infos[0]).booleanValue();
    return needToFix ? new SerialVersionUIDNotStaticFinalFix() : null;
  }

  private static class SerialVersionUIDNotStaticFinalFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "serialversionuid.private.static.final.long.quickfix");
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
      modifierList.setModifierProperty(PRIVATE, true);
      modifierList.setModifierProperty(STATIC, true);
      modifierList.setModifierProperty(FINAL, true);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SerialVersionUIDNotStaticFinalVisitor();
  }

  private static class SerialVersionUIDNotStaticFinalVisitor extends BaseInspectionVisitor {

    @Override
    public void visitField(@NotNull PsiField field) {
      PsiClass containingClass = field.getContainingClass();
      if (containingClass == null || containingClass.isInterface() || containingClass.isAnnotationType()) return;
      visitVariable(field, containingClass);
    }

    @Override
    public void visitRecordComponent(@NotNull PsiRecordComponent recordComponent) {
      visitVariable(recordComponent, recordComponent.getContainingClass());
    }

    private void visitVariable(@NotNull PsiVariable field, @Nullable PsiClass containingClass) {
      if (!SerializationUtils.isSerializable(containingClass)) return;
      if (!HardcodedMethodConstants.SERIAL_VERSION_UID.equals(field.getName())) return;
      final boolean rightReturnType = PsiTypes.longType().equals(field.getType());
      boolean isStaticField = field.hasModifierProperty(STATIC);
      if (rightReturnType && isStaticField && field.hasModifierProperty(PRIVATE) && field.hasModifierProperty(FINAL)) return;
      PsiIdentifier identifier = field.getNameIdentifier();
      assert identifier != null;
      registerError(identifier, Boolean.valueOf(rightReturnType && (!containingClass.isRecord() || isStaticField)));
    }
  }
}