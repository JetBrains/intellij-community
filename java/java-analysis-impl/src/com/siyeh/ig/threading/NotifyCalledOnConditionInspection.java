/*
 * Copyright 2003-20067 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.threading;

import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiTypes;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NotNull;

public class NotifyCalledOnConditionInspection extends BaseInspection {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "notify.called.on.condition.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NotifyCalledOnConditionVisitor();
  }

  private static class NotifyCalledOnConditionVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!MethodCallUtils.isCallToMethod(expression,
                                          "java.util.concurrent.locks.Condition", PsiTypes.voidType(),
                                          HardcodedMethodConstants.NOTIFY) &&
          !MethodCallUtils.isCallToMethod(expression,
                                          "java.util.concurrent.locks.Condition", PsiTypes.voidType(),
                                          HardcodedMethodConstants.NOTIFY_ALL)) {
        return;
      }
      registerMethodCallError(expression);
    }
  }
}