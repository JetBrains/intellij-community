// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.dfaassist.java;

import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.NullabilityProblemKind;
import com.intellij.codeInspection.dataFlow.java.JavaDfaListener;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaExpressionAnchor;
import com.intellij.codeInspection.dataFlow.jvm.problems.ArrayIndexProblem;
import com.intellij.codeInspection.dataFlow.jvm.problems.ArrayStoreProblem;
import com.intellij.codeInspection.dataFlow.jvm.problems.ClassCastProblem;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.lang.UnsatisfiedConditionProblem;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.debugger.engine.dfaassist.DebuggerDfaListener;
import com.intellij.debugger.engine.dfaassist.DfaHint;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

class JavaDebuggerDfaListener implements JavaDfaListener, DebuggerDfaListener {
  private static final TokenSet BOOLEAN_TOKENS = TokenSet.create(
    JavaTokenType.ANDAND, JavaTokenType.OROR, JavaTokenType.XOR, JavaTokenType.AND, JavaTokenType.OR, JavaTokenType.EQEQ, JavaTokenType.NE);

  private final Map<PsiElement, DfaHint> myHints = new HashMap<>();
  private final Set<PsiExpression> myReachableExpressions = new HashSet<>();

  private void addHint(@NotNull PsiElement element, @Nullable DfaHint hint) {
    if (hint != null) {
      myHints.merge(element, hint, DfaHint::merge);
    }
  }

  @Override
  public void beforeExpressionPush(@NotNull DfaValue value,
                                   @NotNull PsiExpression expression,
                                   @NotNull DfaMemoryState state) {
    myReachableExpressions.add(expression);
    if (!shouldTrackExpressionValue(expression)) return;
    DfaHint hint = DfaHint.ANY_VALUE;
    DfType dfType = state.getDfType(value);
    if (dfType == DfTypes.TRUE) {
      hint = DfaHint.TRUE;
    }
    else if (dfType == DfTypes.FALSE) {
      hint = DfaHint.FALSE;
    }
    else if (dfType == DfType.FAIL) {
      hint = DfaHint.FAIL;
    }
    addHint(expression, hint);
  }

  @Override
  public void onCondition(@NotNull UnsatisfiedConditionProblem problem,
                          @NotNull DfaValue value,
                          @NotNull ThreeState failed,
                          @NotNull DfaMemoryState state) {
    if (problem instanceof ArrayStoreProblem) {
      addHint(((ArrayStoreProblem)problem).getAnchor().getLExpression(), failed == ThreeState.YES ? DfaHint.ASE : DfaHint.NONE);
    }
    else if (problem instanceof ArrayIndexProblem) {
      PsiArrayAccessExpression anchor = ((ArrayIndexProblem)problem).getAnchor();
      // Anchor to the last child to differentiate from ArrayStoreException
      addHint(anchor.getLastChild(), failed == ThreeState.YES ? DfaHint.AIOOBE : DfaHint.NONE);
    }
    else if (problem instanceof ClassCastProblem) {
      addHint(((ClassCastProblem)problem).getAnchor(), failed == ThreeState.YES ? DfaHint.CCE : DfaHint.NONE);
    }
    else if (problem instanceof NullabilityProblemKind.NullabilityProblem<?>) {
      var npeProblem = (NullabilityProblemKind.NullabilityProblem<?>) problem;
      PsiExpression expression = npeProblem.getDereferencedExpression();
      if (expression != null && npeProblem.thrownException() != null) {
        DfaHint hint;
        if (failed == ThreeState.YES) {
          hint = npeProblem.thrownException().equals(CommonClassNames.JAVA_LANG_NULL_POINTER_EXCEPTION)
                 ? DfaHint.NPE
                 : DfaHint.NULL_AS_NOT_NULL;
        } else {
          hint = DfaHint.NONE;
        }
        addHint(expression, hint);
      }
    }
  }

  private static boolean shouldTrackExpressionValue(@NotNull PsiExpression expr) {
    if (BoolUtils.isNegated(expr)) {
      // It's enough to report for parent only
      return false;
    }
    if (expr instanceof PsiAssignmentExpression) {
      // Report right hand of assignment only
      return false;
    }
    if (expr instanceof PsiPolyadicExpression) {
      IElementType tokenType = ((PsiPolyadicExpression)expr).getOperationTokenType();
      if (BOOLEAN_TOKENS.contains(tokenType)) {
        PsiExpression firstOperand = ((PsiPolyadicExpression)expr).getOperands()[0];
        if (firstOperand != null && PsiType.BOOLEAN.equals(firstOperand.getType())) {
          // For polyadic boolean expression let's report components only, otherwise the report gets cluttered
          return false;
        }
      }
    }
    return true;
  }

