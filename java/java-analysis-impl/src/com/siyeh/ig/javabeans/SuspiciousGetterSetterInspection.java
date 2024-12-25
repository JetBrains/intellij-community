// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.javabeans;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PropertyUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

/**
 * @author Bas Leijdekkers
 */
public final class SuspiciousGetterSetterInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean onlyWarnWhenFieldPresent = false;

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return ((Boolean)infos[0]).booleanValue()
           ? InspectionGadgetsBundle.message("suspicious.setter.problem.descriptor", infos[1])
           : InspectionGadgetsBundle.message("suspicious.getter.problem.descriptor", infos[1]);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("onlyWarnWhenFieldPresent", JavaAnalysisBundle.message("inspection.suspicious.getter.setter.field.option")));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SuspiciousGetterSetterVisitor();
  }

  private class SuspiciousGetterSetterVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      final String name = method.getName();
      final String fieldName;
      final boolean setter;
      final String extractedFieldName;
      if (nameStartsWith(name, "get")) {
        final PsiField getterField = PropertyUtil.getFieldOfGetter(method);
        if (getterField == null) {
          return;
        }
        fieldName = getterField.getName();
        extractedFieldName = name.substring(3);
        setter = false;
      }
      else if (nameStartsWith(name, "is")) {
        final PsiField getterField = PropertyUtil.getFieldOfGetter(method);
        if (getterField == null) {
          return;
        }
        fieldName = getterField.getName();
        extractedFieldName = name.substring(2);
        setter = false;
      }
      else if (nameStartsWith(name, "set")) {
        final PsiField setterField = PropertyUtil.getFieldOfSetter(method);
        if (setterField == null) {
          return;
        }
        fieldName = setterField.getName();
        extractedFieldName = name.substring(3);
        setter = true;
      }
      else {
        return;
      }
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(method.getProject());
      final String computedFieldName = codeStyleManager.propertyNameToVariableName(extractedFieldName, VariableKind.FIELD);
      final String computedStaticFieldName = codeStyleManager.propertyNameToVariableName(extractedFieldName, VariableKind.STATIC_FINAL_FIELD);
      if (fieldName.equalsIgnoreCase(computedFieldName) || fieldName.equalsIgnoreCase(computedStaticFieldName)) {
        return;
      }
      if (onlyWarnWhenFieldPresent) {
        final PsiClass aClass = method.getContainingClass();
        if (aClass == null) {
          return;
        }
        if (aClass.findFieldByName(computedFieldName, true) == null &&
            aClass.findFieldByName(computedStaticFieldName, true) == null) {
          return;
        }
      }
      registerMethodError(method, Boolean.valueOf(setter), fieldName);
    }
  }

  private static boolean nameStartsWith(String name, String prefix) {
    return name.startsWith(prefix) && name.length() != prefix.length() && Character.isUpperCase(name.charAt(prefix.length()));
  }
}
