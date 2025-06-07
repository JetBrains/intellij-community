/*
 * Copyright 2003-2024 Dave Griffith, Bas Leijdekkers
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
 * limitations under the License.''
 */
package com.siyeh.ig.naming;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class StandardVariableNamesInspection extends BaseInspection {


  static final @NonNls Map<String, String> s_expectedTypes = Map.ofEntries(
    Map.entry("b", "byte"),
    Map.entry("c", "char"),
    Map.entry("ch", "char"),
    Map.entry("d", "double"),
    Map.entry("f", "float"),
    Map.entry("i", "int"),
    Map.entry("j", "int"),
    Map.entry("k", "int"),
    Map.entry("m", "int"),
    Map.entry("n", "int"),
    Map.entry("l", "long"),
    Map.entry("s", CommonClassNames.JAVA_LANG_STRING),
    Map.entry("str", CommonClassNames.JAVA_LANG_STRING)
  );

  static final @NonNls Map<String, String> s_boxingClasses = Map.of(
    "int", CommonClassNames.JAVA_LANG_INTEGER,
    "short", CommonClassNames.JAVA_LANG_SHORT,
    "boolean", CommonClassNames.JAVA_LANG_BOOLEAN,
    "long", CommonClassNames.JAVA_LANG_LONG,
    "byte", CommonClassNames.JAVA_LANG_BYTE,
    "float", CommonClassNames.JAVA_LANG_FLOAT,
    "double", CommonClassNames.JAVA_LANG_DOUBLE,
    "char", CommonClassNames.JAVA_LANG_CHARACTER
  );

  @SuppressWarnings("PublicField")
  public boolean ignoreParameterNameSameAsSuper = false;

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RenameFix();
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreParameterNameSameAsSuper", InspectionGadgetsBundle.message(
        "standard.variable.names.ignore.override.option")));
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public @NotNull String buildErrorString(Object... infos) {
    final PsiVariable variable = (PsiVariable)infos[0];
    final String name = variable.getName();
    final String expectedType = s_expectedTypes.get(name);
    if (PsiUtil.isLanguageLevel5OrHigher(variable)) {
      final String boxedType = s_boxingClasses.get(expectedType);
      if (boxedType != null) {
        return InspectionGadgetsBundle.message(
          "standard.variable.names.problem.descriptor2",
          expectedType, boxedType);
      }
    }
    return InspectionGadgetsBundle.message(
      "standard.variable.names.problem.descriptor", expectedType);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StandardVariableNamesVisitor();
  }

  private class StandardVariableNamesVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitVariable(@NotNull PsiVariable variable) {
      super.visitVariable(variable);
      final String variableName = variable.getName();
      if (variableName == null) return;
      final String expectedType = s_expectedTypes.get(variableName);
      if (expectedType == null) {
        return;
      }
      final PsiType type = variable.getType();
      final String typeText = type.getCanonicalText();
      if (expectedType.equals(typeText)) {
        return;
      }
      if (PsiUtil.isLanguageLevel5OrHigher(variable)) {
        final PsiPrimitiveType unboxedType =
          PsiPrimitiveType.getUnboxedType(type);
        if (unboxedType != null) {
          final String unboxedTypeText =
            unboxedType.getCanonicalText();
          if (expectedType.equals(unboxedTypeText)) {
            return;
          }
        }
      }
      if (ignoreParameterNameSameAsSuper &&
          isVariableNamedSameAsSuper(variable)) {
        return;
      }
      registerVariableError(variable, variable);
    }

    private static boolean isVariableNamedSameAsSuper(PsiVariable variable) {
      if (!(variable instanceof PsiParameter parameter)) {
        return false;
      }
      final PsiElement scope = parameter.getDeclarationScope();
      if (!(scope instanceof PsiMethod method)) {
        return false;
      }
      final String variableName = variable.getName();
      final int index =
        method.getParameterList().getParameterIndex(parameter);
      final PsiMethod[] superMethods = method.findSuperMethods();
      for (PsiMethod superMethod : superMethods) {
        final PsiParameter[] parameters =
          superMethod.getParameterList().getParameters();
        final PsiParameter overriddenParameter = parameters[index];
        if (overriddenParameter.getName().equals(variableName)) {
          return true;
        }
      }
      return false;
    }
  }
}