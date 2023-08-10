/*
 * Copyright 2006-2010 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.serialization;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;

public class NonSerializableObjectBoundToHttpSessionInspection
  extends BaseInspection {

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "non.serializable.object.bound.to.http.session.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NonSerializableObjectBoundToHttpSessionVisitor();
  }

  private static class NonSerializableObjectBoundToHttpSessionVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression methodCallExpression) {
      super.visitMethodCallExpression(methodCallExpression);
      if (!MethodCallUtils.isSimpleCallToMethod(methodCallExpression,
                                                "javax.servlet.http.HttpSession", PsiTypes.voidType(),
                                                "putValue", CommonClassNames.JAVA_LANG_STRING,
                                                CommonClassNames.JAVA_LANG_OBJECT) &&
          !MethodCallUtils.isSimpleCallToMethod(methodCallExpression,
                                                "javax.servlet.http.HttpSession", PsiTypes.voidType(),
                                                "setAttribute", CommonClassNames.JAVA_LANG_STRING,
                                                CommonClassNames.JAVA_LANG_OBJECT)) {
        return;
      }
      final PsiExpressionList argumentList =
        methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 2) {
        return;
      }
      final PsiExpression argument = arguments[1];
      final PsiType argumentType = argument.getType();
      if (argumentType == null) {
        return;
      }
      if (SerializationUtils.isProbablySerializable(argumentType)) {
        return;
      }
      registerError(argument);
    }
  }
}