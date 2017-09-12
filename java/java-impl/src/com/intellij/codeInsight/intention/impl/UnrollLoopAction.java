/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
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
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

public class UnrollLoopAction extends PsiElementBaseIntentionAction {
  private static final CallMatcher LIST_CONSTRUCTOR = CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_ARRAYS, "asList");

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull final PsiElement element) {
    PsiForeachStatement loop = PsiTreeUtil.getParentOfType(element, PsiForeachStatement.class);
    if (loop == null) return false;
    if (!(loop.getParent() instanceof PsiCodeBlock)) return false;
    PsiExpression iteratedValue = ExpressionUtils.resolveExpression(loop.getIteratedValue());
    if (extractExpressions(iteratedValue).length == 0) return false;
    PsiStatement[] statements = ControlFlowUtils.unwrapBlock(loop.getBody());
    if (statements.length == 0) return false;
    if (Arrays.stream(statements).anyMatch(PsiDeclarationStatement.class::isInstance)) return false;
    if (isBreakChain(loop)) {
      statements = Arrays.copyOfRange(statements, 0, statements.length - 1);
    }
    for (PsiStatement statement : statements) {
      boolean acceptable = PsiTreeUtil.processElements(statement, e -> {
        if (e instanceof PsiBreakStatement && ((PsiBreakStatement)e).findExitedStatement() == loop) return false;
        if (e instanceof PsiContinueStatement && ((PsiContinueStatement)e).findContinuedStatement() == loop) return false;
        return true;
      });
      if (!acceptable) return false;
    }
    return true;
  }

  @NotNull
  private static PsiExpression[] extractExpressions(PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression instanceof PsiArrayInitializerExpression) {
      return ((PsiArrayInitializerExpression)expression).getInitializers();
    }
    if (expression instanceof PsiNewExpression) {
      PsiArrayInitializerExpression initializer = ((PsiNewExpression)expression).getArrayInitializer();
      return initializer == null ? PsiExpression.EMPTY_ARRAY : initializer.getInitializers();
    }
    if (expression instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
      if (LIST_CONSTRUCTOR.test(call) && MethodCallUtils.isVarArgCall(call)) {
        return call.getArgumentList().getExpressions();
      }
    }
    return PsiExpression.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.unroll.loop.family");
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    PsiForeachStatement loop = PsiTreeUtil.getParentOfType(element, PsiForeachStatement.class);
    if (loop == null) return;
    if (!(loop.getParent() instanceof PsiCodeBlock)) return;
    boolean breakChain = isBreakChain(loop);
    PsiExpression iteratedValue = loop.getIteratedValue();
    PsiExpression[] expressions = extractExpressions(ExpressionUtils.resolveExpression(iteratedValue));
    if (expressions.length == 0) return;
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    CommentTracker ct = new CommentTracker();
    PsiElement anchor = loop;
    for (PsiExpression expression : expressions) {
      PsiForeachStatement copy = (PsiForeachStatement)factory.createStatementFromText(ct.text(loop), element);
      PsiParameter parameter = copy.getIterationParameter();
      for (PsiReference reference : ReferencesSearch.search(parameter, new LocalSearchScope(copy))) {
        final PsiElement referenceElement = reference.getElement();
        if (referenceElement instanceof PsiJavaCodeReferenceElement) {
          ct.markUnchanged(expression);
          InlineUtil.inlineVariable(parameter, expression, (PsiJavaCodeReferenceElement)referenceElement);
        }
      }
      PsiStatement body = copy.getBody();
      assert body != null;
      if (body instanceof PsiBlockStatement) {
        PsiElement[] children = ((PsiBlockStatement)body).getCodeBlock().getChildren();
        PsiElement parent = anchor.getParent();
        PsiElement currentAnchor = anchor;
        // Skip {braces}
        Arrays.stream(children, 1, children.length - 1).forEach(child -> parent.addBefore(child, currentAnchor));
      }
      if (breakChain) {
        PsiStatement lastStatement = PsiTreeUtil.getPrevSiblingOfType(anchor, PsiStatement.class);
        if (lastStatement instanceof PsiIfStatement) {
          PsiIfStatement ifStatement = (PsiIfStatement)lastStatement;
          PsiExpression condition = Objects.requireNonNull(ifStatement.getCondition());
          PsiStatement thenBranch = Objects.requireNonNull(ifStatement.getThenBranch());
          String negated = BoolUtils.getNegatedExpressionText(condition);
          condition.replace(factory.createExpressionFromText(negated, condition));
          PsiBlockStatement block = (PsiBlockStatement)thenBranch.replace(factory.createStatementFromText("{}", lastStatement));
          anchor = block.getCodeBlock().getLastChild();
        }
      }
    }
    PsiLocalVariable variable = ExpressionUtils.resolveLocalVariable(iteratedValue);
    if (variable != null) ct.delete(variable);
    ct.deleteAndRestoreComments(loop);
  }

  /**
   * @param loop loop to test
   * @return true if the last statement is "if(...) break"
   */
  private static boolean isBreakChain(PsiForeachStatement loop) {
    PsiStatement lastStatement = loop.getBody();
    if (lastStatement instanceof PsiBlockStatement) {
      lastStatement = ControlFlowUtils.getLastStatementInBlock(((PsiBlockStatement)lastStatement).getCodeBlock());
    }
    if (!(lastStatement instanceof PsiIfStatement)) return false;
    PsiIfStatement ifStatement = (PsiIfStatement)lastStatement;
    return ifStatement.getElseBranch() == null &&
           ifStatement.getCondition() != null &&
           ControlFlowUtils.statementBreaksLoop(ControlFlowUtils.stripBraces(ifStatement.getThenBranch()), loop);
  }
}
