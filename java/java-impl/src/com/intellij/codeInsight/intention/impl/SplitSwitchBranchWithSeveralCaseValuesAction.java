// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.siyeh.ig.psiutils.SwitchUtils;
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
    else if (labelStatement instanceof PsiSwitchLabeledRuleStatement ruleStatement) {
      // enhanced syntax "case 1, 2 -> some code"
      if (isMultiValueCase(labelStatement)) {
        PsiStatement body = ruleStatement.getBody();
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
    PsiCaseLabelElementList labelElementList = label.getCaseLabelElementList();
    return labelElementList != null && labelElementList.getElementCount() > 1;
  }

  private static boolean hasSiblingLabel(@Nullable PsiSwitchLabelStatementBase label) {
    return getPrevSiblingOfType(label, PsiStatement.class) instanceof PsiSwitchLabelStatement ||
           getNextSiblingOfType(label, PsiStatement.class) instanceof PsiSwitchLabelStatement;
  }

  /**
   * Handle the case where the caret is on the right side of the element we're interested in
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
    if (statement instanceof PsiSwitchLabelStatement labelStatement) {
      PsiCaseLabelElementList labelElementList = labelStatement.getCaseLabelElementList();
      if (labelElementList != null && labelElementList.getElementCount() > 1) {
        PsiCaseLabelElement labelElement = findLabelElement(element, labelElementList, editor);
        Branch branch = Branch.fromLabel(labelStatement);
        if (branch != null) {
          if (isInList(labelElement, labelElementList)) {
            result = moveLabelElementAfter(labelStatement, labelElement);
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
          if (previousSibling instanceof PsiSwitchLabelStatement previousLabelStatement) {
            result = copyLabelTo(labelStatement, previousLabelStatement);
          }
        }
      }
    }
    else if (statement instanceof PsiSwitchLabeledRuleStatement labeledRule) {
      PsiCaseLabelElementList labelElementList = labeledRule.getCaseLabelElementList();
      if (labelElementList != null) {
        PsiCaseLabelElement labelElement = findLabelElement(element, labelElementList, editor);
        if (isInList(labelElement, labelElementList)) {
          result = moveRuleAfter(labelElement, labeledRule);
        }
        else {
          result = splitRule(labelElementList, labeledRule);
        }
      }
    }
    if (result != null) {
      editor.getCaretModel().moveToOffset(result.getTextOffset());
    }
  }

  private static PsiCaseLabelElement findLabelElement(@NotNull PsiElement element,
                                                      @NotNull PsiCaseLabelElementList labelElementList,
                                                      @NotNull Editor editor) {
    PsiCaseLabelElement labelElement = PsiTreeUtil.getNonStrictParentOfType(element, PsiCaseLabelElement.class);
    if (!isInList(labelElement, labelElementList)) {
      PsiElement previousElement = getPreviousElement(editor, element);
      labelElement = PsiTreeUtil.getNonStrictParentOfType(previousElement, PsiCaseLabelElement.class);
    }
    PsiElement caseLabel = PsiTreeUtil.findFirstParent(labelElement, parent ->
      parent instanceof PsiCaseLabelElement && parent.getParent() instanceof PsiCaseLabelElementList
    );
    return (PsiCaseLabelElement)caseLabel;
  }

  @Contract("null,_ -> false")
  private static boolean isInList(@Nullable PsiCaseLabelElement labelElement, @NotNull PsiCaseLabelElementList labelElementList) {
    return labelElement != null && PsiTreeUtil.isAncestor(labelElementList, labelElement, true);
  }

  @Nullable
  private static PsiSwitchLabelStatementBase findLabelStatement(@NotNull Editor editor,
                                                                @NotNull PsiElement element) {
    // Can't use BaseElementAtCaretIntentionAction, because there's ambiguity when the caret is after the list of case values before the arrow
    PsiStatement statement = PsiTreeUtil.getParentOfType(element, PsiStatement.class);
    if (statement instanceof PsiSwitchLabelStatementBase labelStatement) {
      return labelStatement;
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
  private static PsiElement moveLabelElementAfter(@NotNull PsiSwitchLabelStatement labelStatement,
                                                  @NotNull PsiCaseLabelElement labelElement) {
    Branch branch = Branch.fromLabel(labelStatement);
    if (branch == null) {
      return null;
    }

    PsiSwitchLabelStatement newLabel = branch.addLabelAfter(labelElement, labelStatement);
    return branch.copyTo(newLabel);
  }

  @Nullable
  private static PsiElement splitLabelValues(@NotNull PsiSwitchLabelStatement labelStatement) {
    Branch branch = Branch.fromLabel(labelStatement);
    if (branch == null) {
      return null;
    }

    List<PsiSwitchLabelStatement> newLabels = new ArrayList<>();
    PsiCaseLabelElement[] caseLabelElements = Objects.requireNonNull(labelStatement.getCaseLabelElementList()).getElements();
    for (int i = caseLabelElements.length - 1; i >= 1; i--) {
      PsiCaseLabelElement labelElement = caseLabelElements[i];
      PsiSwitchLabelStatement newLabel = branch.addLabelAfter(labelElement, labelStatement);
      newLabels.add(newLabel);
    }

    for (PsiSwitchLabelStatement newLabel : newLabels) {
      branch.copyTo(newLabel);
    }
    return !newLabels.isEmpty() ? newLabels.get(0) : null;
  }

  private static PsiSwitchLabeledRuleStatement moveRuleAfter(@NotNull PsiCaseLabelElement labelElement,
                                                             @NotNull PsiSwitchLabeledRuleStatement labeledRule) {
    Project project = labeledRule.getProject();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiSwitchLabeledRuleStatement newRule;
    if (labelElement instanceof PsiDefaultCaseLabelElement) {
      newRule = (PsiSwitchLabeledRuleStatement)factory.createStatementFromText("default->{}", null);
    }
    else {
      PsiElement defaultElement = SwitchUtils.findDefaultElement(labeledRule);
      if (defaultElement != null) {
        return moveRuleAfter((PsiCaseLabelElement)defaultElement, labeledRule);
      }
      newRule = (PsiSwitchLabeledRuleStatement)factory.createStatementFromText("case 1->{}", null);
      Objects.requireNonNull(newRule.getCaseLabelElementList()).getElements()[0].replace(labelElement);
    }
    newRule = (PsiSwitchLabeledRuleStatement)labeledRule.getParent().addAfter(newRule, labeledRule);
    newRule = (PsiSwitchLabeledRuleStatement)CodeStyleManager.getInstance(project).reformat(newRule);

    Objects.requireNonNull(newRule.getBody()).replace(Objects.requireNonNull(labeledRule.getBody()));

    labelElement.delete();
    return newRule;
  }

  private static PsiSwitchLabeledRuleStatement splitRule(@NotNull PsiCaseLabelElementList labelElementList,
                                                         @NotNull PsiSwitchLabeledRuleStatement labeledRule) {
    PsiCaseLabelElement[] labelElements = labelElementList.getElements();
    PsiSwitchLabeledRuleStatement anchor = labeledRule;
    for (int i = 1; i < labelElements.length; i++) {
      anchor = moveRuleAfter(labelElements[i], anchor);
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

      PsiStatement firstStatement = firstElement instanceof PsiStatement statement
                                    ? statement
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
    PsiSwitchLabelStatement addLabelAfter(@NotNull PsiCaseLabelElement labelElement, @NotNull PsiSwitchLabelStatement labelStatement) {
      Project project = myCodeBlock.getProject();
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      PsiSwitchLabelStatement newLabel;
      if (labelElement instanceof PsiDefaultCaseLabelElement) {
        newLabel = (PsiSwitchLabelStatement)factory.createStatementFromText("default:", null);
      }
      else {
        PsiElement defaultElement = SwitchUtils.findDefaultElement(labelStatement);
        if (defaultElement != null) {
          return addLabelAfter((PsiCaseLabelElement)defaultElement, labelStatement);
        }
        newLabel = (PsiSwitchLabelStatement)factory.createStatementFromText("case 1:", null);
        Objects.requireNonNull(newLabel.getCaseLabelElementList()).getElements()[0].replace(labelElement);
      }

      newLabel = (PsiSwitchLabelStatement)myCodeBlock.addAfter(newLabel, myLastStatement);
      newLabel = (PsiSwitchLabelStatement)CodeStyleManager.getInstance(project).reformat(newLabel);

      labelElement.delete();
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
