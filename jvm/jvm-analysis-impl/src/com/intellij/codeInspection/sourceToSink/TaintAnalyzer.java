// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.sourceToSink;

import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

class TaintAnalyzer {

  private final Set<UElement> myVisited = new HashSet<>();
  private final List<PsiModifierListOwner> myNonMarkedElements = new ArrayList<>();

  @NotNull TaintValue analyze(@NotNull UExpression expression) {
    if (myVisited.contains(expression)) return TaintValue.UNTAINTED;
    UResolvable uResolvable = ObjectUtils.tryCast(expression, UResolvable.class);
    // ignore possible plus operator overload in kotlin
    if (uResolvable == null || isPlus(expression)) return TaintValue.UNTAINTED;
    return analyseResolvableExpression(uResolvable);
  }

  private @NotNull TaintValue analyseResolvableExpression(@NotNull UResolvable uResolvable) {
    PsiElement target = uResolvable.resolve();
    TaintValue taintValue = fromAnnotation(uResolvable, target);
    if (taintValue != TaintValue.UNKNOWN) return taintValue;
    taintValue = fromLocalVar(target);
    if (taintValue != null) return taintValue;
    if (!(target instanceof PsiModifierListOwner)) return TaintValue.UNTAINTED;
    myNonMarkedElements.add((PsiModifierListOwner)target);
    return TaintValue.UNKNOWN;
  }

  private @Nullable TaintValue fromLocalVar(@Nullable PsiElement target) {
    PsiLocalVariable variable = ObjectUtils.tryCast(target, PsiLocalVariable.class);
    if (variable == null) return null;
    ULocalVariable uVariable = UastContextKt.toUElement(variable, ULocalVariable.class);
    if (uVariable == null) return null;
    UBlockExpression codeBlock = UastUtils.getParentOfType(uVariable, UBlockExpression.class);
    if (codeBlock == null) return TaintValue.UNKNOWN;
    if (!myVisited.add(uVariable)) return TaintValue.UNTAINTED;
    UExpression uInitializer = uVariable.getUastInitializer();
    TaintValue taintValue = fromExpression(uInitializer, true);
    if (taintValue == TaintValue.TAINTED) return taintValue;
    VariableAnalyzer taintValueVisitor = new VariableAnalyzer(variable, taintValue);
    codeBlock.accept(taintValueVisitor);
    return taintValueVisitor.myTaintValue;
  }

  private @NotNull TaintValue fromExpression(@Nullable UExpression uExpression, boolean goDeep) {
    if (uExpression == null) return TaintValue.UNTAINTED;
    uExpression = UastUtils.skipParenthesizedExprDown(uExpression);
    if (uExpression == null || uExpression instanceof ULiteralExpression) return TaintValue.UNTAINTED;
    if (uExpression instanceof UResolvable) return analyze(uExpression);
    UPolyadicExpression uConcatenation = getConcatenation(uExpression);
    if (uConcatenation != null) return joining(StreamEx.of(uConcatenation.getOperands()), true);
    if (!goDeep) return TaintValue.UNTAINTED;
    PsiExpression javaPsi = ObjectUtils.tryCast(uExpression.getJavaPsi(), PsiExpression.class);
    if (javaPsi == null) return TaintValue.UNTAINTED;
    Stream<UExpression> expressions = ExpressionUtils.nonStructuralChildren(javaPsi).map(e -> UastContextKt.toUElement(e, UExpression.class));
    return joining(expressions, false);
  }

  private @NotNull TaintValue joining(@NotNull Stream<UExpression> expressions, boolean goDeep) {
    return expressions.map(e -> fromExpression(e, goDeep)).collect(TaintValue.joining());
  }

  List<PsiModifierListOwner> getNonMarkedElements() {
    return myNonMarkedElements;
  }

  private static @NotNull TaintValue fromAnnotation(@NotNull UResolvable uResolvable, @Nullable PsiElement target) {
    if (target instanceof PsiClass) return TaintValue.UNTAINTED;
    if (target instanceof PsiModifierListOwner) {
      PsiModifierListOwner owner = (PsiModifierListOwner)target;
      TaintValue taintValue = TaintValueFactory.INSTANCE.fromModifierListOwner(owner);
      if (taintValue == TaintValue.UNKNOWN) taintValue = TaintValueFactory.of(owner);
      if (taintValue != TaintValue.UNKNOWN) return taintValue;
    }
    PsiType type = ((UExpression)uResolvable).getExpressionType();
    if (type == null) return TaintValue.UNKNOWN;
    return TaintValueFactory.INSTANCE.fromAnnotationOwner(type);
  }

  private static @Nullable UPolyadicExpression getConcatenation(UExpression uExpression) {
    UPolyadicExpression uPolyadic = ObjectUtils.tryCast(uExpression, UPolyadicExpression.class);
    if (uPolyadic == null) return null;
    UastBinaryOperator uOperator = uPolyadic.getOperator();
    return uOperator == UastBinaryOperator.PLUS || uOperator == UastBinaryOperator.PLUS_ASSIGN ? uPolyadic : null;
  }

  private static boolean isPlus(@NotNull UExpression expression) {
    PsiElement sourcePsi = expression.getSourcePsi();
    return sourcePsi != null && UastBinaryOperator.PLUS.getText().equals(sourcePsi.getText());
  }

  private class VariableAnalyzer extends AbstractUastVisitor {

    private final PsiLocalVariable myVariable;
    private TaintValue myTaintValue;

    private VariableAnalyzer(@NotNull PsiLocalVariable variable, @Nullable TaintValue taintValue) {
      myVariable = variable;
      myTaintValue = taintValue;
    }

    @Override
    public boolean visitBlockExpression(@NotNull UBlockExpression node) {
      for (UExpression expression : node.getExpressions()) {
        expression.accept(this);
        if (myTaintValue == TaintValue.TAINTED) return true;
      }
      return true;
    }

    @Override
    public boolean visitBinaryExpression(@NotNull UBinaryExpression node) {
      UastBinaryOperator operator = node.getOperator();
      if (operator != UastBinaryOperator.ASSIGN && operator != UastBinaryOperator.PLUS_ASSIGN) {
        return super.visitBinaryExpression(node);
      }
      UReferenceExpression lhs = ObjectUtils.tryCast(node.getLeftOperand(), UReferenceExpression.class);
      if (lhs == null || !myVariable.equals(lhs.resolve())) return super.visitBinaryExpression(node);
      UExpression rhs = node.getRightOperand();
      TaintValue taintValue = fromExpression(rhs, true);
      myTaintValue = myTaintValue.join(taintValue);
      return super.visitBinaryExpression(node);
    }
  }
}
