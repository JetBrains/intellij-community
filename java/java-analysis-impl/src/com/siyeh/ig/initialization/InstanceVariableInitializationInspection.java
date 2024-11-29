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
package com.siyeh.ig.initialization;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInsight.daemon.impl.UnusedSymbolUtil;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.MakeInitializerExplicitFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.InitializationUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class InstanceVariableInitializationInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignorePrimitives = false;

  @Override
  @NotNull
  public String getID() {
    return "InstanceVariableMayNotBeInitialized";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final Boolean junitTestCase = (Boolean)infos[0];
    if (junitTestCase.booleanValue()) {
      return InspectionGadgetsBundle.message(
        "instance.Variable.may.not.be.initialized.problem.descriptor.junit");
    }
    return InspectionGadgetsBundle.message(
      "instance.variable.may.not.be.initialized.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("m_ignorePrimitives", InspectionGadgetsBundle.message("primitive.fields.ignore.option")));
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new MakeInitializerExplicitFix();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new InstanceVariableInitializationVisitor();
  }

  private class InstanceVariableInitializationVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitField(@NotNull PsiField field) {
      if (field.hasModifierProperty(PsiModifier.STATIC) ||
          field.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      if (field.getInitializer() != null) {
        return;
      }
      if (NullableNotNullManager.isNullable(field)) {
        return;
      }
      if (m_ignorePrimitives) {
        final PsiType fieldType = field.getType();
        if (ClassUtils.isPrimitive(fieldType)) {
          return;
        }
      }
      final PsiClass aClass = field.getContainingClass();
      if (aClass == null) {
        return;
      }
      if (UnusedSymbolUtil.isImplicitWrite(field)) {
        return;
      }
      final boolean isTestClass = TestFrameworks.getInstance().isTestClass(aClass);
      if (isTestClass) {
        if (isInitializedInSetup(field, aClass)) {
          return;
        }
      }
      if (isInitializedInInitializer(field)) {
        return;
      }
      if (InitializationUtils.isInitializedInConstructors(field, aClass)) {
        return;
      }
      if (isTestClass) {
        registerFieldError(field, Boolean.TRUE);
      }
      else {
        registerFieldError(field, Boolean.FALSE);
      }
    }

    private static boolean isInitializedInSetup(PsiField field, PsiClass aClass) {
      final PsiMethod setupMethod = TestFrameworks.getInstance().findSetUpMethod(aClass);
      return InitializationUtils.methodAssignsVariableOrFails(setupMethod, field);
    }

    private static boolean isInitializedInInitializer(@NotNull PsiField field) {
      final PsiClass aClass = field.getContainingClass();
      if (aClass == null) {
        return false;
      }
      if (InitializationUtils.isInitializedInInitializer(field, aClass)) {
        return true;
      }
      final PsiField[] fields = aClass.getFields();
      for (PsiField otherField : fields) {
        if (field.equals(otherField)) {
          continue;
        }
        if (otherField.hasModifierProperty(PsiModifier.STATIC)) {
          continue;
        }
        final PsiExpression initializer = otherField.getInitializer();
        if (InitializationUtils.expressionAssignsVariableOrFails(initializer, field)) {
          return true;
        }
      }
      return false;
    }
  }
}