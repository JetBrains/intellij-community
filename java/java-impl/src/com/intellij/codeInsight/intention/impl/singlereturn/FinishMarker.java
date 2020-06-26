// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl.singlereturn;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.util.ObjectUtils.NULL;
import static com.intellij.util.ObjectUtils.tryCast;
import static java.util.Objects.requireNonNull;

/**
 * Represents a way to indicate whether method execution is already finished
 */
public final class FinishMarker {
  /**
   * Type of finish marker
   */
  final @NotNull FinishMarkerType myType;
  /**
   * Sentinel value
   */
  final @Nullable PsiExpression myDefaultValue;

  private FinishMarker(@NotNull FinishMarkerType type, @Nullable PsiExpression value) {
    myType = type;
    myDefaultValue = value;
  }

  /**
   * @param block method body (must be physical as CommonDataflow will be queried)
   * @param returnType method return type
   * @param returns list of all method returns
   * @return a FinishMarker which is suitable for given method
   */
  public static FinishMarker defineFinishMarker(@NotNull PsiCodeBlock block, @NotNull PsiType returnType, List<? extends PsiReturnStatement> returns) {
    boolean mayNeedMarker = mayNeedMarker(returns, block);
    return defineFinishMarker(block, returns, returnType, mayNeedMarker, JavaPsiFacade.getElementFactory(block.getProject()));
  }

  private static boolean mayNeedMarker(List<? extends PsiReturnStatement> returns, PsiCodeBlock block) {
    for (PsiReturnStatement returnStatement : returns) {
      if (mayNeedMarker(returnStatement, block)) return true;
    }
    return false;
  }

  /**
   * Checks whether we may need a marker value to indicate premature exit from given return statement.
   *
   * @param returnStatement return statement to check
   * @param block method body (ancestor of return statement).
   * @return false if it's possible to transform the code removing given return statement without introducing a marker;
   * true if marker might be necessary.
   */
  static boolean mayNeedMarker(PsiReturnStatement returnStatement, PsiCodeBlock block) {
    PsiElement parent = returnStatement.getParent();
    while (parent instanceof PsiCodeBlock) {
      PsiElement grandParent = parent.getParent();
      if (grandParent instanceof PsiBlockStatement) {
        parent = grandParent.getParent();
        continue;
      }
      if (grandParent instanceof PsiCatchSection) {
        parent = grandParent.getParent();
        break;
      }
      if (grandParent instanceof PsiStatement) {
        parent = grandParent;
        break;
      }
      return parent != block;
    }
    if (!(parent instanceof PsiStatement)) return true;
    PsiStatement currentContext = (PsiStatement)parent;
    PsiStatement loopOrSwitch = PsiTreeUtil.getNonStrictParentOfType(currentContext, PsiLoopStatement.class, PsiSwitchStatement.class);
    if (loopOrSwitch != null && PsiTreeUtil.isAncestor(block, loopOrSwitch, true)) {
      currentContext = loopOrSwitch;
    }
    else {
      while (currentContext instanceof PsiIfStatement) {
        PsiElement ifParent = currentContext.getParent();
        if (ifParent instanceof PsiIfStatement) {
          currentContext = (PsiStatement)ifParent;
        }
        else if (ifParent instanceof PsiCodeBlock) {
          if (!(ifParent.getParent() instanceof PsiStatement)) {
            return ifParent != block;
          }
          currentContext = (PsiStatement)ifParent.getParent();
          if (!(currentContext instanceof PsiBlockStatement) ||
              !(currentContext.getParent() instanceof PsiIfStatement) ||
              ControlFlowUtils.codeBlockMayCompleteNormally((PsiCodeBlock)ifParent)) {
            break;
          }
          currentContext = (PsiStatement)currentContext.getParent();
        }
      }
    }
    while (true) {
      PsiElement contextParent = currentContext.getParent();
      if (contextParent instanceof PsiCodeBlock) {
        PsiStatement[] contextStatements = ((PsiCodeBlock)contextParent).getStatements();
        int pos = ArrayUtil.indexOf(contextStatements, currentContext);
        assert pos >= 0;
        if (pos < contextStatements.length - 1) return true;
        if (contextParent == block) return false;
        if (!(contextParent.getParent() instanceof PsiStatement)) return true;
        currentContext = (PsiStatement)contextParent.getParent();
      }
      else if (contextParent instanceof PsiIfStatement || contextParent instanceof PsiLabeledStatement) {
        currentContext = (PsiStatement)contextParent;
      }
      else {
        return true;
      }
    }
  }

