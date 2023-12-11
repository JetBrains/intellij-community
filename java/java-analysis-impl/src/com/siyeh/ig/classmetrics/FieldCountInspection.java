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
package com.siyeh.ig.classmetrics;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.*;

public final class FieldCountInspection extends ClassMetricInspection {

  private static final int FIELD_COUNT_LIMIT = 10;

  @SuppressWarnings("PublicField")
  public boolean m_countConstantFields = false;

  @SuppressWarnings("PublicField")
  public boolean m_considerStaticFinalFieldsConstant = false;

  @SuppressWarnings("PublicField")
  public boolean myCountEnumConstants = false;

  @Override
  @NotNull
  public String getID() {
    return "ClassWithTooManyFields";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("too.many.fields.problem.descriptor", infos[0]);
  }

  @Override
  protected int getDefaultLimit() {
    return FIELD_COUNT_LIMIT;
  }

  @Override
  protected String getConfigurationLabel() {
    return InspectionGadgetsBundle.message("too.many.fields.count.limit.option");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      number("m_limit", getConfigurationLabel(), 1, 1000),
      checkbox("m_countConstantFields", InspectionGadgetsBundle.message("field.count.inspection.include.constant.fields.in.count.checkbox")),
      checkbox("m_considerStaticFinalFieldsConstant", InspectionGadgetsBundle.message("field.count.inspection.static.final.fields.count.as.constant.checkbox")),
      checkbox("myCountEnumConstants", InspectionGadgetsBundle.message("field.count.inspection.include.enum.constants.in.count"))
    );
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new FieldCountVisitor();
  }

  private class FieldCountVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      final int totalFields = countFields(aClass);
      if (totalFields <= getLimit()) {
        return;
      }
      registerClassError(aClass, Integer.valueOf(totalFields));
    }

    private int countFields(PsiClass aClass) {
      int totalFields = 0;
      final PsiField[] fields = aClass.getFields();
      for (final PsiField field : fields) {
        if (field instanceof PsiEnumConstant) {
          if (myCountEnumConstants) {
            totalFields++;
          }
        }
        else if (m_countConstantFields || !fieldIsConstant(field)) {
          totalFields++;
        }
      }
      return totalFields;
    }

    private boolean fieldIsConstant(PsiField field) {
      if (!field.hasModifierProperty(PsiModifier.STATIC) || !field.hasModifierProperty(PsiModifier.FINAL)) {
        return false;
      }
      if (m_considerStaticFinalFieldsConstant) {
        return true;
      }
      return ClassUtils.isImmutable(field.getType());
    }
  }
}