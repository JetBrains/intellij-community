// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.sourceToSink;

import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.MoreCollectors;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.*;
import java.util.stream.Collector;

public class TaintAnalyzer {

  private final Set<PsiElement> myVisited = new HashSet<>();
  private final List<NonMarkedElement> myNonMarkedElements = new ArrayList<>();

  public @NotNull TaintValue analyze(@NotNull UExpression expression) {
    UResolvable uResolvable = ObjectUtils.tryCast(expression, UResolvable.class);
    // ignore possible plus operator overload in kotlin
    if (uResolvable == null || isPlus(expression)) return TaintValue.UNTAINTED;
    PsiElement ref = expression.getSourcePsi();
    if (ref == null) return TaintValue.UNTAINTED;
    PsiElement target = uResolvable.resolve();
    return fromElement(target, ref, false);
  }

  public @NotNull TaintValue fromElement(@Nullable PsiElement target, @NotNull PsiElement ref, boolean processRecursively) {
    if (target == null || !myVisited.add(target)) return TaintValue.UNTAINTED;
    TaintValue taintValue = fromAnnotation(target);
    if (taintValue == null) return TaintValue.UNTAINTED;
    if (taintValue != TaintValue.UNKNOWN) return taintValue;
    taintValue = fromModifierListOwner(target, ref, processRecursively);
    return taintValue == null ? TaintValue.UNTAINTED : taintValue;
  }

  public List<NonMarkedElement> getNonMarkedElements() {
    return myNonMarkedElements;
  }

  private @Nullable TaintValue fromModifierListOwner(@NotNull PsiElement target, @NotNull PsiElement ref, boolean processRecursively) {
    PsiModifierListOwner owner = ObjectUtils.tryCast(target, PsiModifierListOwner.class);
    if (owner == null) return null;
    TaintValue taintValue = fromLocalVar(owner);
    if (taintValue != null) return taintValue;
    if (processRecursively) {
      taintValue = fromMethod(owner);
      if (taintValue != null) return taintValue;
      taintValue = fromField(owner);
      if (taintValue != null) return taintValue;
      taintValue = fromParam(owner);
      if (taintValue != null) return taintValue;
      return TaintValue.UNTAINTED;
    }
    myNonMarkedElements.add(new NonMarkedElement(owner, ref));
    return TaintValue.UNKNOWN;
  }

  private @Nullable TaintValue fromLocalVar(@NotNull PsiElement target) {
    PsiLocalVariable psiVariable = ObjectUtils.tryCast(target, PsiLocalVariable.class);
    if (psiVariable == null) return null;
    ULocalVariable uVariable = UastContextKt.toUElement(psiVariable, ULocalVariable.class);
    if (uVariable == null) return null;
    UExpression uInitializer = uVariable.getUastInitializer();
    TaintValue taintValue = fromExpression(uInitializer, true);
    if (taintValue == TaintValue.TAINTED) return taintValue;
    UBlockExpression codeBlock = UastUtils.getParentOfType(uVariable, UBlockExpression.class);
    return codeBlock == null ? TaintValue.UNTAINTED : analyze(taintValue, codeBlock, psiVariable);
  }

