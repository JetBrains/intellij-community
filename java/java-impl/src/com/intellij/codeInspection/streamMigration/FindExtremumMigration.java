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
package com.intellij.codeInspection.streamMigration;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.util.ObjectUtils.tryCast;

/**
 * Created by Roman Ivanov.
 */
public class FindExtremumMigration extends BaseStreamApiMigration {
  final private boolean myIsMax;
  final private boolean isPrimitive;
  final private PsiVariable myCurrentExtremalElement;
  final private PsiVariable myCurrentExtremalComparingElement; // TODO naming?
  final private PsiExpression myKeySelector;

  protected FindExtremumMigration(boolean shouldWarn,
                                  String replacement,
                                  boolean isMax,
                                  boolean isPrimitive,
                                  PsiVariable currentExtremalElement,
                                  PsiVariable currentExtremalComparingElement,
                                  PsiExpression selector) {
    super(shouldWarn, replacement); // TODO min/max?
    myIsMax = isMax;
    this.isPrimitive = isPrimitive;
    myCurrentExtremalElement = currentExtremalElement;
    myCurrentExtremalComparingElement = currentExtremalComparingElement;
    myKeySelector = selector;
  }


  @Override
  PsiElement migrate(@NotNull Project project, @NotNull PsiStatement body, @NotNull TerminalBlock tb) {
    PsiStatement statement = tb.getSingleStatement();
    if (statement == null || !(statement instanceof PsiIfStatement)) return null;
    PsiIfStatement ifStatement = (PsiIfStatement)statement;
    return null;
  }


  static @Nullable
  ExtremumTerminal extractExtremumTerminal(@NotNull TerminalBlock tb, @NotNull List<PsiVariable> variables) {
    if (variables.size() == 1) { // possibly collecting extremum in single variable
      PsiIfStatement ifStatement = tryCast(tb.getSingleStatement(), PsiIfStatement.class);
      if (ifStatement == null) return null;
      PsiBinaryExpression binaryExpression = tryCast(ifStatement.getCondition(), PsiBinaryExpression.class);
      if (binaryExpression == null) return null;
      PsiExpression condition = ifStatement.getCondition();
      PsiExpression lOperand = binaryExpression.getLOperand();

      //TODO primitive
      if (binaryExpression.getOperationSign().getTokenType().equals(JavaTokenType.OROR)) {// maxPerson == null || maxPerson.getAge() < person.getAge()
        // stream().max(Comparator.comparingInt(Person::getAge)
        if (lOperand instanceof PsiBinaryExpression) {
          PsiVariable extremumHolder = extractNullCheckingVar((PsiBinaryExpression)lOperand);
          PsiExpression rOperand = binaryExpression.getROperand();
          if(rOperand instanceof PsiBinaryExpression && extremumHolder != null) {
            extractComparision((PsiBinaryExpression)rOperand, extremumHolder);
          }
        }
      }
      else if (binaryExpression.getOperationSign().getTokenType().equals(JavaTokenType.EQEQ)) { // maxPerson == null, and actual comparision inside if
        PsiVariable variable = extractNullCheckingVar(binaryExpression);
      }
    }
    else if (variables.size() == 2) { // intermediate extremum value in separate variable (maxPersonAge)
      //TODO
    }
    else {
      return null;
    }
    return null;
  }

  @Nullable
  private static ComparisionContext extractComparision(@NotNull PsiBinaryExpression expression, @NotNull PsiVariable extremumHolder) {
    IElementType sign = expression.getOperationSign().getTokenType();
    if (sign.equals(JavaTokenType.LT) || sign.equals(JavaTokenType.LE)) {
      boolean equalPossible = JavaTokenType.LE.equals(sign);
      PsiExpression operand = expression.getROperand();
      if(operand == null) return null;
      extractComparisionOriented(expression.getLOperand(), operand, extremumHolder, true, equalPossible);
    }
    else if (sign.equals(JavaTokenType.GT) || sign.equals(JavaTokenType.GE)) {

    }
    return null;
  }

  @Nullable
  private static ComparisionContext extractComparisionOriented(@NotNull PsiExpression first,
                                                               @NotNull PsiExpression second,
                                                               @NotNull PsiVariable extremumHolder,
                                                               boolean firstLess,
                                                               boolean equalsPossible) {
    PsiReferenceExpression firstReceiver = resolveReceiver(first);
    PsiReferenceExpression secondReceiver = resolveReceiver(second);


    //EquivalenceChecker equivalence = EquivalenceChecker.getCanonicalPsiEquivalence();
    //EquivalenceChecker.Match match = equivalence.expressionsMatch(first, second);
    //PsiVariable variable = tryCast(match.getLeftDiff(), PsiVariable.class);
    return null;
  }

  private static PsiReferenceExpression resolveReceiver(@NotNull PsiExpression expression) {
    PsiMethodCallExpression methodCallExpression = tryCast(expression, PsiMethodCallExpression.class);
    PsiExpression qualifierExpression = methodCallExpression.getMethodExpression().getQualifierExpression();
    return tryCast(qualifierExpression, PsiReferenceExpression.class);
  }

  @Nullable
  private static PsiVariable extractNullCheckingVar(PsiBinaryExpression expression) {
    if (!expression.getOperationSign().getTokenType().equals(JavaTokenType.EQEQ)) {
      return null;
    }
    PsiExpression lOperand = expression.getLOperand();
    PsiExpression rOperand = expression.getROperand();
    if (rOperand == null) return null;

    PsiVariable lVariable = extractOrientedNullCheck(lOperand, rOperand);
    if (lVariable != null) return lVariable;
    PsiVariable rVariable = extractOrientedNullCheck(rOperand, lOperand);
    if (rVariable != null) return rVariable;
    return null;
  }

  @Nullable
  private static PsiVariable extractOrientedNullCheck(PsiExpression first, PsiExpression second) {
    PsiReferenceExpression lReference = tryCast(first, PsiReferenceExpression.class);
    if (lReference != null) {
      PsiVariable variable = tryCast(lReference.resolve(), PsiVariable.class);
      if (variable != null && second.getText().equals("null")) {
        return variable;
      }
    }
    return null;
  }

  static class ComparisionContext {
    boolean myStrict;
    boolean myIsGreater;
    PsiExpression myKeySelector;
  }

  static class ExtremumTerminal {

  }
}
