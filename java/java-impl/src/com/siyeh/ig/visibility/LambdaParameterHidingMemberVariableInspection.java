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
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class LambdaParameterHidingMemberVariableInspection extends BaseInspection {
  @SuppressWarnings("PublicField")
  public boolean m_ignoreInvisibleFields = true;

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RenameFix();
  }

  @Override
  @NotNull
  public String getID() {
    return "LambdaParameterHidesMemberVariable";
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    return InspectionGadgetsBundle.message("lambda.parameter.hides.member.variable.problem.descriptor", aClass.getName());
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("m_ignoreInvisibleFields",
               InspectionGadgetsBundle.message("lambda.parameter.hides.member.variable.ignore.invisible.option")));
  }

  @Override
  public boolean shouldInspect(@NotNull PsiFile file) {
    return PsiUtil.isLanguageLevel8OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new LambdaParameterHidingMemberVariableVisitor();
  }

  private class LambdaParameterHidingMemberVariableVisitor extends BaseInspectionVisitor {

    @Override
    public void visitParameter(@NotNull PsiParameter variable) {
      super.visitParameter(variable);
      final PsiElement declarationScope = variable.getDeclarationScope();
      if (!(declarationScope instanceof PsiLambdaExpression)) {
        return;
      }
      final PsiClass aClass = LocalVariableHidingMemberVariableInspection
        .findSurroundingClassWithHiddenField(variable, m_ignoreInvisibleFields, true);
      if (aClass ==  null) {
        return;
      }
      registerVariableError(variable, aClass);
    }
  }
}