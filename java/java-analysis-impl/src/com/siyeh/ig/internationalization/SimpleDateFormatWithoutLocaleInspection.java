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
package com.siyeh.ig.internationalization;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

public final class SimpleDateFormatWithoutLocaleInspection extends BaseInspection {

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    boolean constructorCall = (boolean)infos[0];
    return constructorCall
           ? InspectionGadgetsBundle.message("instantiating.simpledateformat.without.locale.problem.descriptor")
           : InspectionGadgetsBundle.message("instantiating.datetimeformatter.without.locale.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SimpleDateFormatWithoutLocaleVisitor();
  }

  private static class SimpleDateFormatWithoutLocaleVisitor extends BaseInspectionVisitor {

    private static final CallMatcher.Simple MATCHER =
      CallMatcher.staticCall("java.time.format.DateTimeFormatter", "ofPattern").parameterTypes(CommonClassNames.JAVA_LANG_STRING);

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      if (!ExpressionUtils.hasType(expression, "java.text.SimpleDateFormat")) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      for (PsiExpression argument : arguments) {
        if (ExpressionUtils.hasType(argument, "java.util.Locale")) {
          return;
        }
      }
      registerNewExpressionError(expression, Boolean.TRUE);
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (MATCHER.matches(expression)) {
        registerMethodCallError(expression, Boolean.FALSE);
      }
    }
  }
}