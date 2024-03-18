/*
 * Copyright 2003-2020 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.PsiModifier.*;

public final class SerialPersistentFieldsWithWrongSignatureInspection extends BaseInspection {

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "serialpersistentfields.with.wrong.signature.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SerialPersistentFieldsWithWrongSignatureVisitor();
  }

  private static class SerialPersistentFieldsWithWrongSignatureVisitor extends BaseInspectionVisitor {

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

    private void visitVariable(@NotNull PsiVariable variable, @Nullable PsiClass containingClass) {
      if (!SerializationUtils.isSerializable(containingClass)) return;
      if (!"serialPersistentFields".equals(variable.getName())) return;
      boolean rightReturnType = variable.getType().equalsToText("java.io.ObjectStreamField[]");
      if (rightReturnType && variable.hasModifierProperty(STATIC) && variable.hasModifierProperty(PRIVATE) &&
          variable.hasModifierProperty(FINAL)) {
        return;
      }
      registerVariableError(variable);
    }
  }
}