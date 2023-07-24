// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public final class PsiTemplateExpressionImpl extends ExpressionPsiElement implements PsiTemplateExpression {

  public PsiTemplateExpressionImpl() {
    super(JavaElementType.TEMPLATE_EXPRESSION);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitTemplateExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public @Nullable PsiExpression getProcessor() {
    final PsiElement child = getFirstChild();
    return child instanceof PsiExpression ? (PsiExpression)child : null;
  }

  @Override
  public @NotNull ArgumentType getArgumentType() {
    final PsiElement lastChild = getLastChild();
    if (lastChild instanceof PsiTemplate) {
      return ArgumentType.TEMPLATE;
    }
    else if (!(lastChild instanceof PsiLiteralExpression)) {
      throw new AssertionError("literal expression expected, got " + lastChild.getClass());
    }
    return ((PsiLiteralExpression)lastChild).isTextBlock() ? ArgumentType.TEXT_BLOCK : ArgumentType.STRING_LITERAL;
  }

  @Override
  public @Nullable PsiTemplate getTemplate() {
    final PsiElement lastChild = getLastChild();
    return lastChild instanceof PsiTemplate ? (PsiTemplate)lastChild : null;
  }

  @Override
  public @Nullable PsiLiteralExpression getLiteralExpression() {
    final PsiElement lastChild = getLastChild();
    return lastChild instanceof PsiLiteralExpression ? (PsiLiteralExpression)lastChild : null;
  }

  @Override
  public @Nullable PsiType getType() {
    final PsiExpression processor = getProcessor();
    if (processor ==  null) return null;
    final PsiType type = processor.getType();
    if (type == null) return null;
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(getProject());
    final PsiClassType processorType = factory.createTypeByFQClassName("java.lang.StringTemplate.Processor", processor.getResolveScope());
    if (!TypeConversionUtil.isAssignable(processorType, type)) return null;
    for (PsiClassType classType : PsiTypesUtil.getClassTypeComponents(type)) {
      final PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
      final PsiClass aClass = resolveResult.getElement();
      if (aClass == null) continue;

      final PsiClass processorClass = processorType.resolve();
      if (processorClass == null) continue;
      final PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(processorClass, aClass, resolveResult.getSubstitutor());
      if (substitutor == null) continue;
      final PsiMethod[] methods = processorClass.findMethodsByName("process", false);
      if (methods.length != 1) continue;
      return substitutor.substitute(methods[0].getReturnType());
    }

    return null;
  }

  @Override
  public String toString() {
    return "PsiTemplateExpression";
  }
}