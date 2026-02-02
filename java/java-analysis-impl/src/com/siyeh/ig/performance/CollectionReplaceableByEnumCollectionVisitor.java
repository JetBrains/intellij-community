// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.performance;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.util.ObjectUtils.tryCast;

/**
 * @author Bas Leijdekkers
 */
abstract class CollectionReplaceableByEnumCollectionVisitor extends BaseInspectionVisitor {

  @Override
  public final void visitNewExpression(@NotNull PsiNewExpression expression) {
    super.visitNewExpression(expression);
    final PsiType type = expression.getType();
    if (!(type instanceof PsiClassType classType)) {
      return;
    }
    final PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, false);
    if (!(expectedType instanceof PsiClassType)) {
      return;
    }
    if (!classType.hasParameters()) {
      classType = (PsiClassType)expectedType;
    }
    final PsiType[] typeArguments = classType.getParameters();
    if (typeArguments.length == 0) {
      return;
    }
    final PsiType argumentType = typeArguments[0];
    if (!(argumentType instanceof PsiClassType argumentClassType)) {
      return;
    }
    if (!TypeUtils.expressionHasTypeOrSubtype(expression, getBaseCollectionName())) {
      return;
    }
    if (TypeUtils.expressionHasTypeOrSubtype(expression, getReplacementCollectionName()) ||
        TypeUtils.expressionHasTypeOrSubtype(expression, getUnreplaceableCollectionNames())) {
      return;
    }
    final PsiClass argumentClass = argumentClassType.resolve();
    if (argumentClass == null || !argumentClass.isEnum()) {
      return;
    }
    final PsiClass aClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
    if (argumentClass.equals(aClass)) {
      final PsiMember member = PsiTreeUtil.getParentOfType(expression, PsiMember.class);
      if (member != null && !member.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
    }
    final PsiExpressionList argumentList = expression.getArgumentList();
    if (argumentList != null) {
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length > 0 && TypeUtils.expressionHasTypeOrSubtype(arguments[0], "java.util.Comparator")) {
        return;
      }
    }
    PsiClassType replacementCollectionType = TypeUtils.getType(getReplacementCollectionName(), expression);
    if (!expectedType.isAssignableFrom(replacementCollectionType) && !isReplaceableType((PsiClassType)expectedType)) {
      return;
    }
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    PsiLocalVariable localVariable = tryCast(parent, PsiLocalVariable.class);
    if (localVariable != null) {
      PsiClass localVariableClass = PsiUtil.resolveClassInClassTypeOnly(localVariable.getType());
      if (localVariableClass != null && getBaseCollectionName().equals(localVariableClass.getQualifiedName())) {
        registerNewExpressionError(expression, localVariable);
      }
      else {
        registerNewExpressionError(expression);
      }
    }
    else {
      registerNewExpressionError(expression);
    }
  }

  private boolean isReplaceableType(PsiClassType classType) {
    return getReplaceableCollectionNames().stream().anyMatch(s -> PsiTypesUtil.classNameEquals(classType, s));
  }

  protected abstract @NotNull List<String> getUnreplaceableCollectionNames();

  protected abstract @NotNull List<String> getReplaceableCollectionNames();

  protected abstract @NotNull String getReplacementCollectionName();

  protected abstract @NotNull String getBaseCollectionName();

  static PsiType[] extractParameterType(@NotNull PsiLocalVariable localVariable, int expectedParameterCount) {
    PsiExpression initializer = PsiUtil.skipParenthesizedExprDown(localVariable.getInitializer());
    PsiNewExpression newExpression = tryCast(initializer, PsiNewExpression.class);
    if (newExpression == null) return null;
    PsiExpressionList argumentList = newExpression.getArgumentList();
    if (argumentList == null || !argumentList.isEmpty()) return null;
    PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
    if (classReference == null) return null;
    PsiReferenceParameterList parameterList = classReference.getParameterList();
    if (parameterList == null) return null;
    PsiClassType classType = tryCast(newExpression.getType(), PsiClassType.class);
    if (classType == null) return null;
    if (classType.getParameterCount() != expectedParameterCount) return null;
    return classType.getParameters();
  }
}
