// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.tryCast;

public class LoopTransformationAction extends PsiElementBaseIntentionAction {
  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.transform.loop.family.name");
  }

  @NotNull
  @Override
  public String getText() {
    return CodeInsightBundle.message("intention.transform.loop.family.name");
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    Context context = Context.from(element);
    if(context == null) return;
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    if (context.myConditionInTheBeginning) {
      PsiExpression whileCondition = context.myWhileStatement.getCondition();
      if(whileCondition == null) return;
      String negatedText = BoolUtils.getNegatedExpressionText((PsiExpression)context.myCondition.copy());
      whileCondition.replace(factory.createExpressionFromText(negatedText, context.myCondition));
      context.myConditionStatement.delete();
    } else {
      PsiExpression condition = (PsiExpression)context.myCondition.copy();
      context.myConditionStatement.delete();
      PsiStatement body = context.myWhileStatement.getBody();
      if(body == null) return;
      PsiStatement[] statements = ControlFlowUtils.unwrapBlock((PsiStatement)body.copy());
      String doWhileText = "do{}while(" + BoolUtils.getNegatedExpressionText(condition) + ");";
      PsiStatement statement = factory.createStatementFromText(doWhileText, condition);
      PsiDoWhileStatement doWhileStatement = (PsiDoWhileStatement)context.myWhileStatement.replace(statement);
      PsiBlockStatement blockStatement = (PsiBlockStatement)doWhileStatement.getBody();
      assert blockStatement != null;
      PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
      codeBlock.addRangeAfter(statements[0], statements[statements.length - 1], codeBlock.getLBrace());
    }
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    return Context.from(element) != null;
  }

  private static class Context {
    final PsiWhileStatement myWhileStatement;
    final PsiExpression myCondition;
    final PsiStatement myConditionStatement;
    final boolean myConditionInTheBeginning;

    public Context(PsiWhileStatement whileStatement,
                   PsiExpression condition,
                   PsiStatement statement,
                   boolean conditionInTheBeginning) {
      myWhileStatement = whileStatement;
      myCondition = condition;
      myConditionStatement = statement;
      myConditionInTheBeginning = conditionInTheBeginning;
    }

    @Nullable
    static Context from(@NotNull PsiElement element) {
      PsiJavaToken token = tryCast(element, PsiJavaToken.class);
      if(token == null || !token.getTokenType().equals(JavaTokenType.WHILE_KEYWORD)) return null;
      PsiWhileStatement whileStatement = tryCast(element.getParent(), PsiWhileStatement.class);
      if(whileStatement == null) return null;
      PsiExpression condition = whileStatement.getCondition();
      if(!BoolUtils.isTrue(condition)) return null;
      PsiStatement[] statements = ControlFlowUtils.unwrapBlock(whileStatement.getBody());
      if(statements.length < 2) return null;
      PsiStatement first = statements[0];
      PsiExpression firstBreakCondition = extractBreakCondition(first);
      if(firstBreakCondition != null) {
        return new Context(whileStatement, firstBreakCondition, first, true);
      }
      PsiStatement last = statements[statements.length - 1];
      PsiExpression lastBreakCondition = extractBreakCondition(last);
      if(lastBreakCondition != null) {
        if(StreamEx.of(statements)
          .flatMap(statement -> StreamEx.ofTree((PsiElement)statement, el -> StreamEx.of(el.getChildren())))
          .anyMatch(e -> e instanceof PsiContinueStatement)) return null;
        boolean variablesInLoop = StreamEx.ofTree((PsiElement)lastBreakCondition, e -> StreamEx.of(e.getChildren()))
          .select(PsiReferenceExpression.class)
          .map(ref -> ref.resolve())
          .select(PsiLocalVariable.class)
          .anyMatch(var -> PsiTreeUtil.isAncestor(whileStatement, var, false));
        if(variablesInLoop) return null;
        return new Context(whileStatement, lastBreakCondition, last, false);
      }
      return null;
    }

    @Nullable
    static PsiExpression extractBreakCondition(@NotNull PsiStatement statement) {
      PsiIfStatement ifStatement = tryCast(statement, PsiIfStatement.class);
      if(ifStatement == null) return null;
      if(ifStatement.getElseBranch() != null) return null;
      PsiStatement thenBranch = ifStatement.getThenBranch();
      PsiBreakStatement breakStatement = tryCast(ControlFlowUtils.stripBraces(thenBranch), PsiBreakStatement.class);
      if(breakStatement == null || breakStatement.getLabelIdentifier() != null) return null;
      return ifStatement.getCondition();
    }
  }
}
