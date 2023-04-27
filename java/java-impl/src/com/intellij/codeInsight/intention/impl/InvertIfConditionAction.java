// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class InvertIfConditionAction extends PsiElementBaseIntentionAction {
  private static final Logger LOG = Logger.getInstance(InvertIfConditionAction.class);

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {

    int offset = editor.getCaretModel().getOffset();
    final PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PsiIfStatement.class);
    if (ifStatement == null) return false;
    final PsiExpression condition = ifStatement.getCondition();
    if (condition == null) return false;
    if (ifStatement.getThenBranch() == null) return false;
    if (SyntaxTraverser.psiTraverser(condition)
          .filter(PsiPrefixExpression.class)
          .filter(prefixExpr -> PsiUtil.skipParenthesizedExprDown(prefixExpr.getOperand()) == null)
          .first() != null) return false;
    if (element instanceof PsiKeyword) {
      if (element.getParent() != ifStatement) {
        return false;
      }
      final IElementType tokenType = ((PsiKeyword)element).getTokenType();
      if (tokenType != JavaTokenType.IF_KEYWORD && tokenType != JavaTokenType.ELSE_KEYWORD) {
        return false;
      }
    }
    else {
      final TextRange condTextRange = condition.getTextRange();
      if (condTextRange == null || !condTextRange.contains(offset)) {
        return false;
      }
    }
    PsiElement block = findCodeBlock(ifStatement);
    if (block == null) {
      return false;
    }
    if (PsiUtil.skipParenthesizedExprDown(ifStatement.getCondition()) == null) {
      return true;
    }
    // check that the code structure isn't broken completely
    ControlFlow localFlow = buildControlFlow(block);
    int startThenOffset = getThenOffset(localFlow, ifStatement);
    int afterIfOffset = localFlow.getEndOffset(ifStatement);
    return startThenOffset >= 0 && afterIfOffset >= 0;
  }

  @Override
  @NotNull
  public String getText() {
    return getFamilyName();
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return JavaBundle.message("intention.invert.if.condition");
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PsiIfStatement.class);

    LOG.assertTrue(ifStatement != null);
    PsiElement block = findCodeBlock(ifStatement);
    LOG.assertTrue(block != null);
    ControlFlow controlFlow = buildControlFlow(block);
    ifStatement = setupBranches(ifStatement, controlFlow);

    if (!ifStatement.isValid()) return;

    PsiExpression condition = Objects.requireNonNull(ifStatement.getCondition());
    final CommentTracker tracker = new CommentTracker();
    final String negatedCondition = BoolUtils.getNegatedExpressionText(condition, tracker);
    tracker.replaceAndRestoreComments(condition, negatedCondition);

    formatIf(ifStatement);
  }

  private static void formatIf(PsiIfStatement ifStatement) throws IncorrectOperationException {
    final Project project = ifStatement.getProject();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);

    PsiElement thenBranch = Objects.requireNonNull(ifStatement.getThenBranch()).copy();
    PsiElement elseBranch = ifStatement.getElseBranch() != null ? ifStatement.getElseBranch().copy() : null;
    PsiElement condition = Objects.requireNonNull(ifStatement.getCondition()).copy();

    final CodeStyleManager codeStyle = CodeStyleManager.getInstance(project);

    PsiBlockStatement codeBlock = (PsiBlockStatement)factory.createStatementFromText("{}", ifStatement);
    codeBlock = (PsiBlockStatement)codeStyle.reformat(codeBlock);

    ifStatement.getThenBranch().replace(codeBlock);
    if (elseBranch != null) {
      ifStatement.getElseBranch().replace(codeBlock);
    }
    ifStatement.getCondition().replace(factory.createExpressionFromText("true", null));
    ifStatement = (PsiIfStatement)codeStyle.reformat(ifStatement);

    if (!(thenBranch instanceof PsiBlockStatement)) {
      PsiBlockStatement codeBlock1 = (PsiBlockStatement)Objects.requireNonNull(ifStatement.getThenBranch()).replace(codeBlock);
      codeBlock1 = (PsiBlockStatement)codeStyle.reformat(codeBlock1);
      codeBlock1.getCodeBlock().add(thenBranch);
    }
    else {
      Objects.requireNonNull(ifStatement.getThenBranch()).replace(thenBranch);
    }

    if (elseBranch != null) {
      if (!(elseBranch instanceof PsiBlockStatement)) {
        PsiBlockStatement codeBlock1 = (PsiBlockStatement)Objects.requireNonNull(ifStatement.getElseBranch()).replace(codeBlock);
        codeBlock1 = (PsiBlockStatement)codeStyle.reformat(codeBlock1);
        codeBlock1.getCodeBlock().add(elseBranch);
      }
      else {
        elseBranch = Objects.requireNonNull(ifStatement.getElseBranch()).replace(elseBranch);

        if (emptyBlock(((PsiBlockStatement)elseBranch).getCodeBlock())) {
          PsiElement parent = ifStatement.getParent();
          if (parent instanceof PsiIfStatement && ((PsiIfStatement)parent).getElseBranch() != null) {
            ifStatement = (PsiIfStatement)wrapWithCodeBlock(ifStatement);
          }
          new CommentTracker().deleteAndRestoreComments(Objects.requireNonNull(ifStatement.getElseBranch()));
        }
      }
    }

    Objects.requireNonNull(ifStatement.getCondition()).replace(condition);
  }

  private static boolean emptyBlock (PsiCodeBlock block) {
    PsiElement[] children = block.getChildren();
    for (PsiElement child : children) {
      if (child instanceof PsiComment) return false;
      if (!(child instanceof PsiWhiteSpace) && !(child instanceof PsiJavaToken)) return false;
    }
    return true;
  }

  private static PsiElement findCodeBlock(PsiIfStatement ifStatement) {
    PsiElement e = PsiTreeUtil.getParentOfType(ifStatement, PsiMethod.class, PsiClassInitializer.class, PsiLambdaExpression.class);
    if (e instanceof PsiMethod) return ((PsiMethod) e).getBody();
    if (e instanceof PsiLambdaExpression) return ((PsiLambdaExpression)e).getBody();
    if (e instanceof PsiClassInitializer) return ((PsiClassInitializer) e).getBody();
    return null;
  }

  @NotNull
  private static ControlFlow buildControlFlow(@Nullable PsiElement element) {
    if (element == null) {
      return ControlFlow.EMPTY;
    }
    try {
      return ControlFlowFactory.getInstance(element.getProject()).getControlFlow(element, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(), false);
    }
    catch (AnalysisCanceledException e) {
      return ControlFlow.EMPTY;
    }
  }

  private static PsiIfStatement setupBranches(PsiIfStatement ifStatement, ControlFlow flow) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(ifStatement.getProject());
    Project project = ifStatement.getProject();

    CommentTracker ct = new CommentTracker();
    PsiStatement thenBranch = Objects.requireNonNull(ifStatement.getThenBranch());
    PsiStatement elseBranch = ifStatement.getElseBranch();

    if (elseBranch != null) {
      elseBranch = (PsiStatement) elseBranch.copy();
      setElseBranch(ifStatement, thenBranch, flow, ct);
      ct.replaceAndRestoreComments(ifStatement.getThenBranch(), elseBranch);
      return ifStatement;
    }

    final CodeStyleManager codeStyle = CodeStyleManager.getInstance(project);
    if (flow.getSize() == 0) {
      ifStatement.setElseBranch(thenBranch);
      PsiStatement statement = factory.createStatementFromText("{}", ifStatement);
      statement = (PsiStatement) codeStyle.reformat(statement);
      statement = (PsiStatement) ct.replaceAndRestoreComments(ifStatement.getThenBranch(), statement);
      codeStyle.reformat(statement);
      return ifStatement;
    }

    int endOffset = calcEndOffset(flow, ifStatement);

    LOG.assertTrue(endOffset >= 0);

    if (endOffset >= flow.getSize()) {
      PsiStatement statement = factory.createStatementFromText("return;", ifStatement);
      statement = (PsiStatement) codeStyle.reformat(statement);
      if (thenBranch instanceof PsiBlockStatement) {
        if (ifStatement.getParent() instanceof PsiIfStatement) {
          ifStatement = (PsiIfStatement)wrapWithCodeBlock(ifStatement);
          thenBranch = ifStatement.getThenBranch();
          assert thenBranch != null;
        }
        PsiCodeBlock codeBlock = ((PsiBlockStatement)thenBranch).getCodeBlock();
        PsiElement firstElement = codeBlock.getFirstBodyElement();
        PsiElement lastElement = codeBlock.getLastBodyElement();
        if (firstElement != null && lastElement != null) {
          ifStatement.getParent().addRangeAfter(firstElement, lastElement, ifStatement);
          ct.markRangeUnchanged(firstElement, lastElement);
        }
      } else {
        if (!(thenBranch instanceof PsiReturnStatement)) {
          ifStatement = addAfterWithinCodeBlock(ifStatement, ct.markUnchanged(thenBranch), ct);
        }
      }
      ct.replaceAndRestoreComments(Objects.requireNonNull(ifStatement.getThenBranch()), statement);
      return ifStatement;
    }
    PsiElement element = flow.getElement(endOffset);
    while (element != null && !(element instanceof PsiStatement)) element = element.getParent();

    if (element != null && element.getParent() instanceof PsiForStatement && ((PsiForStatement)element.getParent()).getUpdate() == element ||
        element instanceof PsiWhileStatement && flow.getStartOffset(element) == endOffset && PsiTreeUtil.isAncestor(element, ifStatement, true) ||
        element instanceof PsiForeachStatement && flow.getStartOffset(element) + 1 == endOffset) {
      PsiStatement statement = factory.createStatementFromText("continue;", ifStatement);
      statement = (PsiStatement)codeStyle.reformat(statement);
      ifStatement = addAfterWithinCodeBlock(ifStatement, ct.markUnchanged(thenBranch), ct);
      Objects.requireNonNull(ifStatement.getThenBranch()).replace(statement);
      return ifStatement;
    }

    if (element instanceof PsiReturnStatement returnStatement) {
      ifStatement = addAfterWithinCodeBlock(ifStatement, thenBranch, ct);
      ct.replaceAndRestoreComments(Objects.requireNonNull(ifStatement.getThenBranch()), ct.markUnchanged(returnStatement).copy());

      ControlFlow flow2 = buildControlFlow(findCodeBlock(ifStatement));
      if (!ControlFlowUtil.isInstructionReachable(flow2, flow2.getStartOffset(returnStatement), 0)) returnStatement.delete();
      return ifStatement;
    }

    boolean nextUnreachable = flow.getEndOffset(ifStatement) == flow.getSize();
    if (!nextUnreachable) {
      PsiElement parent = ifStatement.getParent();
      if (parent != null) {
        if (!(parent instanceof PsiCodeBlock)) {
          ifStatement = (PsiIfStatement)wrapWithCodeBlock(ifStatement);
          parent = ifStatement.getParent();
          thenBranch = ifStatement.getThenBranch();
        }
        ControlFlow localFlow = buildControlFlow(parent);
        int startThenOffset = getThenOffset(localFlow, ifStatement);
        int afterIfOffset = localFlow.getEndOffset(ifStatement);
        nextUnreachable = !ControlFlowUtil.isInstructionReachable(localFlow, afterIfOffset, startThenOffset);
      }
    }
    if (nextUnreachable) {
      setElseBranch(ifStatement, thenBranch, flow, ct);

      PsiElement first = PsiTreeUtil.skipWhitespacesForward(ifStatement);
      if (first != null) {
        PsiElement last = first;
        PsiElement next = last.getNextSibling();
        while (next != null && !(next instanceof PsiSwitchLabelStatement)) {
          last = next;
          next = next.getNextSibling();
        }
        while (first != last && (last instanceof PsiWhiteSpace || PsiUtil.isJavaToken(last, JavaTokenType.RBRACE)))
          last = last.getPrevSibling();


        PsiBlockStatement codeBlock = (PsiBlockStatement) factory.createStatementFromText("{}", ifStatement);
        if (first == last && PsiUtil.isJavaToken(last, JavaTokenType.RBRACE)) {
          ct.replaceAndRestoreComments(ifStatement.getThenBranch(), codeBlock);
        }
        else {
          codeBlock.getCodeBlock().addRange(first, last);
          ct.replaceAndRestoreComments(ifStatement.getThenBranch(), codeBlock);
          first.getParent().deleteChildRange(first, last);
        }
      }
      codeStyle.reformat(ifStatement);
      return ifStatement;
    }

    setElseBranch(ifStatement, thenBranch, flow, ct);
    PsiStatement statement = factory.createStatementFromText("{}", ifStatement);
    statement = (PsiStatement) codeStyle.reformat(statement);
    statement = (PsiStatement)ct.replaceAndRestoreComments(Objects.requireNonNull(ifStatement.getThenBranch()), statement);
    codeStyle.reformat(statement);
    return ifStatement;
  }

  private static void setElseBranch(PsiIfStatement ifStatement,
                                    PsiStatement thenBranch,
                                    ControlFlow flow,
                                    CommentTracker ct)
    throws IncorrectOperationException {
    if (flow.getEndOffset(ifStatement) == flow.getEndOffset(thenBranch)) {
      final PsiLoopStatement loopStmt = PsiTreeUtil.getParentOfType(ifStatement, PsiLoopStatement.class);
      if (loopStmt != null) {
        final PsiStatement body = loopStmt.getBody();
        if (body instanceof PsiBlockStatement) {
          final PsiStatement[] statements = ((PsiBlockStatement)body).getCodeBlock().getStatements();
          if (statements.length > 0 && !PsiTreeUtil.isAncestor(statements[statements.length - 1], ifStatement, false) &&
              ArrayUtilRt.find(statements, ifStatement) < 0) {
            ifStatement.setElseBranch(ct.markUnchanged(thenBranch));
            return;
          }
        }
      }
      if (thenBranch instanceof PsiContinueStatement || 
          thenBranch instanceof PsiReturnStatement && ((PsiReturnStatement)thenBranch).getReturnValue() == null) {
        PsiStatement elseBranch = ifStatement.getElseBranch();
        if (elseBranch != null) {
          elseBranch.delete();
        }
        return;
      }
      if (thenBranch instanceof PsiBlockStatement) {
        PsiStatement[] statements = ((PsiBlockStatement) thenBranch).getCodeBlock().getStatements();
        if (statements.length > 0 && statements[statements.length - 1] instanceof PsiContinueStatement) {
          new CommentTracker().deleteAndRestoreComments(statements[statements.length - 1]);
        }
      }
    }
    ifStatement.setElseBranch(ct.markUnchanged(thenBranch));
  }

  private static PsiStatement wrapWithCodeBlock(@NotNull PsiStatement statement) {
    final Project project = statement.getProject();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    final CodeStyleManager codeStyle = CodeStyleManager.getInstance(project);
    PsiIfStatement ifStatement = (PsiIfStatement)factory.createStatementFromText("if (true) {}", statement);
    ifStatement = (PsiIfStatement)codeStyle.reformat(ifStatement);
    PsiStatement thenBranch = ifStatement.getThenBranch();
    assert thenBranch instanceof PsiBlockStatement;
    ((PsiBlockStatement)thenBranch).getCodeBlock().add(statement);
    PsiCodeBlock stmt = ((PsiBlockStatement)statement.replace(thenBranch)).getCodeBlock();
    return stmt.getStatements()[0];
  }

  private static PsiIfStatement addAfterWithinCodeBlock(@NotNull PsiIfStatement ifStatement,
                                                        @NotNull PsiStatement branch,
                                                        CommentTracker ct) {
    final PsiElement parent = ifStatement.getParent();
    if (parent != null && !(parent instanceof PsiCodeBlock)) {
      branch = (PsiStatement)branch.copy();
      ifStatement = (PsiIfStatement)wrapWithCodeBlock(ifStatement);
    }
    addAfter(ifStatement, branch, ct);
    return ifStatement;
  }

  static void addAfter(PsiIfStatement ifStatement, PsiStatement branch, CommentTracker ct) throws IncorrectOperationException {
    if (branch instanceof PsiBlockStatement blockStatement) {
      final PsiCodeBlock block = blockStatement.getCodeBlock();
      final PsiElement firstBodyElement = block.getFirstBodyElement();
      final PsiElement lastBodyElement = block.getLastBodyElement();
      if (firstBodyElement != null && lastBodyElement != null) {
        ct.markRangeUnchanged(firstBodyElement, lastBodyElement);
        ifStatement.getParent().addRangeAfter(firstBodyElement, lastBodyElement, ifStatement);
      }
    } else {
      ifStatement.getParent().addAfter(ct.markUnchanged(branch), ifStatement);
    }
  }

  private static int getThenOffset(ControlFlow controlFlow, PsiIfStatement ifStatement) {
    PsiStatement thenBranch = ifStatement.getThenBranch();

    for (int i = 0; i < controlFlow.getSize(); i++) {
      if (PsiTreeUtil.isAncestor(thenBranch, controlFlow.getElement(i), false)) return i;
    }
    return -1;
  }

  private static int calcEndOffset(ControlFlow controlFlow, PsiIfStatement ifStatement) {
    int endOffset = -1;

    List<Instruction> instructions = controlFlow.getInstructions();
    for (int i = 0; i < instructions.size(); i++) {
      Instruction instruction = instructions.get(i);
      if (controlFlow.getElement(i) != ifStatement) continue;

      if (instruction instanceof GoToInstruction) {
        GoToInstruction goToInstruction = (GoToInstruction)instruction;
        if (goToInstruction.role != BranchingInstruction.Role.END) continue;

        endOffset = goToInstruction.offset;
        break;
      }
      else if (instruction instanceof ConditionalGoToInstruction) {
        ConditionalGoToInstruction goToInstruction = (ConditionalGoToInstruction)instruction;
        if (goToInstruction.role != BranchingInstruction.Role.END) continue;

        endOffset = goToInstruction.offset;
        break;
      }
    }
    if (endOffset == -1) {
      endOffset = controlFlow.getSize();
    }
    while (endOffset < instructions.size() && instructions.get(endOffset) instanceof GoToInstruction &&
           !((GoToInstruction) instructions.get(endOffset)).isReturn &&
           !(controlFlow.getElement(endOffset) instanceof PsiYieldStatement) &&
           !(controlFlow.getElement(endOffset) instanceof PsiBreakStatement) &&
           !(controlFlow.getElement(endOffset) instanceof PsiContinueStatement)) {
      endOffset = ((BranchingInstruction)instructions.get(endOffset)).offset;
    }

    return endOffset;
  }

}
