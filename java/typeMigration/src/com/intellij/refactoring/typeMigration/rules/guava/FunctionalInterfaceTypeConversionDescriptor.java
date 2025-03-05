// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeMigration.rules.guava;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.refactoring.typeMigration.TypeEvaluator;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Batkovich
 */
public class FunctionalInterfaceTypeConversionDescriptor extends TypeConversionDescriptor {
  private final @NotNull String myMethodName;
  private final @NotNull String myTargetMethodName;
  private final @NotNull String myTargetClassQName;

  FunctionalInterfaceTypeConversionDescriptor(@NotNull String methodName,
                                              @NotNull String targetMethodName,
                                              @NotNull String targetClassQName) {
    super(null, null);
    myMethodName = methodName;
    myTargetMethodName = targetMethodName;
    myTargetClassQName = targetClassQName;
  }

  @Override
  public PsiExpression replace(PsiExpression expression, @NotNull TypeEvaluator evaluator) {
    if (expression.getParent() instanceof PsiMethodReferenceExpression) {
      expression = (PsiExpression)expression.getParent();
    }
    if (expression instanceof PsiMethodReferenceExpression) {
      expression = setupAsMethodReference(expression);
    }
    else if (expression instanceof PsiReferenceExpression) {
      setupAsReference();
    }
    else {
      setupAsMethodCall();
    }
    final PsiExpression converted = super.replace(expression, evaluator);
    final PsiElement parent = converted.getParent();
    if (parent instanceof PsiParenthesizedExpression) {
      if (!ParenthesesUtils.areParenthesesNeeded((PsiParenthesizedExpression)parent, true)) {
        return (PsiExpression)parent.replace(converted);
      }
    }
    return converted;
  }

  private void setupAsReference() {
    setStringToReplace("$ref$");
    setReplaceByString("$ref$::" + myTargetMethodName);
  }

  private PsiExpression setupAsMethodReference(PsiExpression methodReferenceExpression) {
    final PsiElement parent = methodReferenceExpression.getParent();
    if (parent instanceof PsiTypeCastExpression) {
      final PsiTypeElement typeElement = ((PsiTypeCastExpression)parent).getCastType();
      if (typeElement != null) {
        final PsiClass resolvedClass = PsiTypesUtil.getPsiClass(typeElement.getType());
        if (resolvedClass != null && myTargetClassQName.equals(resolvedClass.getQualifiedName())) {
          methodReferenceExpression = (PsiExpression)parent.replace(methodReferenceExpression);
        }
      }
    }
    setStringToReplace("$qualifier$::" + myMethodName);
    setReplaceByString("$qualifier$");
    return methodReferenceExpression;
  }

  private void setupAsMethodCall() {
    setStringToReplace("$qualifier$." + myMethodName + "($param$)");
    setReplaceByString("$qualifier$." + myTargetMethodName + "($param$)");
  }
}
