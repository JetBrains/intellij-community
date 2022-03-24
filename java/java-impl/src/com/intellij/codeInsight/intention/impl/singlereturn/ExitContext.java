// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl.singlereturn;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Tracks method exit strategy and additional variables which could be necessary for single-return conversion 
 */
class ExitContext {
  private final @NotNull PsiType myReturnType;
  private final @NotNull FinishMarker.FinishMarkerType myFinishMarkerType;
  private String myFinishedVariable;
  private final @NotNull PsiCodeBlock myBlock;
  private final @NotNull String myReturnVariable;
  private final @NotNull PsiElementFactory myFactory;
  private boolean myReturnVariableUsed = false;
  private final PsiExpression myReturnVariableDefaultValue;

  ExitContext(@NotNull PsiCodeBlock block, @NotNull PsiType returnType, @NotNull FinishMarker marker) {
    myBlock = block;
    myFactory = JavaPsiFacade.getElementFactory(block.getProject());
    myReturnType = returnType;
    myReturnVariable =
      new VariableNameGenerator(block, VariableKind.LOCAL_VARIABLE).byName("result", "res").byType(returnType).generate(true);
    if (marker.myDefaultValue != null && PsiTreeUtil.isAncestor(block, marker.myDefaultValue, true)) {
      myReturnVariableDefaultValue = (PsiExpression)marker.myDefaultValue.copy();
    } else {
      myReturnVariableDefaultValue = marker.myDefaultValue;
    }
    myFinishMarkerType = marker.myType;
  }

  String generateExitCondition() {
    switch (myFinishMarkerType) {
      case BOOLEAN_FALSE:
        return "!" + myReturnVariable;
      case BOOLEAN_TRUE:
        return myReturnVariable;
      case VALUE_EQUAL:
        return myReturnVariable + "==" + myReturnVariableDefaultValue.getText();
      case VALUE_NON_EQUAL:
        return myReturnVariable + "!=" + myReturnVariableDefaultValue.getText();
      default:
        assert myFinishedVariable != null;
        return myFinishedVariable;
    }
  }

  String getNonExitCondition() {
    switch (myFinishMarkerType) {
      case BOOLEAN_FALSE:
        return myReturnVariable;
      case BOOLEAN_TRUE:
        return "!" + myReturnVariable;
      case VALUE_EQUAL:
        return myReturnVariable + "!=" + myReturnVariableDefaultValue.getText();
      case VALUE_NON_EQUAL:
        return myReturnVariable + "==" + myReturnVariableDefaultValue.getText();
      default:
        assert myFinishedVariable != null;
        return "!" + myFinishedVariable;
    }
  }

  void registerReturnValue(PsiExpression value, List<? super String> replacements) {
    myReturnVariableUsed = true;
    if (!EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(myReturnVariableDefaultValue, value)) {
      replacements.add(0, myReturnVariable + "=" + value.getText() + ";");
    }
  }

  void register(List<String> replacements) {
    if (myFinishMarkerType != FinishMarker.FinishMarkerType.SEPARATE_VAR) return;
    if (myFinishedVariable == null) {
      myFinishedVariable =
        new VariableNameGenerator(myBlock, VariableKind.LOCAL_VARIABLE).byName("finished", "completed").generate(true);
    }
    String assignment = myFinishedVariable + "=true;";
    if (!replacements.contains(assignment)) {
      String first = ContainerUtil.getFirstItem(replacements);
      int index = first != null && first.startsWith(myReturnVariable + "=") ? 1 : 0;
      replacements.add(index, assignment);
    }
  }

  PsiLocalVariable declareVariables() {
    if (myFinishedVariable != null) {
      PsiJavaToken start = requireNonNull(myBlock.getLBrace());
      PsiExpression initializer = myFactory.createExpressionFromText("false", null);
      PsiDeclarationStatement declaration =
        myFactory.createVariableDeclarationStatement(myFinishedVariable, PsiType.BOOLEAN, initializer);
      PsiModifierList list = ((PsiLocalVariable)declaration.getDeclaredElements()[0]).getModifierList();
      if (list != null) {
        // Cannot be final: always reassigned or useless
        list.setModifierProperty(PsiModifier.FINAL, false);
      }
      myBlock.addAfter(declaration, start);
    }
    if (myReturnVariableUsed) {
      PsiJavaToken start = requireNonNull(myBlock.getLBrace());
      PsiExpression initializer = myReturnVariableDefaultValue;
      if (initializer == null && myFinishedVariable != null) {
        initializer = myFactory.createExpressionFromText(PsiTypesUtil.getDefaultValueOfType(myReturnType), null);
      }
      PsiDeclarationStatement declaration = myFactory.createVariableDeclarationStatement(myReturnVariable, myReturnType, initializer);
      PsiLocalVariable var = (PsiLocalVariable)((PsiDeclarationStatement)myBlock.addAfter(declaration, start)).getDeclaredElements()[0];
      if (var.hasModifierProperty(PsiModifier.FINAL) && !CommonJavaRefactoringUtil.canBeDeclaredFinal(var)) {
        // Keep final when possible to respect code style setting "generate local variables as 'final'"
        requireNonNull(var.getModifierList()).setModifierProperty(PsiModifier.FINAL, false);
      }
      return var;
    }
    return null;
  }

  public boolean isFinishCondition(PsiStatement statement) {
    if (!(statement instanceof PsiIfStatement)) return false;
    PsiExpression condition = ((PsiIfStatement)statement).getCondition();
    if (condition == null) return false;
    if (BoolUtils.isNegation(condition)) {
      condition = BoolUtils.getNegated(condition);
    }
    if (condition instanceof PsiBinaryExpression) {
      condition = ((PsiBinaryExpression)condition).getLOperand();
    }
    if (!(condition instanceof PsiReferenceExpression)) return false;
    PsiReferenceExpression ref = (PsiReferenceExpression)condition;
    return ref.getQualifierExpression() == null &&
           (Objects.equals(myFinishedVariable, ref.getReferenceName()) ||
            Objects.equals(myReturnVariable, ref.getReferenceName()));
  }

  /**
   * @param statement statement to check
   * @return true if given statement is a return statement which returns registered default value, so it could be fully removed
   */
  boolean isDefaultReturn(PsiStatement statement) {
    if (myReturnVariableDefaultValue != null && statement instanceof PsiReturnStatement) {
      PsiReturnStatement returnStatement = (PsiReturnStatement)statement;
      return EquivalenceChecker.getCanonicalPsiEquivalence()
               .expressionsAreEquivalent(myReturnVariableDefaultValue, returnStatement.getReturnValue()) &&
             !FinishMarker.mayNeedMarker(returnStatement, myBlock);
    }
    return false;
  }
}
