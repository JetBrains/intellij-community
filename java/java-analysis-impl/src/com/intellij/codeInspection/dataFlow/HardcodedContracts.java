/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInspection.dataFlow;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.intellij.codeInspection.dataFlow.MethodContract.ValueConstraint.*;
import static com.intellij.codeInspection.dataFlow.MethodContract.createConstraintArray;

/**
 * @author peter
 */
public class HardcodedContracts {
  public static List<MethodContract> getHardcodedContracts(@NotNull PsiMethod method, @Nullable PsiMethodCallExpression call) {
    PsiClass owner = method.getContainingClass();
    if (owner == null) return Collections.emptyList();

    final int paramCount = method.getParameterList().getParametersCount();
    String className = owner.getQualifiedName();
    if (className == null) return Collections.emptyList();

    String methodName = method.getName();

    if ("java.lang.System".equals(className)) {
      if ("exit".equals(methodName)) {
        return Collections.singletonList(new MethodContract(createConstraintArray(paramCount), THROW_EXCEPTION));
      }
    }
    else if ("com.google.common.base.Preconditions".equals(className)) {
      if ("checkNotNull".equals(methodName) && paramCount > 0) {
        MethodContract.ValueConstraint[] constraints = createConstraintArray(paramCount);
        constraints[0] = NULL_VALUE;
        return Collections.singletonList(new MethodContract(constraints, THROW_EXCEPTION));
      }
      if (("checkArgument".equals(methodName) || "checkState".equals(methodName)) && paramCount > 0) {
        MethodContract.ValueConstraint[] constraints = createConstraintArray(paramCount);
        constraints[0] = FALSE_VALUE;
        return Collections.singletonList(new MethodContract(constraints, THROW_EXCEPTION));
      }
    }
    else if ("java.util.Objects".equals(className)) {
      if ("requireNonNull".equals(methodName) && paramCount > 0) {
        MethodContract.ValueConstraint[] constraints = createConstraintArray(paramCount);
        constraints[0] = NULL_VALUE;
        return Collections.singletonList(new MethodContract(constraints, THROW_EXCEPTION));
      }
    }
    else if ("org.apache.commons.lang.Validate".equals(className) || 
             "org.apache.commons.lang3.Validate".equals(className) || 
             "org.springframework.util.Assert".equals(className)) {
      if ("notNull".equals(methodName) && paramCount > 0) {
        MethodContract.ValueConstraint[] constraints = createConstraintArray(paramCount);
        constraints[0] = NULL_VALUE;
        return Collections.singletonList(new MethodContract(constraints, THROW_EXCEPTION));
      }
    }
    else if ("junit.framework.Assert".equals(className) ||
             "org.junit.Assert".equals(className) ||
             "junit.framework.TestCase".equals(className) ||
             "com.google.common.truth.Truth".equals(className) ||
             className.startsWith("org.assertj.core.api.") ||
             "org.testng.Assert".equals(className) ||
             "org.testng.AssertJUnit".equals(className)) {
      return handleTestFrameworks(paramCount, className, methodName, call);
    }

    return Collections.emptyList();
  }

  private static boolean isNotNullMatcher(PsiExpression expr) {
    if (expr instanceof PsiMethodCallExpression) {
      String calledName = ((PsiMethodCallExpression)expr).getMethodExpression().getReferenceName();
      if ("notNullValue".equals(calledName)) {
        return true;
      }
      if ("not".equals(calledName)) {
        PsiExpression[] notArgs = ((PsiMethodCallExpression)expr).getArgumentList().getExpressions();
        if (notArgs.length == 1 &&
            notArgs[0] instanceof PsiMethodCallExpression &&
            "equalTo".equals(((PsiMethodCallExpression)notArgs[0]).getMethodExpression().getReferenceName())) {
          PsiExpression[] equalArgs = ((PsiMethodCallExpression)notArgs[0]).getArgumentList().getExpressions();
          if (equalArgs.length == 1 && ExpressionUtils.isNullLiteral(equalArgs[0])) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static List<MethodContract> handleTestFrameworks(int paramCount, String className, String methodName,
                                                           @Nullable PsiMethodCallExpression call) {
    if ("assertThat".equals(methodName) && call != null) {
      PsiExpression[] args = call.getArgumentList().getExpressions();
      if (args.length == paramCount) {
        for (int i = 1; i < args.length; i++) {
          if (isNotNullMatcher(args[i])) {
            MethodContract.ValueConstraint[] constraints = createConstraintArray(args.length);
            constraints[i - 1] = NULL_VALUE;
            return Collections.singletonList(new MethodContract(constraints, THROW_EXCEPTION));
          }
        }
        if (args.length == 1) {
          final PsiElement parent = call.getParent();
          if (parent instanceof PsiReferenceExpression && 
              "isNotNull".equals(((PsiReferenceExpression)parent).getReferenceName()) && 
              parent.getParent() instanceof PsiMethodCallExpression) {
            return Collections.singletonList(new MethodContract(new MethodContract.ValueConstraint[]{NULL_VALUE}, THROW_EXCEPTION));
          }
        }
      }
      return Collections.emptyList();
    }

    if (!"junit.framework.Assert".equals(className) &&
        !"junit.framework.TestCase".equals(className) &&
        !"org.junit.Assert".equals(className) &&
        !"org.testng.Assert".equals(className) &&
        !"org.testng.AssertJUnit".equals(className)) {
      return Collections.emptyList();
    }

    boolean testng = className.startsWith("org.testng.");
    if ("fail".equals(methodName)) {
      return Collections.singletonList(new MethodContract(createConstraintArray(paramCount), THROW_EXCEPTION));
    }

    int checkedParam = testng ? 0 : paramCount - 1;
    MethodContract.ValueConstraint[] constraints = createConstraintArray(paramCount);
    if ("assertTrue".equals(methodName)) {
      constraints[checkedParam] = FALSE_VALUE;
      return Collections.singletonList(new MethodContract(constraints, THROW_EXCEPTION));
    }
    if ("assertFalse".equals(methodName)) {
      constraints[checkedParam] = TRUE_VALUE;
      return Collections.singletonList(new MethodContract(constraints, THROW_EXCEPTION));
    }
    if ("assertNull".equals(methodName)) {
      constraints[checkedParam] = NOT_NULL_VALUE;
      return Collections.singletonList(new MethodContract(constraints, THROW_EXCEPTION));
    }
    if ("assertNotNull".equals(methodName)) {
      constraints[checkedParam] = NULL_VALUE;
      return Collections.singletonList(new MethodContract(constraints, THROW_EXCEPTION));
    }
    return Collections.emptyList();
  }

  public static boolean isHardcodedPure(PsiMethod method) {
    String qName = PsiUtil.getMemberQualifiedName(method);
    if ("java.lang.System.exit".equals(qName)) {
      return false;
    }

    if ("java.util.Objects.requireNonNull".equals(qName)) {
      PsiParameter[] parameters = method.getParameterList().getParameters();
      if (parameters.length == 2 && parameters[1].getType().getCanonicalText().contains("Supplier")) {
        return false;
      }
    }

    return true;
  }

  public static boolean hasHardcodedContracts(@Nullable PsiElement element) {
    if (element instanceof PsiMethod) {
      return !getHardcodedContracts((PsiMethod)element, null).isEmpty();
    }

    if (element instanceof PsiParameter) {
      PsiElement parent = element.getParent();
      return parent != null && hasHardcodedContracts(parent.getParent());
    }

    return false;
  }
}
