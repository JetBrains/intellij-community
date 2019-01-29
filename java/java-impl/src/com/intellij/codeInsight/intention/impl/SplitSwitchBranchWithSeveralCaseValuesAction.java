// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.util.PsiTreeUtil.getNextSiblingOfType;
import static com.intellij.psi.util.PsiTreeUtil.getPrevSiblingOfType;
import static com.intellij.util.ObjectUtils.notNull;
import static com.siyeh.ig.psiutils.ControlFlowUtils.statementMayCompleteNormally;

/**
 * @author Pavel.Dolgov
 */
public class SplitSwitchBranchWithSeveralCaseValuesAction extends PsiElementBaseIntentionAction {

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    PsiSwitchLabelStatementBase statement = findLabelStatement(editor, element);
    // traditional syntax "case 1: case 2: some code"
    if (statement instanceof PsiSwitchLabelStatement) {
      if (getPrevSiblingOfType(statement, PsiStatement.class) instanceof PsiSwitchLabelStatement ||
          getNextSiblingOfType(statement, PsiStatement.class) instanceof PsiSwitchLabelStatement) {

        PsiStatement lastSiblingLabel = findLastSiblingLabel(statement);
        PsiStatement lastStatement = findLastStatementInBranch(lastSiblingLabel);
        if (lastStatement != null && (!statementMayCompleteNormally(lastStatement) ||
                                      getNextSiblingOfType(lastStatement, PsiSwitchLabelStatement.class) == null)) {
          setText(CodeInsightBundle.message("intention.split.switch.branch.with.several.case.values.label.text"));
          return true;
        }
      }
    }
    // enhanced syntax "case 1, 2 -> some code"
    else if (statement instanceof PsiSwitchLabeledRuleStatement) {
      PsiExpressionList caseValues = statement.getCaseValues();
      if (caseValues != null && caseValues.getExpressionCount() > 1) {
        PsiStatement body = ((PsiSwitchLabeledRuleStatement)statement).getBody();
        if (body != null && element.getTextOffset() < body.getTextOffset()) {
          setText(CodeInsightBundle.message("intention.split.switch.branch.with.several.case.values.rule.text"));
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Handle the case where the caret is at the right side of the element we're interested in
   */
  @Nullable
  private static PsiElement getPreviousElement(@NotNull Editor editor, @NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    int caretOffset = editor.getCaretModel().getOffset();
    int elementOffset = element.getTextRange().getStartOffset();
    if (caretOffset == elementOffset && caretOffset > 0) {
      return file.findElementAt(caretOffset - 1);
    }
    return null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    PsiSwitchLabelStatementBase statement = findLabelStatement(editor, element);

    PsiElement result = null;
    if (statement instanceof PsiSwitchLabelStatement) {
      PsiSwitchLabelStatement labelStatement = (PsiSwitchLabelStatement)statement;
      PsiSwitchLabelStatement lastSibling = getLastSiblingLabel(labelStatement);
      if (lastSibling != null) {
        result = moveAfter(labelStatement, lastSibling);
      }
      else {
        PsiStatement previousSibling = getPrevSiblingOfType(statement, PsiStatement.class);
        if (previousSibling instanceof PsiSwitchLabelStatement) {
          result = copyTo(labelStatement, (PsiSwitchLabelStatement)previousSibling);
        }
      }
    }
    else if (statement instanceof PsiSwitchLabeledRuleStatement) {
      PsiSwitchLabeledRuleStatement labeledRule = (PsiSwitchLabeledRuleStatement)statement;
      PsiExpressionList caseValues = labeledRule.getCaseValues();
      if (caseValues != null) {
        PsiExpression caseValue = PsiTreeUtil.getNonStrictParentOfType(element, PsiExpression.class);
        if (!isInList(caseValue, caseValues)) {
          PsiElement previousElement = getPreviousElement(editor, element);
          caseValue = PsiTreeUtil.getNonStrictParentOfType(previousElement, PsiExpression.class);
        }

        if (isInList(caseValue, caseValues)) {
          result = moveAfter(caseValue, labeledRule);
        }
        else {
          result = splitAll(caseValues, labeledRule);
        }
      }
    }
    if (result != null) {
      editor.getCaretModel().moveToOffset(result.getTextOffset());
    }
  }

  @Contract("null,_ -> false")
  private static boolean isInList(@Nullable PsiExpression caseValue, @NotNull PsiExpressionList caseValues) {
    return caseValue != null && PsiTreeUtil.isAncestor(caseValues, caseValue, true);
  }

  @Nullable
  private static PsiSwitchLabelStatementBase findLabelStatement(@NotNull Editor editor,
                                                                @NotNull PsiElement element) {
    // Can't use BaseElementAtCaretIntentionAction, because there's ambiguity when the caret is after the list of case values before the arrow
    PsiStatement statement = PsiTreeUtil.getParentOfType(element, PsiStatement.class);
    if (statement instanceof PsiSwitchLabelStatementBase) {
      return (PsiSwitchLabelStatementBase)statement;
    }
    PsiElement previousElement = getPreviousElement(editor, element);
    statement = PsiTreeUtil.getParentOfType(previousElement, PsiStatement.class);
    return ObjectUtils.tryCast(statement, PsiSwitchLabelStatementBase.class);
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.split.switch.branch.with.several.case.values.family");
  }

  @Nullable
  private static PsiSwitchLabelStatement getLastSiblingLabel(@NotNull PsiSwitchLabelStatement original) {
    PsiSwitchLabelStatement result = null;
    for (PsiStatement next = getNextSiblingOfType(original, PsiStatement.class);
         next instanceof PsiSwitchLabelStatement;
         next = getNextSiblingOfType(next, PsiStatement.class)) {
      result = (PsiSwitchLabelStatement)next;
    }
    return result;
  }

  private static PsiElement moveAfter(@NotNull PsiSwitchLabelStatement labelToMove, @NotNull PsiSwitchLabelStatement anchor) {
    Branch branch = Branch.fromLabel(anchor);
    if (branch == null) {
      return null;
    }
    return branch.moveAfter(labelToMove);
  }

  private static PsiElement copyTo(@NotNull PsiSwitchLabelStatement labelToCopyFrom, @NotNull PsiSwitchLabelStatement anchor) {
    Branch branch = Branch.fromLabel(labelToCopyFrom);
    if (branch == null) return null;

    return branch.copyTo(anchor);
  }

  private static PsiSwitchLabeledRuleStatement moveAfter(@NotNull PsiExpression caseValue,
                                                         @NotNull PsiSwitchLabeledRuleStatement labeledRule) {
    CommentTracker tracker = new CommentTracker();

    Project project = labeledRule.getProject();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiSwitchBlock tempSwitch =
      (PsiSwitchBlock)factory.createStatementFromText("switch(1){case " + tracker.text(caseValue) + "->{}}", caseValue);
    PsiCodeBlock tempBody = notNull(tempSwitch.getBody());

    PsiSwitchLabeledRuleStatement newRule = (PsiSwitchLabeledRuleStatement)tempBody.getStatements()[0];
    newRule = (PsiSwitchLabeledRuleStatement)labeledRule.getParent().addAfter(newRule, labeledRule);
    newRule = (PsiSwitchLabeledRuleStatement)CodeStyleManager.getInstance(project).reformat(newRule);

    notNull(newRule.getBody()).replace(notNull(labeledRule.getBody()));

    tracker.deleteAndRestoreComments(caseValue);
    return newRule;
  }

  private static PsiSwitchLabeledRuleStatement splitAll(@NotNull PsiExpressionList caseValues,
                                                        @NotNull PsiSwitchLabeledRuleStatement labeledRule) {
    PsiExpression[] expressions = caseValues.getExpressions();
    PsiSwitchLabeledRuleStatement anchor = labeledRule;
    for (int i = 1; i < expressions.length; i++) {
      anchor = moveAfter(expressions[i], anchor);
    }
    return anchor;
  }

  @Nullable
  static PsiStatement findLastStatementInBranch(@Nullable PsiElement label) {
    PsiStatement lastStatement = null;
    for (PsiStatement statement = getNextSiblingOfType(label, PsiStatement.class);
         statement != null && !(statement instanceof PsiSwitchLabelStatement);
         statement = getNextSiblingOfType(statement, PsiStatement.class)) {
      lastStatement = statement;
    }
    return lastStatement;
  }

  @Nullable
  static PsiStatement findLastSiblingLabel(@Nullable PsiStatement statement) {
    PsiStatement lastSiblingLabel = null;
    for (PsiStatement next = statement; next instanceof PsiSwitchLabelStatement; next = getNextSiblingOfType(next, PsiStatement.class)) {
      lastSiblingLabel = next;
    }
    return lastSiblingLabel;
  }

  private static class Branch {
    private final PsiElement myFirstElement;
    private final PsiStatement myLastStatement;
    private final PsiCodeBlock myCodeBlock;
    private final PsiElement myLBrace;

    private Branch(@NotNull PsiElement firstElement,
                   @NotNull PsiStatement lastStatement,
                   @NotNull PsiCodeBlock codeBlock,
                   @NotNull PsiElement lBrace) {
      myFirstElement = firstElement;
      myLastStatement = lastStatement;
      myCodeBlock = codeBlock;
      myLBrace = lBrace;
    }

    @Nullable
    static Branch fromLabel(@NotNull PsiSwitchLabelStatement label) {
      PsiCodeBlock codeBlock = ObjectUtils.tryCast(label.getParent(), PsiCodeBlock.class);
      if (codeBlock == null) {
        return null;
      }
      PsiElement lBrace = codeBlock.getLBrace(), rBrace = codeBlock.getRBrace();
      if (lBrace == null || rBrace == null) {
        return null;
      }

      PsiElement firstElement = label.getNextSibling();
      if (!isInBranch(firstElement, rBrace)) {
        return null;
      }

      PsiStatement firstStatement = firstElement instanceof PsiStatement
                                    ? (PsiStatement)firstElement
                                    : getNextSiblingOfType(firstElement, PsiStatement.class);
      PsiStatement lastStatement = null;
      for (PsiStatement next = firstStatement;
           isInBranch(next, rBrace);
           next = getNextSiblingOfType(next, PsiStatement.class)) {
        lastStatement = next;
      }
      if (lastStatement == null) {
        return null;
      }
      return new Branch(firstElement, lastStatement, codeBlock, lBrace);
    }

    @Contract("null,_ -> false")
    private static boolean isInBranch(@Nullable PsiElement element, @NotNull PsiElement rBrace) {
      return element != null && element != rBrace && !(element instanceof PsiSwitchLabelStatement);
    }

    PsiElement moveAfter(@NotNull PsiSwitchLabelStatement labelToMove) {
      PsiSwitchLabelStatement previousLabel = getPrevSiblingOfType(myFirstElement, PsiSwitchLabelStatement.class);
      PsiElement labelCopy = myCodeBlock.addAfter(labelToMove, myLastStatement);
      myCodeBlock.addRangeAfter(myFirstElement, myLastStatement, labelCopy);
      addBreakIfNeeded(previousLabel);

      // move comments and white spaces that are before the label being moved
      PsiElement prefixStart = null, prefixEnd = null;
      for (PsiElement previous = labelToMove.getPrevSibling();
           previous != null && previous != myLBrace && !(previous instanceof PsiStatement);
           previous = previous.getPrevSibling()) {
        prefixStart = previous;
        if (prefixEnd == null) prefixEnd = previous;
      }
      if (prefixStart != null) {
        myCodeBlock.addRangeBefore(prefixStart, prefixEnd, labelCopy);
        myCodeBlock.deleteChildRange(prefixStart, labelToMove);
      }
      else {
        labelToMove.delete();
      }
      return labelCopy;
    }

    PsiElement copyTo(@NotNull PsiSwitchLabelStatement anchor) {
      myCodeBlock.addRangeAfter(myFirstElement, myLastStatement, anchor);

      addBreakIfNeeded(anchor);
      return anchor;
    }

    private void addBreakIfNeeded(@Nullable PsiElement label) {
      PsiStatement lastStatementInBranch = findLastStatementInBranch(label);

      if (lastStatementInBranch != null && statementMayCompleteNormally(lastStatementInBranch)) {
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(lastStatementInBranch.getProject());
        PsiStatement breakStatement = factory.createStatementFromText("break;", null);
        myCodeBlock.addAfter(breakStatement, lastStatementInBranch);
      }
    }
  }
}