  void cleanup() {
    myHints.entrySet().removeIf(e -> {
      PsiElement anchor = e.getKey();
      DfaHint hint = e.getValue();
      if (hint.getTitle() == null) return true;
      if (!(anchor instanceof PsiExpression)) return false;
      PsiExpression expr = (PsiExpression)anchor;
      CommonDataflow.DataflowResult result = CommonDataflow.getDataflowResult(expr);
      return result != null && result.getExpressionValues(expr).size() == 1;
    });
  }

  @Override
  public @NotNull Map<PsiElement, DfaHint> computeHints() {
    cleanup();
    return Collections.unmodifiableMap(myHints);
  }

  @Override
  public @NotNull Collection<TextRange> unreachableSegments(@NotNull PsiElement startAnchor, @NotNull List<DfaAnchor> allAnchors) {
    Set<PsiExpression> unreachable =
      StreamEx.of(allAnchors).select(JavaExpressionAnchor.class).map(JavaExpressionAnchor::getExpression).toMutableSet();
    unreachable.removeAll(myReachableExpressions);
    Set<TextRange> result = new HashSet<>();
    for (PsiExpression expression : unreachable) {
      ContainerUtil.addIfNotNull(result, createRange(startAnchor, expression));
    }
    return result;
  }

  private @Nullable TextRange createRange(@NotNull PsiElement startAnchor, @NotNull PsiExpression expression) {
    PsiElement parent = expression.getParent();
    if (parent instanceof PsiConditionalExpression && myReachableExpressions.contains(parent)) {
      return expression.getTextRange();
    }
    if (parent instanceof PsiPolyadicExpression) {
      IElementType tokenType = ((PsiPolyadicExpression)parent).getOperationTokenType();
      if (tokenType == JavaTokenType.ANDAND || tokenType == JavaTokenType.OROR) {
        PsiExpression prev = PsiTreeUtil.getPrevSiblingOfType(expression, PsiExpression.class);
        if (prev != null && myReachableExpressions.contains(prev)) {
          PsiJavaToken token = ((PsiPolyadicExpression)parent).getTokenBeforeOperand(expression);
          if (token != null) {
            return TextRange.create(token.getTextRange().getStartOffset(), parent.getTextRange().getEndOffset());
          }
        }
      }
    }
    PsiStatement statement = findStatement(expression);
    if (statement != null && !PsiTreeUtil.isAncestor(statement, startAnchor, false)) {
      PsiElement statementParent = statement.getParent();
      PsiExpression anchor = getControlFlowStatementAnchor(statementParent);
      if (anchor != null &&
          (PsiTreeUtil.isAncestor(statement, startAnchor, true) ||
           myReachableExpressions.contains(anchor))) {
        return statement.getTextRange();
      }
      if (statementParent instanceof PsiCodeBlock) {
        PsiStatement prevStatement = ObjectUtils.tryCast(PsiTreeUtil.skipWhitespacesAndCommentsBackward(statement), PsiStatement.class);
        if (prevStatement instanceof PsiSwitchLabelStatement) {
          PsiSwitchBlock block = ((PsiSwitchLabelStatement)prevStatement).getEnclosingSwitchBlock();
          if (block != null && (isAnchorBefore(startAnchor, statement) || myReachableExpressions.contains(block.getExpression()))) {
            PsiElement last = ((PsiCodeBlock)statementParent).getRBrace();
            PsiSwitchLabelStatement nextLabel = PsiTreeUtil.getNextSiblingOfType(statement, PsiSwitchLabelStatement.class);
            if (nextLabel != null) {
              last = nextLabel;
            }
            PsiElement lastStatement = PsiTreeUtil.skipWhitespacesAndCommentsBackward(last);
            if (lastStatement != null) {
              return TextRange.create(statement.getTextRange().getStartOffset(), lastStatement.getTextRange().getEndOffset());
            }
          }
          return null;
        }
        PsiElement lastStatement = PsiTreeUtil.skipWhitespacesAndCommentsBackward(((PsiCodeBlock)statementParent).getRBrace());
        if (lastStatement != null && prevStatement != null) {
          if (PsiTreeUtil.isAncestor(prevStatement, startAnchor, false)) {
            if (prevStatement instanceof PsiLoopStatement) return null;
            return TextRange.create(statement.getTextRange().getStartOffset(), lastStatement.getTextRange().getEndOffset());
          }
          if (ContainerUtil.exists(myReachableExpressions, ex -> PsiTreeUtil.isAncestor(prevStatement, ex, true))) {
            return TextRange.create(statement.getTextRange().getStartOffset(), lastStatement.getTextRange().getEndOffset());
          }
        }
      }
    }
    return null;
  }

