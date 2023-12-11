/*
 * Copyright 2003-2020 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class CachedNumberConstructorCallInspection extends BaseInspection implements CleanupLocalInspectionTool {

  static final Set<String> cachedNumberTypes = new HashSet<>();

  static {
    cachedNumberTypes.add(CommonClassNames.JAVA_LANG_LONG);
    cachedNumberTypes.add(CommonClassNames.JAVA_LANG_BYTE);
    cachedNumberTypes.add(CommonClassNames.JAVA_LANG_INTEGER);
    cachedNumberTypes.add(CommonClassNames.JAVA_LANG_SHORT);
  }

  @SuppressWarnings("PublicField")
  public boolean ignoreStringArguments = false;

  @SuppressWarnings("PublicField")
  public boolean reportOnlyWhenDeprecated = true;

  @Override
  @NotNull
  public String buildErrorString(Object... infos) { return InspectionGadgetsBundle.message(
      "cached.number.constructor.call.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreStringArguments", InspectionGadgetsBundle.message("cached.number.constructor.call.ignore.string.arguments.option")),
      checkbox("reportOnlyWhenDeprecated", InspectionGadgetsBundle.message("cached.number.constructor.call.report.only.deprecated")));
  }

  @Override
  public boolean shouldInspect(@NotNull PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file);
  }


  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new LongConstructorVisitor();
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    final PsiNewExpression expression = (PsiNewExpression)infos[0];
    final PsiJavaCodeReferenceElement classReference = expression.getClassReference();
    assert classReference != null;
    final String className = classReference.getText();
    return new CachedNumberConstructorCallFix(className);
  }

  private static class CachedNumberConstructorCallFix extends PsiUpdateModCommandQuickFix {

    private final String className;

    CachedNumberConstructorCallFix(String className) {
      this.className = className;
    }

    @Override
    @NotNull
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", className+".valueOf()");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", ".valueOf()");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      final PsiNewExpression expression = PsiTreeUtil.getParentOfType(startElement, PsiNewExpression.class, false);
      assert expression != null;
      final PsiExpressionList argList = expression.getArgumentList();
      assert argList != null;
      final PsiExpression[] args = argList.getExpressions();
      final PsiExpression arg = args[0];
      CommentTracker commentTracker = new CommentTracker();
      final String text = commentTracker.text(arg);
      PsiReplacementUtil.replaceExpression(expression, className + ".valueOf(" + text + ')', commentTracker);
    }
  }

  private class LongConstructorVisitor extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiType type = expression.getType();
      if (type == null) {
        return;
      }
      final String canonicalText = type.getCanonicalText();
      if (!cachedNumberTypes.contains(canonicalText)) {
        return;
      }
      final PsiClass aClass = ClassUtils.getContainingClass(expression);
      if (aClass != null && cachedNumberTypes.contains(aClass.getQualifiedName())) {
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
      final PsiType argumentType = argument.getType();
      if (argumentType == null || (ignoreStringArguments && argumentType.equalsToText(CommonClassNames.JAVA_LANG_STRING))) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null || (reportOnlyWhenDeprecated && !method.isDeprecated())) {
        return;
      }
      if (expression.getClassReference() == null) {
        return;
      }
      registerNewExpressionError(expression, expression);
    }
  }
}