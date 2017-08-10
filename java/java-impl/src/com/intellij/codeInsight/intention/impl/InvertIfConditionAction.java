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
import com.intellij.codeInsight.CodeInsightServicesUtil;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author mike
 */
public class InvertIfConditionAction extends PsiElementBaseIntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.InvertIfConditionAction");

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
      PsiKeyword keyword = (PsiKeyword) element;
      if ((keyword.getTokenType() == JavaTokenType.IF_KEYWORD || keyword.getTokenType() == JavaTokenType.ELSE_KEYWORD)
          && keyword.getParent() == ifStatement) {
        return true;
      }
    }
    final TextRange condTextRange = condition.getTextRange();
    if (condTextRange == null) return false;
    if (!condTextRange.contains(offset)) return false;
    PsiElement block = findCodeBlock(ifStatement);
    return block != null;
  }

  @Override
  @NotNull
  public String getText() {
    return getFamilyName();
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.invert.if.condition");
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PsiIfStatement.class);

    LOG.assertTrue(ifStatement != null);
    PsiElement block = findCodeBlock(ifStatement);

    ControlFlow controlFlow = buildControlFlow(block);

    PsiExpression condition = (PsiExpression) ifStatement.getCondition().copy();

    ifStatement = setupBranches(ifStatement, controlFlow);
    if (condition != null) {
      ifStatement.getCondition().replace(CodeInsightServicesUtil.invertCondition(condition));
    }

    formatIf(ifStatement);
  }

  private static void formatIf(PsiIfStatement ifStatement) throws IncorrectOperationException {
    final Project project = ifStatement.getProject();
    PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();

    PsiElement thenBranch = ifStatement.getThenBranch().copy();
    PsiElement elseBranch = ifStatement.getElseBranch() != null ? ifStatement.getElseBranch().copy() : null;
    PsiElement condition = ifStatement.getCondition().copy();

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
      PsiBlockStatement codeBlock1 = (PsiBlockStatement)ifStatement.getThenBranch().replace(codeBlock);
      codeBlock1 = (PsiBlockStatement)codeStyle.reformat(codeBlock1);
      codeBlock1.getCodeBlock().add(thenBranch);
    }
    else {
      ifStatement.getThenBranch().replace(thenBranch);
    }

    if (elseBranch != null) {
      if (!(elseBranch instanceof PsiBlockStatement)) {
        PsiBlockStatement codeBlock1 = (PsiBlockStatement)ifStatement.getElseBranch().replace(codeBlock);
        codeBlock1 = (PsiBlockStatement)codeStyle.reformat(codeBlock1);
        codeBlock1.getCodeBlock().add(elseBranch);
      }
      else {
        elseBranch = ifStatement.getElseBranch().replace(elseBranch);

        if (emptyBlock(((PsiBlockStatement)elseBranch).getCodeBlock())) {
          ifStatement.getElseBranch().delete();
        }
      }
    }

    ifStatement.getCondition().replace(condition);
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

  private static ControlFlow buildControlFlow(PsiElement element) {
    try {
      return ControlFlowFactory.getInstance(element.getProject()).getControlFlow(element, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(), false);
    }
    catch (AnalysisCanceledException e) {
      return ControlFlow.EMPTY;
    }
  }

  private static PsiIfStatement setupBranches(PsiIfStatement ifStatement, ControlFlow flow) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(ifStatement.getProject()).getElementFactory();
    Project project = ifStatement.getProject();

    PsiStatement thenBranch = ifStatement.getThenBranch();
    PsiStatement elseBranch = ifStatement.getElseBranch();

    if (elseBranch != null) {
      elseBranch = (PsiStatement) elseBranch.copy();
      setElseBranch(ifStatement, thenBranch, flow);
      ifStatement.getThenBranch().replace(elseBranch);
      return ifStatement;
    }

    final CodeStyleManager codeStyle = CodeStyleManager.getInstance(project);
    if (flow.getSize() == 0) {
      ifStatement.setElseBranch(thenBranch);
      PsiStatement statement = factory.createStatementFromText("{}", ifStatement);
      statement = (PsiStatement) codeStyle.reformat(statement);
      statement = (PsiStatement) ifStatement.getThenBranch().replace(statement);
      codeStyle.reformat(statement);
      return ifStatement;
    }

    int endOffset = calcEndOffset(flow, ifStatement);

    LOG.assertTrue(endOffset >= 0);

    if (endOffset >= flow.getSize()) {
      PsiStatement statement = factory.createStatementFromText("return;", ifStatement);
      statement = (PsiStatement) codeStyle.reformat(statement);
      if (thenBranch instanceof PsiBlockStatement) {
        PsiStatement[] statements = ((PsiBlockStatement) thenBranch).getCodeBlock().getStatements();
        if (statements.length > 0) {
          PsiElement firstElement = statements [0];
          while (firstElement.getPrevSibling() instanceof PsiWhiteSpace || firstElement.getPrevSibling() instanceof PsiComment) {
            firstElement = firstElement.getPrevSibling();
          }
          ifStatement.getParent().addRangeAfter(firstElement, statements[statements.length - 1], ifStatement);
        }
      } else {
        if (!(thenBranch instanceof PsiReturnStatement)) {
          ifStatement = addAfterWithinCodeBlock(ifStatement, thenBranch);
        }
      }
      ifStatement.getThenBranch().replace(statement);
      return ifStatement;
    }
    PsiElement element = flow.getElement(endOffset);
    while (element != null && !(element instanceof PsiStatement)) element = element.getParent();

    if (element != null && element.getParent() instanceof PsiForStatement && ((PsiForStatement)element.getParent()).getUpdate() == element ||
        element instanceof PsiWhileStatement && flow.getStartOffset(element) == endOffset && PsiTreeUtil.isAncestor(element, ifStatement, true) ||
        element instanceof PsiForeachStatement && flow.getStartOffset(element) + 1 == endOffset) {
      PsiStatement statement = factory.createStatementFromText("continue;", ifStatement);
      statement = (PsiStatement)codeStyle.reformat(statement);
      ifStatement = addAfterWithinCodeBlock(ifStatement, thenBranch);
      ifStatement.getThenBranch().replace(statement);
      return ifStatement;
    }

    if (element instanceof PsiReturnStatement) {
      PsiReturnStatement returnStatement = (PsiReturnStatement) element;
      ifStatement = addAfterWithinCodeBlock(ifStatement, thenBranch);
      ifStatement.getThenBranch().replace(returnStatement.copy());

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
      setElseBranch(ifStatement, thenBranch, flow);

      PsiElement first = ifStatement.getNextSibling();
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
        codeBlock.getCodeBlock().addRange(first, last);
        first.getParent().deleteChildRange(first, last);
        ifStatement.getThenBranch().replace(codeBlock);
      }
      codeStyle.reformat(ifStatement);
      return ifStatement;
    }

    setElseBranch(ifStatement, thenBranch, flow);
    PsiStatement statement = factory.createStatementFromText("{}", ifStatement);
    statement = (PsiStatement) codeStyle.reformat(statement);
    statement = (PsiStatement) ifStatement.getThenBranch().replace(statement);
    codeStyle.reformat(statement);
    return ifStatement;
  }

  private static void setElseBranch(PsiIfStatement ifStatement, PsiStatement thenBranch, ControlFlow flow)
    throws IncorrectOperationException {
    if (flow.getEndOffset(ifStatement) == flow.getEndOffset(thenBranch)) {
      final PsiLoopStatement loopStmt = PsiTreeUtil.getParentOfType(ifStatement, PsiLoopStatement.class);
      if (loopStmt != null) {
        final PsiStatement body = loopStmt.getBody();
        if (body instanceof PsiBlockStatement) {
          final PsiStatement[] statements = ((PsiBlockStatement)body).getCodeBlock().getStatements();
          if (statements.length > 0 && !PsiTreeUtil.isAncestor(statements[statements.length - 1], ifStatement, false) &&
              ArrayUtilRt.find(statements, ifStatement) < 0) {
            ifStatement.setElseBranch(thenBranch);
            return;
          }
        }
      }
      if (thenBranch instanceof PsiContinueStatement) {
        PsiStatement elseBranch = ifStatement.getElseBranch();
        if (elseBranch != null) {
          elseBranch.delete();
        }
        return;
      }
      else if (thenBranch instanceof PsiBlockStatement) {
        PsiStatement[] statements = ((PsiBlockStatement) thenBranch).getCodeBlock().getStatements();
        if (statements.length > 0 && statements[statements.length - 1] instanceof PsiContinueStatement) {
          statements[statements.length - 1].delete();
        }
      }
    }
    ifStatement.setElseBranch(thenBranch);
  }

  private static PsiStatement wrapWithCodeBlock(@NotNull PsiStatement statement) {
    final Project project = statement.getProject();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    final CodeStyleManager codeStyle = CodeStyleManager.getInstance(project);
    PsiIfStatement ifStatement = (PsiIfStatement)factory.createStatementFromText("if (true) {}", statement);
    ifStatement = (PsiIfStatement)codeStyle.reformat(ifStatement);
    PsiStatement thenBranch = ifStatement.getThenBranch();
    ((PsiBlockStatement)thenBranch).getCodeBlock().add(statement);
    PsiCodeBlock stmt = ((PsiBlockStatement)statement.replace(thenBranch)).getCodeBlock();
    return stmt.getStatements()[0];
  }

  private static PsiIfStatement addAfterWithinCodeBlock(@NotNull PsiIfStatement ifStatement, @NotNull PsiStatement branch) {
    final PsiElement parent = ifStatement.getParent();
    if (parent != null && !(parent instanceof PsiCodeBlock)) {
      branch = (PsiStatement)branch.copy();
      ifStatement = (PsiIfStatement)wrapWithCodeBlock(ifStatement);
    }
    addAfter(ifStatement, branch);
    return ifStatement;
  }

  static void addAfter(PsiIfStatement ifStatement, PsiStatement branch) throws IncorrectOperationException {
    if (branch instanceof PsiBlockStatement) {
      PsiBlockStatement blockStatement = (PsiBlockStatement) branch;
      final PsiCodeBlock block = blockStatement.getCodeBlock();
      final PsiElement firstBodyElement = block.getFirstBodyElement();
      final PsiElement lastBodyElement = block.getLastBodyElement();
      if (firstBodyElement != null && lastBodyElement != null) {
        ifStatement.getParent().addRangeAfter(firstBodyElement, lastBodyElement, ifStatement);
      }
    } else {
      ifStatement.getParent().addAfter(branch, ifStatement);
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
           !((GoToInstruction) instructions.get(endOffset)).isReturn && !(controlFlow.getElement(endOffset) instanceof PsiBreakStatement)) {
      endOffset = ((BranchingInstruction)instructions.get(endOffset)).offset;
    }

    return endOffset;
  }

}
