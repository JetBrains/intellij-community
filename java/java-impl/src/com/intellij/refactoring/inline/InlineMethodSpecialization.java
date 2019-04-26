// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.inline;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;

import java.util.Objects;
import java.util.function.Supplier;

public class InlineMethodSpecialization {
  private static final CallMatcher
    CLASS_METHODS = CallMatcher.exactInstanceCall(CommonClassNames.JAVA_LANG_CLASS, "getName", "getSimpleName").parameterCount(0);

  private static final CallMapper<Supplier<PsiCodeBlock>> SPECIALIZATIONS = new CallMapper<Supplier<PsiCodeBlock>>()
    .register(CLASS_METHODS, (PsiMethodCallExpression call) -> {
      PsiReferenceExpression ref = call.getMethodExpression();
      PsiExpression qualifier = ref.getQualifierExpression();
      PsiClassObjectAccessExpression receiver =
        ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(qualifier), PsiClassObjectAccessExpression.class);
      if (receiver != null) {
        PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(receiver.getOperand().getType());
        if (psiClass != null) {
          String name = "getSimpleName".equals(ref.getReferenceName()) ? psiClass.getName() : psiClass.getQualifiedName();
          if (name != null) {
            return () -> {
              PsiElementFactory factory = JavaPsiFacade.getElementFactory(call.getProject());
              return factory.createCodeBlockFromText("{return \"" + StringUtil.escapeStringCharacters(name) + "\";}", call);
            };
          }
        }
      }
      return null;
    });

  static Supplier<PsiCodeBlock> forReference(PsiReference ref) {
    if (!(ref instanceof PsiReferenceExpression)) return null;
    PsiMethodCallExpression call = ObjectUtils.tryCast(((PsiReferenceExpression)ref).getParent(), PsiMethodCallExpression.class);
    return SPECIALIZATIONS.mapFirst(call);
  }

  /**
   * Replace method body with specialized implementation for some known methods
   * @param method method to specialize
   * @param ref method call site reference
   * @return call-site-specific specialization of method body; or original method if there's no specialization for given method
   */
  static PsiMethod specialize(PsiMethod method, PsiReference ref) {
    Supplier<PsiCodeBlock> specialization = forReference(ref);
    if (specialization == null) return method;
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(method.getProject());
    String parameters = method.getParameterList().getText();
    PsiType returnType = method.getReturnType();
    String type = returnType == null ? "" : returnType.getCanonicalText(true);
    PsiMethod copy = factory.createMethodFromText(type + " " + method.getName() + parameters + " {}", method);
    Objects.requireNonNull(copy.getBody()).replace(specialization.get());
    return copy;
  }
}
