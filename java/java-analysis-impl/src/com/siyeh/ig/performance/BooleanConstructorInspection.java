/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class BooleanConstructorInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  public @NotNull String getID() {
    return "BooleanConstructorCall";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("boolean.constructor.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BooleanConstructorVisitor();
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new BooleanConstructorFix();
  }

  private static class BooleanConstructorFix extends PsiUpdateModCommandQuickFix {

    private static final String TRUE = '\"' + JavaKeywords.TRUE + '\"';
    private static final String FALSE = '\"' + JavaKeywords.FALSE + '\"';

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("boolean.constructor.simplify.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      final PsiElement element = startElement.getParent();
      if (!(element instanceof PsiNewExpression expression)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiExpression argument = arguments[0];
      final String text = argument.getText();
      final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(expression);
      CommentTracker tracker = new CommentTracker();
      final @NonNls String newExpression;
      if (JavaKeywords.TRUE.equals(text) || TRUE.equalsIgnoreCase(text)) {
        newExpression = "java.lang.Boolean.TRUE";
      }
      else if (JavaKeywords.FALSE.equals(text) || FALSE.equalsIgnoreCase(text)) {
        newExpression = "java.lang.Boolean.FALSE";
      }
      else if (languageLevel.equals(LanguageLevel.JDK_1_3)) {
        newExpression = buildText(tracker.markUnchanged(argument), false);
      }
      else {
        final PsiClass booleanClass = ClassUtils.findClass(CommonClassNames.JAVA_LANG_BOOLEAN, argument);
        boolean methodFound = false;
        if (booleanClass != null) {
          final PsiMethod[] methods = booleanClass.findMethodsByName("valueOf", false);
          for (PsiMethod method : methods) {
            final PsiParameterList parameterList = method.getParameterList();
            final PsiParameter[] parameters = parameterList.getParameters();
            if (parameters.length == 0) {
              continue;
            }
            final PsiParameter parameter = parameters[0];
            final PsiType type = parameter.getType();
            if (PsiTypes.booleanType().equals(type)) {
              methodFound = true;
              break;
            }
          }
        }
        newExpression = buildText(tracker.markUnchanged(argument), methodFound);
      }
      PsiReplacementUtil.replaceExpression(expression, newExpression, tracker);
    }

    private static @NonNls String buildText(PsiExpression argument, boolean useValueOf) {
      final String text = argument.getText();
      final PsiType argumentType = argument.getType();
      if (!useValueOf && PsiTypes.booleanType().equals(argumentType)) {
        if (ParenthesesUtils.getPrecedence(argument) > ParenthesesUtils.CONDITIONAL_PRECEDENCE) {
          return text + "?java.lang.fBoolean.TRUE:java.lang.Boolean.FALSE";
        }
        else {
          return '(' + text + ")?java.lang.Boolean.TRUE:java.lang.Boolean.FALSE";
        }
      }
      else {
        return "java.lang.Boolean.valueOf(" + text + ')';
      }
    }
  }

  private static class BooleanConstructorVisitor extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiType type = expression.getType();
      if (type == null || !type.equalsToText(CommonClassNames.JAVA_LANG_BOOLEAN)) {
        return;
      }
      final PsiClass aClass = PsiUtil.getContainingClass(expression);
      if (aClass != null) {
        final String qualifiedName = aClass.getQualifiedName();
        if (CommonClassNames.JAVA_LANG_BOOLEAN.equals(qualifiedName)) {
          return;
        }
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] expressions = argumentList.getExpressions();
      if (expressions.length != 1) {
        return;
      }
      registerNewExpressionError(expression);
    }
  }
}