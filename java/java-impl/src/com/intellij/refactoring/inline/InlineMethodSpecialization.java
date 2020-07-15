// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.inline;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Contract;

import java.util.Objects;
import java.util.function.Supplier;

import static com.intellij.util.ObjectUtils.tryCast;

public final class InlineMethodSpecialization {
  private static final CallMatcher
    CLASS_METHODS = CallMatcher.exactInstanceCall(CommonClassNames.JAVA_LANG_CLASS, "getName", "getSimpleName").parameterCount(0);
  private static final CallMatcher
    ENUM_NAME = CallMatcher.exactInstanceCall(CommonClassNames.JAVA_LANG_ENUM, "name").parameterCount(0);

  private static final CallMapper<Supplier<PsiCodeBlock>> SPECIALIZATIONS = new CallMapper<Supplier<PsiCodeBlock>>()
    .register(CLASS_METHODS, (PsiMethodCallExpression call) -> {
      PsiReferenceExpression ref = call.getMethodExpression();
      PsiExpression qualifier = ref.getQualifierExpression();
      PsiClassObjectAccessExpression receiver =
        tryCast(PsiUtil.skipParenthesizedExprDown(qualifier), PsiClassObjectAccessExpression.class);
      if (receiver == null) return null;
      PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(receiver.getOperand().getType());
      if (psiClass == null) return null;
      String name = "getSimpleName".equals(ref.getReferenceName()) ? psiClass.getName() : psiClass.getQualifiedName();
      return getStringSupplier(call, name);
    })
    .register(ENUM_NAME, (PsiMethodCallExpression call) -> {
      PsiReferenceExpression qualifier =
        tryCast(PsiUtil.skipParenthesizedExprDown(call.getMethodExpression().getQualifierExpression()), PsiReferenceExpression.class);
      if (qualifier == null) return null;
      PsiEnumConstant enumConstant = tryCast(qualifier.resolve(), PsiEnumConstant.class);
      if (enumConstant == null) return null;
      return getStringSupplier(call, enumConstant.getName());
    })
    .register(CallMatcher.enumValueOf(), (PsiMethodCallExpression call) -> {
      PsiReferenceExpression qualifier =
        tryCast(PsiUtil.skipParenthesizedExprDown(call.getMethodExpression().getQualifierExpression()), PsiReferenceExpression.class);
      if (qualifier == null) return null;
      PsiClass cls = tryCast(qualifier.resolve(), PsiClass.class);
      if (cls == null || !cls.isEnum()) return null;
      PsiLiteralExpression literal = ExpressionUtils.getLiteral(call.getArgumentList().getExpressions()[0]);
      if (literal == null) return null;
      String name = tryCast(literal.getValue(), String.class);
      if (name == null) return null;
      PsiEnumConstant constant = tryCast(cls.findFieldByName(name, false), PsiEnumConstant.class);
      if (constant == null) return null;
      return () -> {
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(call.getProject());
        return factory.createCodeBlockFromText("{return " + qualifier.getText() + "." + constant.getName() + ";}", call);
      };
    });

  @Contract(value = "_, null -> null", pure = true)
  private static Supplier<PsiCodeBlock> getStringSupplier(PsiElement context, String name) {
    if (name == null) return null;
    return () -> {
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.getProject());
      return factory.createCodeBlockFromText("{return \"" + StringUtil.escapeStringCharacters(name) + "\";}", context);
    };
  }

  static Supplier<PsiCodeBlock> forReference(PsiReference ref) {
    if (!(ref instanceof PsiReferenceExpression)) return null;
    PsiMethodCallExpression call = tryCast(((PsiReferenceExpression)ref).getParent(), PsiMethodCallExpression.class);
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