  private static boolean isAnchorBefore(@NotNull PsiElement anchor, @NotNull PsiStatement statement) {
    return PsiTreeUtil.isAncestor(statement.getParent(), anchor, true) &&
           StreamEx.iterate(statement.getPrevSibling(), Objects::nonNull, PsiElement::getPrevSibling)
             .select(PsiStatement.class).anyMatch(st -> PsiTreeUtil.isAncestor(st, anchor, false));
  }

  private static @Nullable PsiExpression getControlFlowStatementAnchor(@NotNull PsiElement statement) {
    if (statement instanceof PsiIfStatement) {
      return ((PsiIfStatement)statement).getCondition();
    }
    if (statement instanceof PsiWhileStatement || statement instanceof PsiForStatement) {
      return ((PsiConditionalLoopStatement)statement).getCondition();
    }
    if (statement instanceof PsiForeachStatement) {
      return ((PsiForeachStatement)statement).getIteratedValue();
    }
    return null;
  }

  private static @Nullable PsiElement goDown(@Nullable PsiElement element) {
    if (element == null) return null;
    if (element instanceof PsiExpression && PsiUtil.isConstantExpression((PsiExpression)element)) return element;
    if (element instanceof PsiIfStatement) {
      return ((PsiIfStatement)element).getCondition();
    }
    if (element instanceof PsiWhileStatement) {
      return ((PsiWhileStatement)element).getCondition();
    }
    if (element instanceof PsiSwitchBlock) {
      return ((PsiSwitchBlock)element).getExpression();
    }
    if (element instanceof PsiForeachStatement) {
      return ((PsiForeachStatement)element).getIteratedValue();
    }
    if (element instanceof PsiDoWhileStatement) {
      return ((PsiDoWhileStatement)element).getBody();
    }
    if (element instanceof PsiForStatement) {
      PsiStatement initialization = ((PsiForStatement)element).getInitialization();
      if (initialization != null) {
        return initialization;
      }
      PsiExpression condition = ((PsiForStatement)element).getCondition();
      if (condition != null) {
        return condition;
      }
      return ((PsiForStatement)element).getBody();
    }
    if (element instanceof PsiSynchronizedStatement) {
      return ((PsiSynchronizedStatement)element).getLockExpression();
    }
    if (element instanceof PsiTryStatement) {
      PsiResourceList list = ((PsiTryStatement)element).getResourceList();
      if (list != null) {
        Iterator<PsiResourceListElement> iterator = list.iterator();
        if (iterator.hasNext()) {
          return iterator.next();
        }
      }
      return ((PsiTryStatement)element).getTryBlock();
    }
    if (element instanceof PsiBlockStatement) {
      return ((PsiBlockStatement)element).getCodeBlock();
    }
    if (element instanceof PsiDeclarationStatement) {
      for (PsiElement declaredElement : ((PsiDeclarationStatement)element).getDeclaredElements()) {
        if (declaredElement instanceof PsiLocalVariable && ((PsiLocalVariable)declaredElement).getInitializer() != null) {
          return ((PsiLocalVariable)declaredElement).getInitializer();
        }
      }
      return null;
    }
    if (element instanceof PsiCodeBlock) {
      PsiStatement[] statements = ((PsiCodeBlock)element).getStatements();
      for (PsiStatement statement : statements) {
        PsiElement statementElement = goDown(statement);
        if (statementElement != null) {
          return statementElement;
        }
      }
      return null;
    }
    if (element instanceof PsiReturnStatement) {
      return ((PsiReturnStatement)element).getReturnValue();
    }
    if (element instanceof PsiExpressionStatement) {
      return ((PsiExpressionStatement)element).getExpression();
    }
    if (element instanceof PsiYieldStatement) {
      return ((PsiYieldStatement)element).getExpression();
    }
    if (element instanceof PsiAssertStatement) {
      return ((PsiAssertStatement)element).getAssertCondition();
    }
    if (element instanceof PsiResourceExpression) {
      return ((PsiResourceExpression)element).getExpression();
    }
    if (element instanceof PsiResourceVariable) {
      return ((PsiResourceVariable)element).getInitializer();
    }
    if (element instanceof PsiPolyadicExpression) {
      return ((PsiPolyadicExpression)element).getOperands()[0];
    }
    if (element instanceof PsiInstanceOfExpression) {
      return ((PsiInstanceOfExpression)element).getOperand();
    }
    if (element instanceof PsiParenthesizedExpression) {
      return ((PsiParenthesizedExpression)element).getExpression();
    }
    if (element instanceof PsiConditionalExpression) {
      return ((PsiConditionalExpression)element).getCondition();
    }
    if (element instanceof PsiReferenceExpression) {
      PsiExpression qualifier = ((PsiReferenceExpression)element).getQualifierExpression();
      return isExpressionQualifier(qualifier) ? qualifier : element;
    }
    if (element instanceof PsiMethodCallExpression) {
      PsiExpression qualifier = ((PsiMethodCallExpression)element).getMethodExpression().getQualifierExpression();
      if (isExpressionQualifier(qualifier)) return qualifier;
      PsiExpression[] arguments = ((PsiMethodCallExpression)element).getArgumentList().getExpressions();
      if (arguments.length > 0) {
        return arguments[0];
      }
      return element;
    }
    if (element instanceof PsiLiteralExpression || element instanceof PsiLambdaExpression || element instanceof PsiQualifiedExpression) {
      return element;
    }
    if (element instanceof PsiAssignmentExpression) {
      return ((PsiAssignmentExpression)element).getLExpression();
    }
    if (element instanceof PsiTypeCastExpression) {
      return ((PsiTypeCastExpression)element).getOperand();
    }
    if (element instanceof PsiUnaryExpression) {
      return ((PsiUnaryExpression)element).getOperand();
    }
    if (element instanceof PsiNewExpression) {
      PsiExpression qualifier = ((PsiNewExpression)element).getQualifier();
      if (qualifier != null) {
        return qualifier;
      }
      PsiArrayInitializerExpression initializer = ((PsiNewExpression)element).getArrayInitializer();
      if (initializer != null) {
        return initializer;
      }
      PsiExpression[] dimensions = ((PsiNewExpression)element).getArrayDimensions();
      if (dimensions.length > 0) {
        return dimensions[0];
      }
      PsiExpressionList args = ((PsiNewExpression)element).getArgumentList();
      if (args != null && !args.isEmpty()) {
        return args.getExpressions()[0];
      }
      return element;
    }
    if (element instanceof PsiArrayInitializerExpression) {
      PsiExpression[] initializers = ((PsiArrayInitializerExpression)element).getInitializers();
      if (initializers.length > 0) return initializers[0];
      return element;
    }
    return null;
  }

