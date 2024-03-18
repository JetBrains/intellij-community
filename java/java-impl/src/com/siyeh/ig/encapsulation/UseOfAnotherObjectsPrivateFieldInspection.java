/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.encapsulation;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.EncapsulateVariableFix;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class UseOfAnotherObjectsPrivateFieldInspection
  extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreSameClass = false;
  @SuppressWarnings({"PublicField"})
  public boolean ignoreInnerClasses = false;
  @SuppressWarnings({"PublicField"})
  public boolean ignoreEquals = false;

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiField field = (PsiField)infos[0];
    return new EncapsulateVariableFix(field.getName());
  }

  @Override
  @NotNull
  public String getID() {
    return "AccessingNonPublicFieldOfAnotherObject";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "accessing.non.public.field.of.another.object.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreSameClass", InspectionGadgetsBundle.message("ignore.accesses.from.the.same.class"),
               checkbox("ignoreInnerClasses", InspectionGadgetsBundle.message("inspection.use.of.private.field.inner.classes.option"))),
      checkbox("ignoreEquals", InspectionGadgetsBundle.message("ignore.accesses.from.equals.method")));
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    // this inspection uses old style serialization, make sure newly introduced setting field does not change profile.
    defaultWriteSettings(node, "ignoreInnerClasses");
    writeBooleanOption(node, "ignoreInnerClasses", false);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UseOfAnotherObjectsPrivateFieldVisitor();
  }

  private class UseOfAnotherObjectsPrivateFieldVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      final PsiExpression qualifier = expression.getQualifierExpression();
      if (qualifier == null || qualifier instanceof PsiThisExpression) {
        return;
      }
      if (ignoreEquals) {
        final PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
        if (MethodUtils.isEquals(method)) {
          return;
        }
      }
      final PsiElement referent = expression.resolve();
      if (!(referent instanceof PsiField field)) {
        return;
      }
      if (ignoreSameClass) {
        final PsiClass parent = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
        final PsiClass containingClass = field.getContainingClass();
        if (parent != null && (parent.equals(containingClass) ||
                               ignoreInnerClasses && PsiTreeUtil.isAncestor(containingClass, parent, true))) {
          return;
        }
      }
      if (!field.hasModifierProperty(PsiModifier.PRIVATE) &&
          !field.hasModifierProperty(PsiModifier.PROTECTED)) {
        return;
      }
      if (field.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      final PsiElement fieldNameElement = expression.getReferenceNameElement();
      if (fieldNameElement == null) {
        return;
      }
      registerError(fieldNameElement, field);
    }
  }
}
