// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.intellij.psi.util.PsiTreeUtil.getNextSiblingOfType;
import static com.intellij.psi.util.PsiTreeUtil.getPrevSiblingOfType;
import static com.siyeh.ig.psiutils.ControlFlowUtils.statementMayCompleteNormally;

/**
 * @author Pavel.Dolgov
 */
public class SplitSwitchBranchWithSeveralCaseValuesAction extends PsiElementBaseIntentionAction {

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("intention.split.switch.branch.with.several.case.values.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    PsiSwitchLabelStatementBase labelStatement = findLabelStatement(editor, element);
    if (labelStatement == null || labelStatement.getEnclosingSwitchBlock() == null) return false;
    if (labelStatement instanceof PsiSwitchLabelStatement) {
      if (isMultiValueCase(labelStatement)) {
        // mixed syntax "case 1, 2: some code"
        if (isAvailableForLabel(labelStatement)) {
          setText(JavaBundle.message("intention.split.switch.branch.with.several.case.values.split.text"));
          return true;
        }
      }
      else if (hasSiblingLabel(labelStatement)) {
        // traditional syntax "case 1: case 2: some code"
        PsiSwitchLabelStatement lastSiblingLabel = findLastSiblingLabel(labelStatement, false);
        if (isAvailableForLabel(lastSiblingLabel)) {
          setText(JavaBundle.message("intention.split.switch.branch.with.several.case.values.copy.text"));
          return true;
        }
      }
    }
    else if (labelStatement instanceof PsiSwitchLabeledRuleStatement) {
      // enhanced syntax "case 1, 2 -> some code"
      if (isMultiValueCase(labelStatement)) {
        PsiStatement body = ((PsiSwitchLabeledRuleStatement)labelStatement).getBody();
        if (body != null && element.getTextOffset() < body.getTextOffset()) {
          setText(JavaBundle.message("intention.split.switch.branch.with.several.case.values.split.text"));
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isAvailableForLabel(@Nullable PsiSwitchLabelStatementBase label) {
    PsiStatement lastStatement = findLastStatementInBranch(label);
    return lastStatement != null && (!statementMayCompleteNormally(lastStatement) ||
                                     getNextSiblingOfType(lastStatement, PsiSwitchLabelStatement.class) == null);
  }

  private static boolean isMultiValueCase(@NotNull PsiSwitchLabelStatementBase label) {
    PsiExpressionList caseValues = label.getCaseValues();
    return caseValues != null && caseValues.getExpressionCount() > 1;
  }

  private static boolean hasSiblingLabel(@Nullable PsiSwitchLabelStatementBase label) {
    return getPrevSiblingOfType(label, PsiStatement.class) instanceof PsiSwitchLabelStatement ||
           getNextSiblingOfType(label, PsiStatement.class) instanceof PsiSwitchLabelStatement;
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
      PsiExpressionList caseValues = labelStatement.getCaseValues();
      if (caseValues != null && caseValues.getExpressionCount() > 1) {
        PsiExpression caseValue = findCaseValue(element, caseValues, editor);
        Branch branch = Branch.fromLabel(labelStatement);
        if (branch != null) {
          if (isInList(caseValue, caseValues)) {
            result = moveLabelValueAfter(labelStatement, caseValue);
          }
          else {
            result = splitLabelValues(labelStatement);
          }
        }
      }
      else {
        PsiSwitchLabelStatement lastSiblingLabel = findLastSiblingLabel(labelStatement, true);
        if (lastSiblingLabel != null) {
          result = moveLabelAfter(labelStatement, lastSiblingLabel);
        }
        else {
          PsiStatement previousSibling = getPrevSiblingOfType(statement, PsiStatement.class);
          if (previousSibling instanceof PsiSwitchLabelStatement) {
            result = copyLabelTo(labelStatement, (PsiSwitchLabelStatement)previousSibling);
          }
        }
      }
    }
    else if (statement instanceof PsiSwitchLabeledRuleStatement) {
      PsiSwitchLabeledRuleStatement labeledRule = (PsiSwitchLabeledRuleStatement)statement;
      PsiExpressionList caseValues = labeledRule.getCaseValues();
      if (caseValues != null) {
        PsiExpression caseValue = findCaseValue(element, caseValues, editor);
        if (isInList(caseValue, caseValues)) {
          result = moveRuleAfter(caseValue, labeledRule);
        }
        else {
          result = splitRule(caseValues, labeledRule);
        }
      }
    }
    if (result != null) {
      editor.getCaretModel().moveToOffset(result.getTextOffset());
    }
  }

  private static PsiExpression findCaseValue(@NotNull PsiElement element, @NotNull PsiExpressionList caseValues, @NotNull Editor editor) {
    PsiExpression caseValue = PsiTreeUtil.getNonStrictParentOfType(element, PsiExpression.class);
    if (!isInList(caseValue, caseValues)) {
      PsiElement previousElement = getPreviousElement(editor, element);
      caseValue = PsiTreeUtil.getNonStrictParentOfType(previousElement, PsiExpression.class);
    }
    while (caseValue != null && caseValue.getParent() instanceof PsiExpression) {
      caseValue = (PsiExpression)caseValue.getParent();
    }
    return caseValue;
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

  private static PsiElement moveLabelAfter(@NotNull PsiSwitchLabelStatement labelToMove, @NotNull PsiSwitchLabelStatement anchor) {
    Branch branch = Branch.fromLabel(anchor);
    if (branch == null) {
      return null;
    }
    return branch.moveAfter(labelToMove);
  }

  private static PsiElement copyLabelTo(@NotNull PsiSwitchLabelStatement labelToCopyFrom, @NotNull PsiSwitchLabelStatement anchor) {
    Branch branch = Branch.fromLabel(labelToCopyFrom);
    if (branch == null) return null;

    return branch.copyTo(anchor);
  }

  @Nullable
  private static PsiElement moveLabelValueAfter(@NotNull PsiSwitchLabelStatement labelStatement, @NotNull PsiExpression caseValue) {
    Branch branch = Branch.fromLabel(labelStatement);
    if (branch == null) {
      return null;
    }

    PsiSwitchLabelStatement newLabel = branch.addLabelAfter(caseValue);
    caseValue.delete();
    return branch.copyTo(newLabel);
  }

  @Nullable
  private static PsiElement splitLabelValues(@NotNull PsiSwitchLabelStatement labelStatement) {
    Branch branch = Branch.fromLabel(labelStatement);
    if (branch == null) {
      return null;
    }

    List<PsiSwitchLabelStatement> newLabels = new ArrayList<>();
    PsiExpression[] expressions = Objects.requireNonNull(labelStatement.getCaseValues()).getExpressions();
    for (int i = expressions.length - 1; i >= 1; i--) {
      PsiExpression caseValue = expressions[i];
      PsiSwitchLabelStatement newLabel = branch.addLabelAfter(caseValue);
      newLabels.add(newLabel);
      caseValue.delete();
    }

    for (PsiSwitchLabelStatement newLabel : newLabels) {
      branch.copyTo(newLabel);
    }
    return !newLabels.isEmpty() ? newLabels.get(0) : null;
  }

  private static PsiSwitchLabeledRuleStatement moveRuleAfter(@NotNull PsiExpression caseValue,
                                                             @NotNull PsiSwitchLabeledRuleStatement labeledRule) {
    Project project = labeledRule.getProject();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiSwitchLabeledRuleStatement newRule = (PsiSwitchLabeledRuleStatement)factory.createStatementFromText("case 1->{}", null);

    Objects.requireNonNull(newRule.getCaseValues()).getExpressions()[0].replace(caseValue);
    newRule = (PsiSwitchLabeledRuleStatement)labeledRule.getParent().addAfter(newRule, labeledRule);
    newRule = (PsiSwitchLabeledRuleStatement)CodeStyleManager.getInstance(project).reformat(newRule);

    Objects.requireNonNull(newRule.getBody()).replace(Objects.requireNonNull(labeledRule.getBody()));

    caseValue.delete();
    return newRule;
  }

  private static PsiSwitchLabeledRuleStatement splitRule(@NotNull PsiExpressionList caseValues,
                                                         @NotNull PsiSwitchLabeledRuleStatement labeledRule) {
    PsiExpression[] expressions = caseValues.getExpressions();
    PsiSwitchLabeledRuleStatement anchor = labeledRule;
    for (int i = 1; i < expressions.length; i++) {
      anchor = moveRuleAfter(expressions[i], anchor);
    }
    return anchor;
  }

  @Contract("null -> null")
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
  private static PsiSwitchLabelStatement findLastSiblingLabel(@Nullable PsiStatement statement, boolean strict) {
    PsiSwitchLabelStatement result = null;
    PsiStatement start = strict ? getNextSiblingOfType(statement, PsiStatement.class) : statement;
    for (PsiStatement next = start; next instanceof PsiSwitchLabelStatement; next = getNextSiblingOfType(next, PsiStatement.class)) {
      result = (PsiSwitchLabelStatement)next;
    }
    return result;
  }

  private static final class Branch {
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

    PsiSwitchLabelStatement copyTo(@NotNull PsiSwitchLabelStatement anchor) {
      myCodeBlock.addRangeAfter(myFirstElement, myLastStatement, anchor);

      addBreakIfNeeded(anchor);
      return anchor;
    }

    @NotNull
    PsiSwitchLabelStatement addLabelAfter(@NotNull PsiExpression caseValue) {
      Project project = myCodeBlock.getProject();
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      PsiSwitchLabelStatement newLabel = (PsiSwitchLabelStatement)factory.createStatementFromText("case 1:", null);
      Objects.requireNonNull(newLabel.getCaseValues()).getExpressions()[0].replace(caseValue);

      newLabel = (PsiSwitchLabelStatement)myCodeBlock.addAfter(newLabel, myLastStatement);
      newLabel = (PsiSwitchLabelStatement)CodeStyleManager.getInstance(project).reformat(newLabel);
      return newLabel;
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
