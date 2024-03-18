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

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.*;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class LocalVariableHidingMemberVariableInspection extends BaseInspection {
  @SuppressWarnings("PublicField")
  public boolean m_ignoreInvisibleFields = true;
  @SuppressWarnings("PublicField")
  public boolean m_ignoreStaticMethods = true;

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new RenameFix();
  }

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  public String getID() {
    return "LocalVariableHidesMemberVariable";
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    return InspectionGadgetsBundle.message("local.variable.hides.member.variable.problem.descriptor", aClass.getName());
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("m_ignoreInvisibleFields", InspectionGadgetsBundle.message("field.name.hides.in.superclass.ignore.option")),
      checkbox("m_ignoreStaticMethods", InspectionGadgetsBundle.message("local.variable.hides.member.variable.ignore.option")));
  }

  @Nullable
  static PsiClass findSurroundingClassWithHiddenField(PsiVariable variable,
                                                      boolean ignoreInvisibleFields,
                                                      boolean ignoreStaticHidingInstance) {
    PsiClass aClass = ClassUtils.getContainingClass(variable);
    final String variableName = variable.getName();
    if (variableName == null) {
      return null;
    }
    while (aClass != null) {
      final PsiField field = aClass.findFieldByName(variableName, true);
      if (field != null) {
        if (!ignoreInvisibleFields || ClassUtils.isFieldVisible(field, aClass)) {
          if (!ignoreStaticHidingInstance || field.hasModifierProperty(PsiModifier.STATIC) ||
              !CommonJavaRefactoringUtil.isInStaticContext(variable, aClass)) {
            return aClass;
          }
        }
      }
      aClass = ClassUtils.getContainingClass(aClass);
    }
    return null;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new LocalVariableHidingMemberVariableVisitor();
  }

  private class LocalVariableHidingMemberVariableVisitor extends BaseInspectionVisitor {

    @Override
    public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
      super.visitLocalVariable(variable);
      final PsiClass aClass = findSurroundingClassWithHiddenField(variable, m_ignoreInvisibleFields, m_ignoreStaticMethods);
      if (aClass == null) {
        return;
      }
      registerVariableError(variable, aClass);
    }

    @Override
    public void visitParameter(@NotNull PsiParameter variable) {
      super.visitParameter(variable);
      final PsiElement declarationScope = variable.getDeclarationScope();
      if (!(declarationScope instanceof PsiCatchSection) && !(declarationScope instanceof PsiForeachStatement)) {
        return;
      }
      final PsiClass aClass = findSurroundingClassWithHiddenField(variable, m_ignoreInvisibleFields, m_ignoreStaticMethods);
      if (aClass == null) {
        return;
      }
      registerVariableError(variable, aClass);
    }
  }
}