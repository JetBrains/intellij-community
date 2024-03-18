/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.IntroduceVariableFix;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class NestedMethodCallInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreFieldInitializations = true;
  public boolean ignoreStaticMethods = false;
  public boolean ignoreGetterCalls = false;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("m_ignoreFieldInitializations", InspectionGadgetsBundle.message("nested.method.call.ignore.option")),
      checkbox("ignoreStaticMethods", InspectionGadgetsBundle.message("ignore.calls.to.static.methods")),
      checkbox("ignoreGetterCalls", InspectionGadgetsBundle.message("ignore.calls.to.property.getters")));
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new IntroduceVariableFix(false);
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("nested.method.call.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NestedMethodCallVisitor();
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    defaultWriteSettings(node, "ignoreStaticMethods", "ignoreGetterCalls");
    writeBooleanOption(node, "ignoreStaticMethods", false);
    writeBooleanOption(node, "ignoreGetterCalls", false);
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  private class NestedMethodCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      PsiExpression outerExpression = expression;
      while (outerExpression.getParent() instanceof PsiExpression) {
        outerExpression = (PsiExpression)outerExpression.getParent();
      }
      final PsiElement parent = outerExpression.getParent();
      if (!(parent instanceof PsiExpressionList)) {
        return;
      }
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiCallExpression)) {
        return;
      }
      if (JavaPsiConstructorUtil.isConstructorCall(grandParent)) {
        //ignore nested method calls at the start of a constructor,
        //where they can't be extracted
        return;
      }
      if (m_ignoreFieldInitializations) {
        final PsiElement field = PsiTreeUtil.getParentOfType(expression, PsiField.class);
        if (field != null) {
          return;
        }
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      if (ignoreStaticMethods || ignoreGetterCalls) {
        if (ignoreStaticMethods && method.hasModifierProperty(PsiModifier.STATIC)) {
          return;
        }
        if (ignoreGetterCalls && PropertyUtil.isSimpleGetter(method)) {
          return;
        }
      }
      registerMethodCallError(expression);
    }
  }
}