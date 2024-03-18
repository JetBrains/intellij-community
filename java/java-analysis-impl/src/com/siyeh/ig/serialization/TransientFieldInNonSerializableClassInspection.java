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

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.RemoveModifierFix;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;

public final class TransientFieldInNonSerializableClassInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiField field = (PsiField)infos[0];
    return InspectionGadgetsBundle.message(
      "transient.field.in.non.serializable.class.problem.descriptor",
      field.getName());
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new RemoveModifierFix(PsiModifier.TRANSIENT);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TransientFieldInNonSerializableClassVisitor();
  }

  private static class TransientFieldInNonSerializableClassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitField(@NotNull PsiField field) {
      if (!field.hasModifierProperty(PsiModifier.TRANSIENT)) {
        return;
      }
      final PsiClass aClass = field.getContainingClass();
      if (SerializationUtils.isSerializable(aClass)) {
        return;
      }
      registerModifierError(PsiModifier.TRANSIENT, field, field);
    }
  }
}