  private @NotNull TaintValue analyze(@NotNull TaintValue taintValue, @NotNull UBlockExpression codeBlock, @NotNull PsiVariable psiVariable) {
    class VarAnalyzer extends AbstractUastVisitor {
      private TaintValue myTaintValue;

      VarAnalyzer(@NotNull TaintValue taintValue) {
        myTaintValue = taintValue;
      }

      @Override
      public boolean visitBlockExpression(@NotNull UBlockExpression node) {
        for (UExpression expression : node.getExpressions()) {
          expression.accept(this);
          if (taintValue == TaintValue.TAINTED) return true;
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
        if (lhs == null || !psiVariable.equals(lhs.resolve())) return super.visitBinaryExpression(node);
        UExpression rhs = node.getRightOperand();
        myTaintValue = myTaintValue.join(fromExpression(rhs, true));
        return super.visitBinaryExpression(node);
      }
    }

    VarAnalyzer varAnalyzer = new VarAnalyzer(taintValue);
    codeBlock.accept(varAnalyzer);
    return varAnalyzer.myTaintValue;
  }

  private @Nullable TaintValue fromParam(@Nullable PsiElement target) {
    PsiParameter psiParameter = ObjectUtils.tryCast(target, PsiParameter.class);
    if (psiParameter == null) return null;
    UParameter uParameter = UastContextKt.toUElement(target, UParameter.class);
    if (uParameter == null) return null;
    // default parameter value
    UExpression uInitializer = uParameter.getUastInitializer();
    TaintValue taintValue = fromExpression(uInitializer, true);
    if (taintValue == TaintValue.TAINTED) return taintValue;
    UMethod uMethod = ObjectUtils.tryCast(uParameter.getUastParent(), UMethod.class);
    if (uMethod == null) return TaintValue.UNTAINTED;
    UBlockExpression uBlock = ObjectUtils.tryCast(uMethod.getUastBody(), UBlockExpression.class);
    if (uBlock != null) taintValue = analyze(taintValue, uBlock, psiParameter);
    if (taintValue == TaintValue.TAINTED) return taintValue;
    SmartList<NonMarkedElement> nonMarkedElements = new SmartList<>();
    // this might happen when we analyze kotlin primary constructor parameter
    if (uBlock == null) nonMarkedElements.addAll(findAssignments(target));
    PsiMethod psiMethod = ObjectUtils.tryCast(uMethod.getSourcePsi(), PsiMethod.class);
    // TODO: handle varargs
    if (psiMethod != null && !psiMethod.isVarArgs()) {
      int paramIdx = uMethod.getUastParameters().indexOf(uParameter);
      nonMarkedElements.addAll(findArgs(psiMethod, paramIdx));
    }
    if (nonMarkedElements.isEmpty()) return taintValue;
    myNonMarkedElements.addAll(nonMarkedElements);
    return TaintValue.UNKNOWN;
  }

  private @Nullable TaintValue fromField(@NotNull PsiElement target) {
    UField uField = UastContextKt.toUElement(target, UField.class);
    if (uField == null) return null;
    List<NonMarkedElement> children = new ArrayList<>();
    NonMarkedElement initializer = NonMarkedElement.create(uField.getUastInitializer());
    if (initializer != null) children.add(initializer);
    children.addAll(findAssignments(target));
    if (children.isEmpty()) return TaintValue.UNTAINTED;
    myNonMarkedElements.addAll(children);
    return TaintValue.UNKNOWN;
  }

  private @Nullable TaintValue fromMethod(@NotNull PsiElement target) {
    UMethod uMethod = UastContextKt.toUElement(target, UMethod.class);
    if (uMethod == null) return null;
    return analyze(uMethod);
  }

  private @NotNull TaintValue analyze(@NotNull UMethod uMethod) {
    class MethodAnalyzer extends AbstractUastVisitor {

      private final List<NonMarkedElement> myChildren = new ArrayList<>();

      @Override
      public boolean visitBlockExpression(@NotNull UBlockExpression node) {
        for (UExpression expression : node.getExpressions()) {
          expression.accept(this);
        }
        return true;
      }

      @Override
      public boolean visitReturnExpression(@NotNull UReturnExpression node) {
        UExpression returnExpression = node.getReturnExpression();
        if (returnExpression == null) return true;
        returnExpression.accept(new AbstractUastVisitor() {
          @Override
          public boolean visitElement(@NotNull UElement node) {
            NonMarkedElement nonMarked = NonMarkedElement.create(node);
            if (nonMarked == null) return super.visitElement(node);
            myChildren.add(nonMarked);
            return true;
          }
        });
        return super.visitReturnExpression(node);
      }
    }

    UBlockExpression methodBody = ObjectUtils.tryCast(uMethod.getUastBody(), UBlockExpression.class);
    if (methodBody == null) {
      // maybe it is a generated kotlin property getter or setter
      PsiElement sourcePsi = uMethod.getSourcePsi();
      if (sourcePsi == null) return TaintValue.UNTAINTED;
      TaintValue taintValue = fromField(sourcePsi);
      return taintValue == null ? TaintValue.UNTAINTED : taintValue;
    }
    MethodAnalyzer methodAnalyzer = new MethodAnalyzer();
    methodBody.accept(methodAnalyzer);
    List<NonMarkedElement> children = methodAnalyzer.myChildren;
    if (children.isEmpty()) return TaintValue.UNTAINTED;
    myNonMarkedElements.addAll(children);
    return TaintValue.UNKNOWN;
  }

  private @NotNull TaintValue fromExpression(@Nullable UExpression uExpression, boolean goDeep) {
    if (uExpression == null) return TaintValue.UNTAINTED;
    uExpression = UastUtils.skipParenthesizedExprDown(uExpression);
    if (uExpression == null || uExpression instanceof ULiteralExpression) return TaintValue.UNTAINTED;
    if (uExpression instanceof UResolvable) return analyze(uExpression);
    UPolyadicExpression uConcatenation = getConcatenation(uExpression);
    if (uConcatenation != null) return StreamEx.of(uConcatenation.getOperands()).collect(joining(true));
    UIfExpression uIfExpression = ObjectUtils.tryCast(uExpression, UIfExpression.class);
    if (uIfExpression != null) {
      return StreamEx.of(uIfExpression.getThenExpression(), uIfExpression.getElseExpression()).collect(joining(true));
    }
    if (!goDeep) return TaintValue.UNTAINTED;
    PsiExpression javaPsi = ObjectUtils.tryCast(uExpression.getJavaPsi(), PsiExpression.class);
    if (javaPsi == null) return TaintValue.UNTAINTED;
    return ExpressionUtils.nonStructuralChildren(javaPsi)
      .map(e -> UastContextKt.toUElement(e, UExpression.class))
      .collect(joining(false));
  }

  private @NotNull Collector<UExpression, ?, TaintValue> joining(boolean goDeep) {
    return MoreCollectors.mapping((UExpression e) -> fromExpression(e, goDeep), TaintValue.joining());
  }

  private static @NotNull Collection<NonMarkedElement> findArgs(PsiMethod psiMethod, int paramIdx) {
    return ReferencesSearch.search(psiMethod, psiMethod.getUseScope())
      .mapping(r -> ObjectUtils.tryCast(r.getElement().getParent(), PsiMethodCallExpression.class))
      .mapping(call -> call == null ? null : call.getArgumentList().getExpressions())
      .filtering(args -> args != null && args.length > paramIdx)
      .mapping(args -> UastContextKt.toUElement(args[paramIdx]))
      .mapping(arg -> NonMarkedElement.create(arg))
      .filtering(arg -> arg != null)
      .findAll();
  }

  private static @NotNull Collection<NonMarkedElement> findAssignments(@NotNull PsiElement target) {
    return ReferencesSearch.search(target, target.getUseScope())
      .mapping(u -> UastContextKt.getUastParentOfType(u.getElement(), UBinaryExpression.class))
      .filtering(binary -> isLhs(binary, target))
      .mapping(binary -> NonMarkedElement.create(binary.getRightOperand()))
      .filtering(e -> e != null)
      .findAll();
  }

  private static boolean isLhs(@Nullable UBinaryExpression uBinary, @NotNull PsiElement target) {
    if (uBinary == null) return false;
    UastBinaryOperator operator = uBinary.getOperator();
    if (operator != UastBinaryOperator.ASSIGN && operator != UastBinaryOperator.PLUS_ASSIGN) return false;
    UResolvable leftOperand = ObjectUtils.tryCast(UastUtils.skipParenthesizedExprDown(uBinary.getLeftOperand()), UResolvable.class);
    if (leftOperand == null) return false;
    PsiElement lhsTarget = leftOperand.resolve();
    if (lhsTarget == target) return true;
    // maybe it's kotlin property auto generated setter
    if (!(lhsTarget instanceof PsiMethod) || ((PsiMethod)lhsTarget).getBody() != null) {
      return false;
    }
    UElement uElement = UastContextKt.toUElement(lhsTarget);
    if (uElement == null) return false;
    PsiElement property = uElement.getSourcePsi();
    return property == target;
  }

  public static @Nullable TaintValue fromAnnotation(@Nullable PsiElement target) {
    PsiType type = target == null ? null : PsiUtil.getTypeByPsiElement(target);
    if (type == null) return null;
    if (target instanceof PsiClass) return null;
    if (target instanceof PsiModifierListOwner) {
      PsiModifierListOwner owner = (PsiModifierListOwner)target;
      TaintValue taintValue = TaintValueFactory.INSTANCE.fromModifierListOwner(owner);
      if (taintValue == TaintValue.UNKNOWN) taintValue = TaintValueFactory.of(owner);
      if (taintValue != TaintValue.UNKNOWN) return taintValue;
    }
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
}