  private static boolean isExpressionQualifier(PsiExpression qualifier) {
    return qualifier != null &&
           (!(qualifier instanceof PsiReferenceExpression) || !(((PsiReferenceExpression)qualifier).resolve() instanceof PsiClass));
  }

  private static @Nullable PsiExpression getFirstExpressionInside(@Nullable PsiElement element) {
    while (true) {
      PsiElement firstExecuted = goDown(element);
      if (firstExecuted == null || firstExecuted == element) return (PsiExpression)firstExecuted;
      element = firstExecuted;
    }
  }

  private static @Nullable PsiStatement findStatement(@NotNull PsiExpression anchor) {
    PsiStatement statement = PsiTreeUtil.getParentOfType(anchor, PsiStatement.class);
    if (statement == null || getFirstExpressionInside(statement) != anchor) {
      return null;
    }
    PsiElement parent = statement;
    while (true) {
      PsiElement grandParent = parent.getParent();
      if (grandParent instanceof PsiCodeBlock &&
          grandParent.getParent() instanceof PsiBlockStatement &&
          PsiTreeUtil.getChildOfType(grandParent, PsiStatement.class) == parent) {
        parent = grandParent.getParent();
      }
      else {
        break;
      }
    }
    return (PsiStatement)parent;
  }
}
