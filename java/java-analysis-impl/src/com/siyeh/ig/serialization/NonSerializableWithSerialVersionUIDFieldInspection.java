/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.DelegatingFixFactory;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;

public final class NonSerializableWithSerialVersionUIDFieldInspection extends BaseInspection {

  @Override
  public @NotNull String getID() {
    return "NonSerializableClassWithSerialVersionUID";
  }

  @Override
  public @NotNull String buildErrorString(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    if (aClass.isAnnotationType()) {
      return InspectionGadgetsBundle.message(
        "non.serializable.@interface.with.serialversionuid.problem.descriptor");
    }
    else if (aClass.isInterface()) {
      return InspectionGadgetsBundle.message(
        "non.serializable.interface.with.serialversionuid.problem.descriptor");
    }
    else if (aClass instanceof PsiAnonymousClass) {
      return InspectionGadgetsBundle.message(
        "non.serializable.anonymous.with.serialversionuid.problem.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message(
        "non.serializable.class.with.serialversionuid.problem.descriptor");
    }
  }

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    PsiField field = aClass.findFieldByName(HardcodedMethodConstants.SERIAL_VERSION_UID, false);
    if (field == null) return InspectionGadgetsFix.EMPTY_ARRAY;
    boolean onTheFly = (boolean)infos[1];
    LocalQuickFix removeFieldFix = (LocalQuickFix)QuickFixFactory.getInstance().createSafeDeleteFix(field);
    if (aClass.isAnnotationType() || aClass.isInterface() || aClass instanceof PsiAnonymousClass) {
      return onTheFly ? new LocalQuickFix[]{removeFieldFix} : InspectionGadgetsFix.EMPTY_ARRAY;
    }
    return onTheFly ? new LocalQuickFix[]{DelegatingFixFactory.createMakeSerializableFix(aClass), removeFieldFix} 
                    : new LocalQuickFix[]{DelegatingFixFactory.createMakeSerializableFix(aClass)};
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NonSerializableWithSerialVersionUIDVisitor();
  }

  private static class NonSerializableWithSerialVersionUIDVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      final PsiField field = aClass.findFieldByName(HardcodedMethodConstants.SERIAL_VERSION_UID, false);
      if (field == null) {
        return;
      }
      if (SerializationUtils.isSerializable(aClass)) {
        return;
      }
      registerClassError(aClass, aClass, isOnTheFly());
    }
  }
}