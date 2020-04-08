// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.siyeh.ig.callMatcher.CallMatcher.anyOf;
import static com.siyeh.ig.callMatcher.CallMatcher.staticCall;

public class UnrollLoopAction extends PsiElementBaseIntentionAction {
  private static class Holder {
    private static final CallMatcher LIST_CONSTRUCTOR = anyOf(staticCall(CommonClassNames.JAVA_UTIL_ARRAYS, "asList"),
                                                              staticCall(CommonClassNames.JAVA_UTIL_LIST, "of"));
    private static final CallMatcher SINGLETON_CONSTRUCTOR =
      anyOf(staticCall(CommonClassNames.JAVA_UTIL_COLLECTIONS, "singleton", "singletonList").parameterCount(1),
            staticCall(CommonClassNames.JAVA_UTIL_LIST, "of").parameterTypes("E"));
  }
  /**
   * Do not show the intention if approximate size of generated code exceeds given value to prevent
   * accidental code blow up or out-of-memory error
   */
  private static final int MAX_BODY_SIZE_ESTIMATE = 5_000;

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull final PsiElement element) {
    PsiLoopStatement loop = PsiTreeUtil.getParentOfType(element, PsiLoopStatement.class);
    PsiVariable iterationParameter = getVariable(loop);
    if (iterationParameter == null || !(loop.getParent() instanceof PsiCodeBlock)) return false;
    List<PsiExpression> expressions = extractExpressions(loop);
    PsiStatement body = loop.getBody();
    if (body == null) return false;
    if (expressions.isEmpty() || expressions.size() > MAX_BODY_SIZE_ESTIMATE ||
        (expressions.size() - 1) * body.getTextLength() > MAX_BODY_SIZE_ESTIMATE){
      return false;
    }
    PsiStatement[] statements = ControlFlowUtils.unwrapBlock(body);
    if (statements.length == 0) return false;
    if (Arrays.stream(statements).anyMatch(PsiDeclarationStatement.class::isInstance)) return false;
    if (VariableAccessUtils.variableIsAssigned(iterationParameter, body)) return false;
    for (PsiStatement statement : statements) {
      if (isLoopBreak(statement)) continue;
      boolean acceptable = PsiTreeUtil.processElements(statement, e -> {
        if (e instanceof PsiBreakStatement && ((PsiBreakStatement)e).findExitedStatement() == loop) return false;
        if (e instanceof PsiContinueStatement && ((PsiContinueStatement)e).findContinuedStatement() == loop) return false;
        return true;
      });
      if (!acceptable) return false;
    }
    return true;
  }

  @Contract("null -> null")
  @Nullable
  private static PsiVariable getVariable(PsiLoopStatement loop) {
    if (loop instanceof PsiForeachStatement) {
      return ((PsiForeachStatement)loop).getIterationParameter();
    }
    if (loop instanceof PsiForStatement) {
      CountingLoop countingLoop = CountingLoop.from((PsiForStatement)loop);
      if (countingLoop != null) {
        return countingLoop.getCounter();
      }
    }
    return null;
  }

  @NotNull
  private static List<PsiExpression> extractExpressions(PsiLoopStatement loop) {
    if (loop instanceof PsiForeachStatement) {
      PsiExpression expression = ExpressionUtils.resolveExpression(((PsiForeachStatement)loop).getIteratedValue());
      expression = PsiUtil.skipParenthesizedExprDown(expression);
      if (expression instanceof PsiArrayInitializerExpression) {
        return Arrays.asList(((PsiArrayInitializerExpression)expression).getInitializers());
      }
      if (expression instanceof PsiNewExpression) {
        PsiArrayInitializerExpression initializer = ((PsiNewExpression)expression).getArrayInitializer();
        return initializer == null ? Collections.emptyList() : Arrays.asList(initializer.getInitializers());
      }
      if (expression instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
        if (Holder.SINGLETON_CONSTRUCTOR.test(call)) {
          return Arrays.asList(call.getArgumentList().getExpressions());
        }
        if (Holder.LIST_CONSTRUCTOR.test(call)) {
          PsiExpression[] args = call.getArgumentList().getExpressions();
          if (args.length > 1 || MethodCallUtils.isVarArgCall(call)) {
            return Arrays.asList(args);
          }
        }
      }
    }
    if (loop instanceof PsiForStatement) {
      CountingLoop countingLoop = CountingLoop.from((PsiForStatement)loop);
      if (countingLoop != null) {
        boolean descending = countingLoop.isDescending();
        long multiplier = descending ? -1 : 1;
        Object from = ExpressionUtils.computeConstantExpression(countingLoop.getInitializer());
        if (!(from instanceof Integer) && !(from instanceof Long)) return Collections.emptyList();
        long fromValue = ((Number)from).longValue();
        Object to = ExpressionUtils.computeConstantExpression(countingLoop.getBound());
        if (!(to instanceof Integer) && !(to instanceof Long)) return Collections.emptyList();
        long toValue = ((Number)to).longValue();
        long diff = multiplier * (toValue - fromValue);
        String suffix = PsiType.LONG.equals(countingLoop.getCounter().getType()) ? "L" : "";
        if (countingLoop.isIncluding()) {
          diff++; // overflow is ok: diff will become negative and we will exit
        }
        if (diff < 0 || diff > MAX_BODY_SIZE_ESTIMATE) return Collections.emptyList();
        int size = (int)(diff); // Less or equal to MAX_ITERATIONS => fits to int
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(loop.getProject());
        return new AbstractList<PsiExpression>() {
          @Override
          public PsiExpression get(int index) {
            long value = fromValue + multiplier * index;
            return factory.createExpressionFromText(value + suffix, loop);
          }

          @Override
          public int size() {
            return size;
          }
        };
      }
    }
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return JavaBundle.message("intention.unroll.loop.family");
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    PsiLoopStatement loop = PsiTreeUtil.getParentOfType(element, PsiLoopStatement.class);
    if (loop == null) return;
    if (!(loop.getParent() instanceof PsiCodeBlock)) return;
    List<PsiExpression> expressions = extractExpressions(loop);
    if (expressions.isEmpty()) return;
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    CommentTracker ct = new CommentTracker();
    PsiElement anchor = loop;
    for (PsiExpression expression : expressions) {
      ct.markUnchanged(loop.getBody());
      PsiLoopStatement copy = (PsiLoopStatement)factory.createStatementFromText(loop.getText(), element);
      PsiVariable variable = Objects.requireNonNull(getVariable(copy));
      for (PsiReference reference : ReferencesSearch.search(variable, new LocalSearchScope(copy))) {
        final PsiElement referenceElement = reference.getElement();
        if (referenceElement instanceof PsiJavaCodeReferenceElement) {
          ct.markUnchanged(expression);
          InlineUtil.inlineVariable(variable, expression, (PsiJavaCodeReferenceElement)referenceElement);
        }
      }
      PsiStatement body = copy.getBody();
      assert body != null;
      PsiElement[] children;
      if (body instanceof PsiBlockStatement) {
        children = ((PsiBlockStatement)body).getCodeBlock().getChildren();
        // Skip {braces}
        children = Arrays.copyOfRange(children, 1, children.length-1);
      } else {
        children = new PsiElement[]{body};
      }
      for(PsiElement child : children) {
        PsiElement added = anchor.getParent().addBefore(child, anchor);
        if (added instanceof PsiIfStatement && isLoopBreak((PsiStatement)added)) {
          PsiIfStatement ifStatement = (PsiIfStatement)added;
          PsiExpression condition = Objects.requireNonNull(ifStatement.getCondition());
          PsiStatement thenBranch = Objects.requireNonNull(ifStatement.getThenBranch());
          String negated = BoolUtils.getNegatedExpressionText(condition, ct);
          condition.replace(factory.createExpressionFromText(negated, condition));
          PsiBlockStatement block = (PsiBlockStatement)thenBranch.replace(factory.createStatementFromText("{}", added));
          anchor = block.getCodeBlock().getLastChild();
        }
      }
    }
    if (loop instanceof PsiForeachStatement) {
      PsiExpression iteratedValue = ((PsiForeachStatement)loop).getIteratedValue();
      PsiLocalVariable variable = ExpressionUtils.resolveLocalVariable(iteratedValue);
      if (variable != null) ct.delete(variable);
    }
    ct.deleteAndRestoreComments(loop);
  }

  private static boolean isLoopBreak(PsiStatement statement) {
    if (!(statement instanceof PsiIfStatement)) return false;
    PsiIfStatement ifStatement = (PsiIfStatement)statement;
    if (ifStatement.getElseBranch() != null || ifStatement.getCondition() == null) return false;
    PsiStatement thenBranch = ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
    return thenBranch instanceof PsiBreakStatement && ((PsiBreakStatement)thenBranch).getLabelIdentifier() == null;
  }
}