  private static FinishMarker defineFinishMarker(PsiCodeBlock block, List<? extends PsiReturnStatement> returns, PsiType returnType,
                                                 boolean mayNeedMarker, PsiElementFactory factory) {
    if (PsiType.VOID.equals(returnType)) {
      return new FinishMarker(FinishMarkerType.SEPARATE_VAR, null);
    }
    PsiReturnStatement terminalReturn = tryCast(ArrayUtil.getLastElement(block.getStatements()), PsiReturnStatement.class);
    List<PsiExpression> nonTerminalReturns = StreamEx.<PsiReturnStatement>of(returns).without(terminalReturn)
      .map(PsiReturnStatement::getReturnValue)
      .map(PsiUtil::skipParenthesizedExprDown).toList();
    if (nonTerminalReturns.size() == 0) {
      return new FinishMarker(FinishMarkerType.SEPARATE_VAR, null);
    }
    Set<Object> nonTerminalReturnValues = StreamEx.of(nonTerminalReturns)
      .map(val -> val instanceof PsiLiteralExpression ? ((PsiLiteralExpression)val).getValue() : NULL)
      .toSet();
    if (!mayNeedMarker) {
      PsiExpression initValue = findBestExpression(terminalReturn, nonTerminalReturns, mayNeedMarker);
      if (initValue == null && nonTerminalReturnValues.size() == 1 && nonTerminalReturnValues.iterator().next() != NULL) {
        initValue = nonTerminalReturns.iterator().next();
      }
      return new FinishMarker(FinishMarkerType.SEPARATE_VAR, initValue);
    }
    if (PsiType.BOOLEAN.equals(returnType)) {
      if (nonTerminalReturnValues.size() == 1) {
        Object value = nonTerminalReturnValues.iterator().next();
        if (value instanceof Boolean) {
          boolean boolReturn = (boolean)value;
          FinishMarkerType markerType = boolReturn ? FinishMarkerType.BOOLEAN_TRUE : FinishMarkerType.BOOLEAN_FALSE;
          return new FinishMarker(markerType, factory.createExpressionFromText(String.valueOf(!boolReturn), null));
        }
      }
    }
    if (PsiType.INT.equals(returnType) || PsiType.LONG.equals(returnType)) {
      return getMarkerForIntegral(nonTerminalReturns, terminalReturn, mayNeedMarker, returnType, factory);
    }
    if (!(returnType instanceof PsiPrimitiveType)) {
      if (StreamEx.of(nonTerminalReturns).map(ret -> NullabilityUtil.getExpressionNullability(ret, true))
        .allMatch(Nullability.NOT_NULL::equals)) {
        return new FinishMarker(FinishMarkerType.VALUE_NON_EQUAL, factory.createExpressionFromText("null", null));
      }
    }
    if (terminalReturn != null) {
      PsiExpression value = terminalReturn.getReturnValue();
      if (canMoveToStart(value)) {
        return new FinishMarker(FinishMarkerType.SEPARATE_VAR, value);
      }
    }
    return new FinishMarker(FinishMarkerType.SEPARATE_VAR, findBestExpression(terminalReturn, nonTerminalReturns, mayNeedMarker));
  }

  @Nullable
  private static PsiExpression findBestExpression(PsiReturnStatement terminalReturn,
                                                  List<PsiExpression> nonTerminalReturns,
                                                  boolean mayNeedMarker) {
    List<PsiExpression> bestGroup = StreamEx.of(nonTerminalReturns)
      .filter(FinishMarker::canMoveToStart)
      .groupingBy(PsiExpression::getText, LinkedHashMap::new, Collectors.toList())
      .values()
      .stream()
      .max(Comparator.comparingInt(List::size))
      .orElse(Collections.emptyList());
    if (bestGroup.size() >= 2) {
      return bestGroup.get(0);
    }
    if (terminalReturn != null && canMoveToStart(terminalReturn.getReturnValue())) {
      return terminalReturn.getReturnValue();
    }
    if (mayNeedMarker && !bestGroup.isEmpty()) {
      return bestGroup.get(0);
    }
    return null;
  }

