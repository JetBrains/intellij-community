// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.sourceToSink;

import com.intellij.codeInspection.restriction.StringFlowUtil;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.HashSet;
import java.util.Set;

class TaintAnalyzer {

  static @Nullable TaintValue getTaintValue(@Nullable UExpression expression) {
    if (expression == null) return null;
    return analyze(expression, new HashSet<>());
  }

  private static @Nullable TaintValue analyze(@NotNull UExpression expression, @NotNull Set<UElement> current) {
    if (current.contains(expression)) return null;
    if (expression instanceof UCallExpression) {
      UExpression returnValue = StringFlowUtil.getReturnValue((UCallExpression)expression);
      if (expression.equals(returnValue)) return null;
      if (returnValue != null && current.add(returnValue)) {
        TaintValue taintValue = analyze(returnValue, current);
        current.remove(returnValue);
        if (taintValue != TaintValue.UNKNOWN) return taintValue;
      }
    }
    if (expression instanceof UResolvable) {
      // ignore possible plus operator overload in kotlin
      if (isPlus(expression)) return TaintValue.UNTAINTED;
      return analyseResolvableExpression((UResolvable)expression, current);
    }
    return null;
  }

  private static boolean isPlus(@NotNull UExpression expression) {
    PsiElement sourcePsi = expression.getSourcePsi();
    return sourcePsi != null && UastBinaryOperator.PLUS.getText().equals(sourcePsi.getText());
  }

  private static @Nullable TaintValue analyseResolvableExpression(@NotNull UResolvable ref, @NotNull Set<UElement> current) {
    PsiElement target = ref.resolve();
    if (target instanceof PsiClass) return null;
    TaintValue taintValue;
    if (target instanceof PsiModifierListOwner) {
      PsiModifierListOwner owner = (PsiModifierListOwner)target;
      taintValue = TaintValueFactory.INSTANCE.fromModifierListOwner(owner);
      if (taintValue == TaintValue.UNKNOWN) taintValue = TaintValueFactory.of(owner);
      if (taintValue != TaintValue.UNKNOWN) return taintValue;
    }
    PsiType type = ((UExpression)ref).getExpressionType();
    if (type != null) {
      taintValue = TaintValueFactory.INSTANCE.fromAnnotationOwner(type);
      if (taintValue != TaintValue.UNKNOWN) return taintValue;
    }
    if (target instanceof PsiLocalVariable) {
      ULocalVariable uLocalVariable = UastContextKt.toUElementOfExpectedTypes(target, ULocalVariable.class);
      if (uLocalVariable == null) return null;
      if (!current.add(uLocalVariable)) return null;
      taintValue = getTaintValue((PsiLocalVariable)target, uLocalVariable, current);
      current.remove(uLocalVariable);
      return taintValue;
    }
    return TaintValue.UNKNOWN;
  }

  private static @Nullable TaintValue getTaintValue(@NotNull PsiLocalVariable target,
                                                    @NotNull ULocalVariable localVariable,
                                                    @NotNull Set<UElement> current) {
    UBlockExpression codeBlock = UastUtils.getParentOfType(localVariable, UBlockExpression.class);
    if (codeBlock == null) return null;
    UExpression initializer = localVariable.getUastInitializer();
    TaintValue taintValue = getTaintValue(initializer, current);
    if (taintValue == TaintValue.TAINTED) return taintValue;
    MyTaintValueVisitor taintValueVisitor = new MyTaintValueVisitor(target, current, taintValue);
    codeBlock.accept(taintValueVisitor);
    return taintValueVisitor.myTaintValue;
  }

  private static @Nullable TaintValue getTaintValue(@Nullable UExpression uExpression, @NotNull Set<UElement> current) {
    if (uExpression == null) return TaintValue.UNTAINTED;
    uExpression = UastUtils.skipParenthesizedExprDown(uExpression);
    ULiteralExpression literal = ObjectUtils.tryCast(uExpression, ULiteralExpression.class);
    if (literal != null) return TaintValue.UNTAINTED;
    if (uExpression instanceof UCallExpression || uExpression instanceof UReferenceExpression) {
      return analyze(uExpression, current);
    }
    return null;
  }

  private static class MyTaintValueVisitor extends AbstractUastVisitor {

    private final PsiLocalVariable myVariable;
    private final Set<UElement> myCurrent;
    private TaintValue myTaintValue;

    private MyTaintValueVisitor(@NotNull PsiLocalVariable variable,
                                @NotNull Set<UElement> current,
                                @Nullable TaintValue taintValue) {
      myVariable = variable;
      myCurrent = current;
      myTaintValue = taintValue;
    }

    @Override
    public boolean visitBlockExpression(@NotNull UBlockExpression node) {
      for (UExpression expression : node.getExpressions()) {
        expression.accept(this);
      }
      return super.visitBlockExpression(node);
    }

    @Override
    public boolean visitBinaryExpression(@NotNull UBinaryExpression node) {
      if (myTaintValue == TaintValue.TAINTED) return super.visitExpression(node);
      UastBinaryOperator operator = node.getOperator();
      if (operator != UastBinaryOperator.ASSIGN && operator != UastBinaryOperator.PLUS_ASSIGN) return super.visitBinaryExpression(node);
      UReferenceExpression lhs = ObjectUtils.tryCast(node.getLeftOperand(), UReferenceExpression.class);
      if (lhs == null || !myVariable.equals(lhs.resolve())) return super.visitBinaryExpression(node);
      UExpression rhs = node.getRightOperand();
      TaintValue taintValue = getTaintValue(rhs, myCurrent);
      if (taintValue != TaintValue.UNTAINTED) myTaintValue = taintValue;
      return super.visitBinaryExpression(node);
    }
  }
}
