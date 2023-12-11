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
package com.siyeh.ig.style;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RemoveModifierFix;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class UnnecessaryFinalOnLocalVariableOrParameterInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @SuppressWarnings("PublicField")
  public boolean onlyWarnOnAbstractMethods = false;

  @SuppressWarnings("PublicField")
  public boolean reportLocalVariables = true;

  @SuppressWarnings("PublicField")
  public boolean reportPatternVariables = true;

  @SuppressWarnings("PublicField")
  public boolean reportParameters = true;

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiVariable variable = (PsiVariable)infos[0];
    final String variableName = variable.getName();
    if (variable instanceof PsiParameter) {
      return InspectionGadgetsBundle.message("unnecessary.final.on.parameter.problem.descriptor", variableName);
    }
    else {
      return InspectionGadgetsBundle.message("unnecessary.final.on.local.variable.problem.descriptor", variableName);
    }
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("reportLocalVariables", InspectionGadgetsBundle.message("unnecessary.final.report.local.variables.option")),
      checkbox("reportPatternVariables", InspectionGadgetsBundle.message("unnecessary.final.report.pattern.variables.option")),
      checkbox("reportParameters", InspectionGadgetsBundle.message("unnecessary.final.report.parameters.option"),
               checkbox("onlyWarnOnAbstractMethods", InspectionGadgetsBundle.message("unnecessary.final.on.parameter.only.interface.option"))));
  }


  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryFinalOnLocalVariableOrParameterVisitor();
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new RemoveModifierFix(PsiModifier.FINAL);
  }

  private class UnnecessaryFinalOnLocalVariableOrParameterVisitor extends BaseInspectionVisitor {

    @Override
    public void visitDeclarationStatement(@NotNull PsiDeclarationStatement statement) {
      super.visitDeclarationStatement(statement);
      if (!reportLocalVariables) {
        return;
      }
      final PsiElement[] declaredElements = statement.getDeclaredElements();
      if (declaredElements.length == 0) {
        return;
      }
      final PsiElement firstElement = declaredElements[0];
      if (!(firstElement instanceof final PsiLocalVariable firstVariable)) {
        return;
      }
      if (!firstVariable.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      final PsiCodeBlock containingBlock = PsiTreeUtil.getParentOfType(statement, PsiCodeBlock.class);
      if (containingBlock == null) {
        return;
      }
      for (PsiElement declaredElement : declaredElements) {
        final PsiLocalVariable variable = (PsiLocalVariable)declaredElement;
        if (isNecessaryFinal(variable, containingBlock)) {
          return;
        }
      }
      registerModifierError(PsiModifier.FINAL, firstVariable, firstVariable);
    }

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      if (!reportParameters) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      final PsiParameter[] parameters = parameterList.getParameters();
      for (final PsiParameter parameter : parameters) {
        checkParameter(method, parameter);
      }
    }

    private void checkParameter(PsiMethod method, PsiParameter parameter) {
      if (!parameter.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        registerModifierError(PsiModifier.FINAL, parameter, parameter);
      }
      else if (!onlyWarnOnAbstractMethods) {
        check(parameter);
      }
    }

    @Override
    public void visitPatternVariable(@NotNull PsiPatternVariable variable) {
      super.visitPatternVariable(variable);
      if (reportPatternVariables && variable.hasModifierProperty(PsiModifier.FINAL)) {
        registerModifierError(PsiModifier.FINAL, variable, variable);
      }
    }

    @Override
    public void visitTryStatement(@NotNull PsiTryStatement statement) {
      super.visitTryStatement(statement);
      final PsiResourceList resourceList = statement.getResourceList();
      if (resourceList != null && reportLocalVariables) {
        for (PsiResourceListElement element : resourceList) {
          if (element instanceof final PsiResourceVariable variable && variable.hasModifierProperty(PsiModifier.FINAL)) {
            registerModifierError(PsiModifier.FINAL, variable, variable);
          }
        }
      }
      if (onlyWarnOnAbstractMethods || !reportParameters) {
        return;
      }
      final PsiCatchSection[] catchSections = statement.getCatchSections();
      for (PsiCatchSection catchSection : catchSections) {
        final PsiParameter parameter = catchSection.getParameter();
        final PsiCodeBlock catchBlock = catchSection.getCatchBlock();
        if (parameter == null || catchBlock == null) {
          continue;
        }
        if (!parameter.hasModifierProperty(PsiModifier.FINAL)) {
          continue;
        }
        if (parameter.getType() instanceof PsiDisjunctionType || !isNecessaryFinal(parameter, parameter.getDeclarationScope())) {
          registerModifierError(PsiModifier.FINAL, parameter, parameter);
        }
      }
    }

    @Override
    public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
      super.visitForeachStatement(statement);
      if (onlyWarnOnAbstractMethods || !reportParameters) {
        return;
      }
      PsiParameter parameter = statement.getIterationParameter();
      if (!parameter.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      check(parameter);
    }

    private static boolean isNecessaryFinal(PsiVariable variable, PsiElement context) {
      return PsiUtil.isConstantExpression(variable.getInitializer()) ||
             !PsiUtil.isLanguageLevel8OrHigher(variable) && VariableAccessUtils.variableIsUsedInInnerClass(variable, context);
    }

    private void check(PsiParameter parameter) {
      if (!isNecessaryFinal(parameter, parameter.getDeclarationScope())) {
        registerModifierError(PsiModifier.FINAL, parameter, parameter);
      }
    }
  }
}