  @NotNull
  private static FinishMarker getMarkerForIntegral(List<PsiExpression> nonTerminalReturns,
                                                   PsiReturnStatement terminalReturn,
                                                   boolean mayNeedMarker, PsiType returnType, PsiElementFactory factory) {
    boolean isLong = PsiType.LONG.equals(returnType);
    LongRangeSet fullSet = requireNonNull(LongRangeSet.fromType(returnType));
    LongRangeSet set = nonTerminalReturns.stream()
      .map(CommonDataflow::getExpressionRange)
      .map(range -> range == null ? fullSet : range)
      .reduce(LongRangeSet::unite)
      .orElse(fullSet);
    if (!set.isEmpty() && !set.contains(fullSet)) {
      PsiExpression terminalReturnValue = terminalReturn == null ? null : terminalReturn.getReturnValue();
      LongRangeSet terminal = CommonDataflow.getExpressionRange(terminalReturnValue);
      Long point;
      if (terminal != null) {
        point = terminal.getConstantValue();
        if (point != null && !set.contains(point)) {
          PsiExpression defValue = canMoveToStart(terminalReturnValue) ?
                                   (PsiExpression)terminalReturnValue.copy() :
                                   factory.createExpressionFromText(point + (isLong ? "L" : ""), null);
          return new FinishMarker(FinishMarkerType.VALUE_NON_EQUAL, defValue);
        }
      }
      long[] candidates = {0, 1, -1, fullSet.min(), fullSet.max()};
      point = null;
      for (long candidate : candidates) {
        if (!set.contains(candidate)) {
          point = candidate;
          break;
        }
      }
      if (point != null) {
        String text = point == Integer.MIN_VALUE ? "java.lang.Integer.MIN_VALUE" :
                      point == Integer.MAX_VALUE ? "java.lang.Integer.MAX_VALUE" :
                      point == Long.MIN_VALUE ? "java.lang.Long.MIN_VALUE" :
                      point == Long.MAX_VALUE ? "java.lang.Long.MAX_VALUE" :
                      String.valueOf(point);
        return new FinishMarker(FinishMarkerType.VALUE_NON_EQUAL, factory.createExpressionFromText(text, null));
      }
    }
    return new FinishMarker(FinishMarkerType.SEPARATE_VAR, findBestExpression(terminalReturn, nonTerminalReturns, mayNeedMarker));
  }

  @Contract("null -> false")
  private static boolean canMoveToStart(PsiExpression value) {
    if (!ExpressionUtils.isSafelyRecomputableExpression(value)) return false;
    PsiReferenceExpression ref = tryCast(PsiUtil.skipParenthesizedExprDown(value), PsiReferenceExpression.class);
    if (ref != null && !ref.isQualified()) {
      PsiVariable target = tryCast(ref.resolve(), PsiVariable.class);
      if (target instanceof PsiLocalVariable) return false;
      if (target instanceof PsiParameter) {
        PsiElement block = ((PsiParameter)target).getDeclarationScope();
        return block instanceof PsiMethod && HighlightControlFlowUtil.isEffectivelyFinal(target, block, null);
      }
    }
    return true;
  }

  /**
   * Type of finish marker
   */
  enum FinishMarkerType {
    /**
     * If result boolean variable is true, then the method execution is finished
     */
    BOOLEAN_TRUE,
    /**
     * If result boolean variable is false, then the method execution is finished
     */
    BOOLEAN_FALSE,
    /**
     * If result variable is equal to sentinel, then the method execution is finished
     */
    VALUE_EQUAL,
    /**
     * If result variable is not equal to sentinel, then the method execution is finished
     */
    VALUE_NON_EQUAL,
    /**
     * Separate boolean variable is used to indicate whether the method execution is finished.
     * This value also used if it was detected that no finish marker is actually necessary
     */
    SEPARATE_VAR
  }
}
