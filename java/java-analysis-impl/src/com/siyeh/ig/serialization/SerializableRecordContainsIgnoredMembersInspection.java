// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.serialization;

import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;

public final class SerializableRecordContainsIgnoredMembersInspection extends BaseInspection {

  @Override
  public boolean shouldInspect(@NotNull PsiFile file) {
    return PsiUtil.isLanguageLevel14OrHigher(file);
  }

  @Override
  protected @NotNull @InspectionMessage String buildErrorString(Object... infos) {
    final Object member = infos[0];
    if (member instanceof PsiField) {
      return InspectionGadgetsBundle.message("serializable.record.contains.ignored.field.problem.descriptor", member);
    }
    else {
      return InspectionGadgetsBundle.message("serializable.record.contains.ignored.method.problem.descriptor", member);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SerializableRecordContainsIgnoredMembersVisitor();
  }

  /**
   * @see <a href="https://docs.oracle.com/en/java/javase/14/docs/specs/records-serialization.html">Record Serialization</a>
   */
  private static class SerializableRecordContainsIgnoredMembersVisitor extends BaseInspectionVisitor {

    @Override
    public void visitField(@NotNull PsiField field) {
      super.visitField(field);
      PsiClass psiClass = MissingSerialAnnotationInspection.getSerializablePsiClass(field);
      if (psiClass == null || !psiClass.isRecord()) return;

      if (SerializationUtils.isExternalizable(psiClass)) return;
      if (SerializationUtils.isSerialPersistentFields(field)) {
        registerFieldError(field, field);
      }
    }

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      PsiClass psiClass = MissingSerialAnnotationInspection.getSerializablePsiClass(method);
      if (psiClass == null || !psiClass.isRecord()) return;

      if (SerializationUtils.isExternalizable(psiClass) && method.hasModifierProperty(PsiModifier.PUBLIC) &&
          (SerializationUtils.isReadExternal(method) ||
           SerializationUtils.isWriteExternal(method))) {
        registerMethodError(method, method);
      }
      else if (SerializationUtils.isSerializable(psiClass) && method.hasModifierProperty(PsiModifier.PRIVATE) &&
               (SerializationUtils.isWriteObject(method) ||
                SerializationUtils.isReadObject(method) ||
                SerializationUtils.isReadObjectNoData(method))) {
        registerMethodError(method, method);
      }
    }
  }
}
