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
  private final ExtremumTerminal myExtremumTerminal;

  static final String MAX_REPLACEMENT = "max()";
  static final String MIN_REPLACEMENT = "min()";

  protected FindExtremumMigration(boolean shouldWarn,
                                  String replacement,
                                  ExtremumTerminal terminal) {
    super(shouldWarn, replacement);
    myExtremumTerminal = terminal;
  }


  @Override
  PsiElement migrate(@NotNull Project project, @NotNull PsiStatement body, @NotNull TerminalBlock tb) {
    String operation = myExtremumTerminal.isMax() ? "max" : "min";
    String comparator;
    if(!myExtremumTerminal.myIsPrimitive) {
      PsiMember keySelector = myExtremumTerminal.getKeySelector();
      String comparatorLambda = null;
      PsiType type = null;
      if(keySelector instanceof PsiMethod) {
        // TODO how to create method reference properly?
        PsiClass containingClass = keySelector.getContainingClass();
        if(containingClass == null) return null; // TODO how it could be?
        String classQualifiedName = containingClass.getQualifiedName();
        if(classQualifiedName == null) return null;
        comparatorLambda = classQualifiedName + "::" + keySelector.getName();
        PsiType returnType = ((PsiMethod)keySelector).getReturnType();
        if(returnType == null) return null; // TODO maybe check this in extraction and pass as context?
        type = returnType;
      } else if(keySelector instanceof PsiVariable) {
        return null; //TODO
      }
      if(type == null) return null; // TODO it should never happen
      String comparingMethod = getComparingMethod(type);
      if(comparingMethod == null) return null;
      comparator = CommonClassNames.JAVA_UTIL_COMPARATOR + "." + comparingMethod + "(" + comparatorLambda + ")";
    } else {
      comparator = ""; // TODO
    }

    String stream = myExtremumTerminal.getTerminalBlock().generate() + "." + operation + "(" + comparator + ").orElse(null)"; // TODO handle primitive
    PsiLoopStatement loop = tb.getMainLoop();

    return replaceWithFindExtremum(loop, myExtremumTerminal.getExtremumHolder(), stream, PsiType.INT); // TODO type?
  }

  @Nullable
  static private String getComparingMethod(@NotNull PsiType type) {
    if(type.equals(PsiType.INT)) return "comparingInt";
    if(type.equals(PsiType.DOUBLE)) return "comparingDouble";
    if(type.equals(PsiType.LONG)) return "comparingLong";
    return null; // TODO more precise handling
  }


  static @Nullable
  ExtremumTerminal extractExtremumTerminal(@NotNull TerminalBlock tb, @NotNull List<PsiVariable> variables) {
    StreamApiMigrationInspection.FilterOp filterOp = tb.getLastOperation(StreamApiMigrationInspection.FilterOp.class);
    if(filterOp == null) return null;
    tb = tb.withoutLastOperation();
    if(tb == null) return null;

    if (variables.size() == 1) { // possibly collecting extremum in single variable
      PsiBinaryExpression binaryExpression = tryCast(filterOp.myExpression, PsiBinaryExpression.class);
      if (binaryExpression == null) return null;
      PsiExpression lOperand = binaryExpression.getLOperand();

      //TODO primitive
      IElementType sign = binaryExpression.getOperationSign().getTokenType();
      if (sign.equals(JavaTokenType.OROR)) {// maxPerson == null || maxPerson.getAge() < person.getAge()
        // stream().max(Comparator.comparingInt(Person::getAge)
        if (lOperand instanceof PsiBinaryExpression) {
          PsiVariable extremumHolder = extractNullCheckingVar((PsiBinaryExpression)lOperand);
          PsiExpression rOperand = binaryExpression.getROperand();
          if(rOperand instanceof PsiBinaryExpression && extremumHolder != null) {
            ComparisionContext comparision = extractComparision((PsiBinaryExpression)rOperand, extremumHolder);
            if(comparision == null) return null;
            PsiAssignmentExpression assignmentExpression = tb.getSingleExpression(PsiAssignmentExpression.class);
            if(assignmentExpression == null) return null;
            PsiReference reference = assignmentExpression.getLExpression().getReference();
            if(reference == null) return null;
            PsiVariable leftVar = tryCast(reference.resolve(), PsiVariable.class);
            PsiExpression assigned = assignmentExpression.getRExpression();

            if(leftVar == null || assigned == null) return null;
            //TODO how to compare references?
            if(!leftVar.equals(extremumHolder) || !assigned.getText().equals(comparision.getCurrentVariable().getName())) return null; // TODO how to compare
            return new ExtremumTerminal(false, comparision.isMax(), comparision.getKeySelector(), comparision.getCurrentVariable(), null,
                                        tb, extremumHolder);
          }
        }
      }
      else if (sign.equals(JavaTokenType.EQEQ)) { // maxPerson == null, and actual comparision inside if
        PsiVariable variable = extractNullCheckingVar(binaryExpression);
      }
      // Incompatible with long streams with filter ops and so on
      //else if(sign.equals(JavaTokenType.GT)) { // TODO maxPerson.getAge() < person.getAge() - before this empty check and use
      //
      //}
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
    PsiExpression operand = expression.getROperand();
    if(operand == null) return null;
    if (sign.equals(JavaTokenType.LT) || sign.equals(JavaTokenType.LE)) {
      return extractComparision(expression.getLOperand(), operand, extremumHolder, true);
    }
    else if (sign.equals(JavaTokenType.GT) || sign.equals(JavaTokenType.GE)) {
      return extractComparision(expression.getLOperand(), operand, extremumHolder, false);
    }
    return null;
  }


  //TODO inline?
  @Nullable
  private static ComparisionContext extractComparision(@NotNull PsiExpression first,
                                                       @NotNull PsiExpression second,
                                                       @NotNull PsiVariable extremumHolder,
                                                       boolean firstLess) {
    PsiVariable firstReceiver = resolveMethodReceiver(first); // works only for methods
    PsiVariable secondReceiver = resolveMethodReceiver(second);
    if(firstReceiver == null || secondReceiver == null) return null;
    PsiMethod firstMethod = resolveMethod(first);
    PsiMethod secondMethod = resolveMethod(first);
    if(firstMethod == null || secondMethod == null) return null;
    if(!firstMethod.equals(secondMethod)) return null; // TODO is it correct equals?
    if(extremumHolder.equals(firstReceiver)) { // TODO is it correct equals?
      return new ComparisionContext(firstLess, firstMethod, secondReceiver);
    } else if(extremumHolder.equals(secondReceiver)) {
      return new ComparisionContext(!firstLess, secondMethod, firstReceiver);
    }
    //EquivalenceChecker equivalence = EquivalenceChecker.getCanonicalPsiEquivalence();
    //EquivalenceChecker.Match match = equivalence.expressionsMatch(first, second);
    //PsiVariable variable = tryCast(match.getLeftDiff(), PsiVariable.class);
    return null;
  }



  @Nullable
  private static PsiVariable resolveMethodReceiver(@NotNull PsiExpression expression) {
    PsiMethodCallExpression methodCallExpression = tryCast(expression, PsiMethodCallExpression.class);
    PsiExpression qualifierExpression = methodCallExpression.getMethodExpression().getQualifierExpression();
    PsiReferenceExpression reference = tryCast(qualifierExpression, PsiReferenceExpression.class);
    if(reference == null) return null;
    return tryCast(reference.resolve(), PsiVariable.class);
  }

  @Nullable
  private static PsiMethod resolveMethod(@NotNull PsiExpression expression) {
    PsiMethodCallExpression methodCallExpression = tryCast(expression, PsiMethodCallExpression.class);
    PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    PsiElement resolvedMethod = methodExpression.resolve();
    return tryCast(resolvedMethod, PsiMethod.class);
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
    private final boolean myIsMax;
    private final PsiMember myKeySelector; // field or method
    private final PsiVariable myCurrentVariable;

    public ComparisionContext(boolean isMax, @NotNull PsiMember keySelector, @NotNull PsiVariable currentVariable) {
      myIsMax = isMax;
      myKeySelector = keySelector;
      myCurrentVariable = currentVariable;
    }

    public boolean isMax() {
      return myIsMax;
    }

    public PsiMember getKeySelector() {
      return myKeySelector;
    }

    public PsiVariable getCurrentVariable() {
      return myCurrentVariable;
    }
  }

  static class ExtremumTerminal {
    private final boolean myIsPrimitive;
    private final boolean myIsMax;
    private final PsiMember myKeySelector; // field or method
    private final PsiVariable myCurrentVariable;
    private final PsiExpression myStartingValue;
    private final TerminalBlock myTerminalBlock;
    private final PsiVariable myExtremumHolder;


    public ExtremumTerminal(boolean isPrimitive,
                            boolean isMax,
                            @NotNull PsiMember keySelector,
                            @NotNull PsiVariable currentVariable,
                            @Nullable PsiExpression startingValue,
                            @NotNull TerminalBlock block,
                            @NotNull PsiVariable extremumHolder) {
      myIsPrimitive = isPrimitive;
      myIsMax = isMax;
      myKeySelector = keySelector;
      myCurrentVariable = currentVariable;
      myStartingValue = startingValue;
      myTerminalBlock = block;
      myExtremumHolder = extremumHolder;
    }

    public boolean isPrimitive() {
      return myIsPrimitive;
    }

    public boolean isMax() {
      return myIsMax;
    }

    public PsiMember getKeySelector() {
      return myKeySelector;
    }

    public PsiVariable getCurrentVariable() {
      return myCurrentVariable;
    }

    public PsiExpression getStartingValue() {
      return myStartingValue;
    }

    public TerminalBlock getTerminalBlock() {
      return myTerminalBlock;
    }

    public PsiVariable getExtremumHolder() {
      return myExtremumHolder;
    }
  }
}
