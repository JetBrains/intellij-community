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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.InlineGetterSetterCallFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class CallToSimpleGetterInClassInspection extends BaseInspection implements CleanupLocalInspectionTool {
  @SuppressWarnings("UnusedDeclaration")
  public boolean ignoreGetterCallsOnOtherObjects = false;
  @SuppressWarnings("UnusedDeclaration")
  public boolean onlyReportPrivateGetter = false;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreGetterCallsOnOtherObjects", InspectionGadgetsBundle.message("call.to.simple.getter.in.class.ignore.option")),
      checkbox("onlyReportPrivateGetter", InspectionGadgetsBundle.message("call.to.private.simple.getter.in.class.option")));
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new InlineGetterSetterCallFix(true);
  }

  @Override
  public boolean runForWholeFile() {
    // Changes in another method (making getter more complicated) may affect 
    // the inspection result at call sites
    return true;
  }

  @Override
  @NotNull
  public String getID() {
    return "CallToSimpleGetterFromWithinClass";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("call.to.simple.getter.in.class.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CallToSimpleGetterInClassVisitor();
  }

  private class CallToSimpleGetterInClassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
      super.visitMethodCallExpression(call);

      final PsiReferenceExpression methodExpression = call.getMethodExpression();
      final String referenceName = methodExpression.getReferenceName();
      if (referenceName == null ||
          PropertyUtilBase.getMethodNameGetterFlavour(referenceName) == PropertyUtilBase.GetterFlavour.NOT_A_GETTER) {
        return;
      }

      final PsiElement parent = call.getParent();
      if (parent instanceof PsiExpressionStatement) {
        // inlining a top-level getter call would break code
        return;
      }
      final PsiClass containingClass = ClassUtils.getContainingClass(call);
      if (containingClass == null) {
        return;
      }
      final PsiMethod method = call.resolveMethod();
      if (method == null) {
        return;
      }
      if (!containingClass.equals(method.getContainingClass())) {
        return;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier != null && !(qualifier instanceof PsiThisExpression)) {
        if (ignoreGetterCallsOnOtherObjects) {
          return;
        }
        final PsiClass qualifierClass = PsiUtil.resolveClassInClassTypeOnly(qualifier.getType());
        if (!containingClass.equals(qualifierClass)) {
          return;
        }
      }
      final PsiField field = PropertyUtil.getFieldOfGetter(method);
      if (field == null) {
        return;
      }
      final PsiMember member = PsiTreeUtil.getParentOfType(call, PsiMember.class);
      if (member instanceof PsiField && !(member instanceof PsiEnumConstant) && member.getTextOffset() < field.getTextOffset()) {
        return;
      }
      if (onlyReportPrivateGetter && !method.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      final PsiMethod overridingMethod = OverridingMethodsSearch.search(method).findFirst();
      if (overridingMethod != null) {
        return;
      }
      registerMethodCallError(call);
    }
  }
}