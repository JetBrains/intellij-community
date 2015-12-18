/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.refactoring.typeMigration.rules.guava;

import com.intellij.psi.*;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Batkovich
 */
public class FunctionalInterfaceTypeConversionDescriptor extends TypeConversionDescriptor {
  @NotNull private final String myMethodName;
  @NotNull private final String myTargetMethodName;

  public FunctionalInterfaceTypeConversionDescriptor(@NotNull String methodName, @NotNull String targetMethodName) {
    super(null, null);
    myMethodName = methodName;
    myTargetMethodName = targetMethodName;
  }

  @Override
  public PsiExpression replace(PsiExpression expression) {
    if (expression instanceof PsiMethodReferenceExpression) {
      setAsMethodReference((PsiMethodReferenceExpression)expression);
    } else {
      setAsMethodCall();
    }
    return super.replace(expression);
  }

  private void setAsMethodReference(PsiMethodReferenceExpression methodReference) {
    setStringToReplace("$qualifier$::" + myMethodName);
    if (methodReference.getParent() instanceof PsiExpressionList &&
        methodReference.getParent().getParent() instanceof PsiMethodCallExpression &&
        isPredicates((PsiMethodCallExpression)methodReference.getParent().getParent())) {
      setReplaceByString("$qualifier$::" + myTargetMethodName);
    }
    setReplaceByString("$qualifier$");
  }

  private void setAsMethodCall() {
    setStringToReplace("$qualifier$." + myMethodName + "($param$)");
    setReplaceByString("$qualifier$." + myTargetMethodName + "($param$)");
  }

  public static boolean isPredicates(PsiMethodCallExpression expression) {
    final String methodName = expression.getMethodExpression().getReferenceName();
    if (GuavaPredicateConversionRule.PREDIACTES_NOT.equals(methodName) ||
        GuavaPredicateConversionRule.PREDICATES_AND_OR.contains(methodName)) {
      final PsiMethod method = expression.resolveMethod();
      if (method == null) return false;
      final PsiClass aClass = method.getContainingClass();
      if (aClass != null && GuavaPredicateConversionRule.GUAVA_PREDICATES_UTILITY.equals(aClass.getQualifiedName())) {
        return true;
      }
    }
    return false;
  }
}
