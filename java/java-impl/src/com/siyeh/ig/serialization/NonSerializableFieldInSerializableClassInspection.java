/*
 * Copyright 2006-2024 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.options.JavaClassValidator;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.util.SpecialAnnotationsUtilBase;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.SerializationUtils;
import com.siyeh.ig.ui.ExternalizableStringSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.options.OptPane.pane;
import static com.intellij.codeInspection.options.OptPane.stringList;

public final class NonSerializableFieldInSerializableClassInspection extends SerializableInspectionBase {
  @SuppressWarnings({"PublicField"})
  public final ExternalizableStringSet ignorableAnnotations = new ExternalizableStringSet();

  @Override
  protected @NotNull OptPane getAdditionalOptions() {
    return pane(stringList("ignorableAnnotations", InspectionGadgetsBundle.message("ignore.if.annotated.by"),
                           new JavaClassValidator().annotationsOnly()));
  }

  @Override
  public @NotNull String buildErrorString(Object... infos) {
    boolean isRecord = (boolean)infos[1];
    return isRecord
           ? InspectionGadgetsBundle.message("non.serializable.component.in.serializable.record.problem.descriptor")
           :InspectionGadgetsBundle.message("non.serializable.field.in.serializable.class.problem.descriptor");
  }

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    final PsiVariable field = (PsiVariable)infos[0];
    return SpecialAnnotationsUtilBase.createAddAnnotationToListFixes(field, this, insp -> insp.ignorableAnnotations)
      .toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NonSerializableFieldInSerializableClassVisitor();
  }

  private class NonSerializableFieldInSerializableClassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitField(@NotNull PsiField field) {
      PsiClass containingClass = field.getContainingClass();
      if (containingClass == null || containingClass.isEnum()) {
        // https://docs.oracle.com/javase/1.5.0/docs/guide/serialization/spec/serial-arch.html#enum
        return;
      }
      if (ignoreAnonymousInnerClasses && containingClass instanceof PsiAnonymousClass) {
        return;
      }
      if (isIgnoredSubclass(containingClass)) {
        return;
      }
      visitVariable(field, containingClass);
    }

    @Override
    public void visitRecordComponent(@NotNull PsiRecordComponent recordComponent) {
      visitVariable(recordComponent, recordComponent.getContainingClass());
    }

    private void visitVariable(@NotNull PsiVariable variable, @Nullable PsiClass containingClass) {
      if (variable.hasModifierProperty(PsiModifier.TRANSIENT) || variable.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      if (!SerializationUtils.isSerializable(containingClass)) {
        return;
      }
      PsiType variableType = variable.getType();
      if (SerializationUtils.isProbablySerializable(variableType)) {
        return;
      }
      PsiClass variableClass = PsiUtil.resolveClassInClassTypeOnly(variableType);
      if (variableClass != null && isIgnoredSubclass(variableClass)) {
        return;
      }
      if (SerializationUtils.hasWriteObject(containingClass) || SerializationUtils.hasWriteReplace(containingClass)) {
        return;
      }
      if (AnnotationUtil.isAnnotated(variable, ignorableAnnotations, 0)) {
        return;
      }
      registerVariableError(variable, variable, containingClass.isRecord());
    }
  }
}