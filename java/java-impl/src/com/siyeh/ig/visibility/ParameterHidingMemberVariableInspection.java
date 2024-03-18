/*
 * Copyright 2003-2022 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.visibility;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.*;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class ParameterHidingMemberVariableInspection extends BaseInspection {
  @SuppressWarnings("PublicField")
  public boolean m_ignoreInvisibleFields = true;
  @SuppressWarnings("PublicField")
  public boolean m_ignoreStaticMethodParametersHidingInstanceFields = true;
  @SuppressWarnings("PublicField")
  public boolean m_ignoreForConstructors = false;
  @SuppressWarnings("PublicField")
  public boolean m_ignoreForPropertySetters = false;
  @SuppressWarnings("PublicField")
  public boolean m_ignoreForAbstractMethods = false;

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RenameFix();
  }

  @Override
  @NotNull
  public String getID() {
    return "ParameterHidesMemberVariable";
  }

  @Nullable
  @Override
  public String getAlternativeID() {
    return "hiding";
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    return InspectionGadgetsBundle.message("parameter.hides.member.variable.problem.descriptor", aClass.getName());
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("m_ignoreForPropertySetters", InspectionGadgetsBundle.message("parameter.hides.member.variable.ignore.setters.option")),
      checkbox("m_ignoreInvisibleFields", InspectionGadgetsBundle.message("parameter.hides.member.variable.ignore.superclass.option")),
      checkbox("m_ignoreForConstructors", InspectionGadgetsBundle.message("parameter.hides.member.variable.ignore.constructors.option")),
      checkbox("m_ignoreForAbstractMethods",
               InspectionGadgetsBundle.message("parameter.hides.member.variable.ignore.abstract.methods.option")),
      checkbox("m_ignoreStaticMethodParametersHidingInstanceFields",
               InspectionGadgetsBundle.message("parameter.hides.member.variable.ignore.static.parameters.option")));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ParameterHidingMemberVariableVisitor();
  }

  private class ParameterHidingMemberVariableVisitor extends BaseInspectionVisitor {

    @Override
    public void visitParameter(@NotNull PsiParameter variable) {
      super.visitParameter(variable);
      final PsiElement declarationScope = variable.getDeclarationScope();
      if (!(declarationScope instanceof PsiMethod method)) {
        return;
      }
      if (method.isConstructor() &&
          (m_ignoreForConstructors || JavaPsiRecordUtil.isCanonicalConstructor(method))) {
        return;
      }
      if (m_ignoreForAbstractMethods) {
        if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
          return;
        }
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass != null && containingClass.isInterface()) {
          return;
        }
      }
      if (m_ignoreForPropertySetters) {
        final String methodName = method.getName();
        if (methodName.startsWith(HardcodedMethodConstants.SET) && PsiTypes.voidType().equals(method.getReturnType())) {
          return;
        }

        if (PropertyUtilBase.isSimplePropertySetter(method)) {
          return;
        }
      }
      final PsiClass aClass = LocalVariableHidingMemberVariableInspection
        .findSurroundingClassWithHiddenField(variable, m_ignoreInvisibleFields, m_ignoreStaticMethodParametersHidingInstanceFields);
      if (aClass == null) {
        return;
      }
      registerVariableError(variable, aClass);
    }
  }
}