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

import static com.intellij.util.ObjectUtils.tryCast;

/**
 * Created by Roman Ivanov.
 */
public class FindExtremumMigration extends BaseStreamApiMigration {

  static final String MAX_REPLACEMENT = "max()";
  static final String MIN_REPLACEMENT = "min()";

  protected FindExtremumMigration(boolean shouldWarn,
                                  String replacement) {
    super(shouldWarn, replacement);
  }


  @Override
  PsiElement migrate(@NotNull Project project, @NotNull PsiStatement body, @NotNull TerminalBlock tb) {
    ExtremumTerminal terminal = extractExtremumTerminal(tb);
    if (terminal == null) return null;
    String operation = terminal.isMax() ? "max" : "min";
    String comparator;
    KeySelector keySelector = terminal.getKeySelector();
    PsiVariable currentVariable =
      terminal.getCurrentVariable(); // TODO is it needed to check if type of comparator and current variable are the same

    String lambdaText = keySelector.getLambdaText(currentVariable);
    String comparingMethod = getComparingMethod(keySelector.acceptingType());
    if (comparingMethod == null) return null;
    comparator = CommonClassNames.JAVA_UTIL_COMPARATOR + "." + comparingMethod + "(" + lambdaText + ")";

    TerminalBlock terminalBlock = terminal.getTerminalBlock();
    String stream = terminalBlock.generate() + "." + operation + "(" + comparator + ").orElse(null)"; // TODO handle primitive
    PsiLoopStatement loop = terminalBlock.getMainLoop();

    return replaceWithFindExtremum(loop, terminal.getExtremumHolder(), stream, PsiType.INT); // TODO type?
  }


  @Nullable
  static ExtremumTerminal extractExtremumTerminal(@NotNull TerminalBlock tb) {
    PsiStatement[] statements = tb.getStatements();
    StreamApiMigrationInspection.Operation operation = tb.getLastOperation();
    StreamApiMigrationInspection.FilterOp filterOp = tryCast(operation, StreamApiMigrationInspection.FilterOp.class);
    if (filterOp != null) {
      tb = tb.withoutLastOperation();
      if (tb == null) return null;
      ExtremumTerminal terminal = extractSimpleCase(filterOp.getExpression(), statements, tb);
      if (terminal != null) return terminal;
    }
    switch (statements.length) {
      case 1: // if() .. else ..
        break;
      case 2: // if () .. if() ..
        break;
    }
    return null;
  }

  // maxPerson == null || maxPerson.getAge() < person.getAge()
  @Nullable
  private static ExtremumTerminal extractSimpleCase(@NotNull PsiExpression filterExpression,
                                                    @NotNull PsiStatement[] statements,
                                                    @NotNull TerminalBlock terminalBlock) {
    PsiBinaryExpression binaryExpression = tryCast(filterExpression, PsiBinaryExpression.class);
    if (binaryExpression == null) return null;

    IElementType sign = binaryExpression.getOperationSign().getTokenType();
    if (!sign.equals(JavaTokenType.OROR)) {
      return null;
    }
    PsiExpression lOperand = binaryExpression.getLOperand();
    PsiExpression rOperand = binaryExpression.getROperand();
    if(rOperand == null) return null;
    ExtremumTerminal terminal = extractSimpleCaseOriented(lOperand, rOperand, statements, terminalBlock);
    if(terminal != null) return terminal;
    return extractSimpleCaseOriented(rOperand, lOperand, statements, terminalBlock);
  }

  @Nullable
  private static ExtremumTerminal extractSimpleCaseOriented(@NotNull PsiExpression first,
                                                            @NotNull PsiExpression second,
                                                            @NotNull PsiStatement[] statements,
                                                            @NotNull TerminalBlock terminalBlock) {
    if (first instanceof PsiBinaryExpression) {
      PsiVariable extremumHolder = extractNullCheckingVar((PsiBinaryExpression)first);
      if (second instanceof PsiBinaryExpression && extremumHolder != null) {

        Comparision comparision = extractComparision((PsiBinaryExpression)second);
        if (comparision == null) return null;
        Assignment[] assignments = extractAssignments(statements);
        if (assignments == null) return null;
        return extractExtremumTerminal(comparision, assignments, extremumHolder, terminalBlock);
      }
    }
    return null;
  }

  @Nullable
  static private String getComparingMethod(@NotNull PsiType type) {
    if (type.equals(PsiType.INT)) return "comparingInt";
    if (type.equals(PsiType.DOUBLE)) return "comparingDouble";
    if (type.equals(PsiType.LONG)) return "comparingLong";
    return null; // TODO more precise handling
  }

  @Nullable
  static private Assignment[] extractAssignments(@NotNull PsiStatement[] statements) {
    if (statements.length == 1) {
      Assignment assignment = getSingleAssignment(statements[0]);
      if (assignment == null) return null;
      return new Assignment[]{assignment};
    }
    else if (statements.length == 2) {
      Assignment first = getSingleAssignment(statements[0]);
      if (first == null) return null;
      Assignment second = getSingleAssignment(statements[1]);
      if (second == null) return null;
      return new Assignment[]{first, second};
    }
    return null;
  }

  @Nullable
  private static Assignment getSingleAssignment(@NotNull PsiStatement statement) {
    if (statement instanceof PsiExpressionStatement) {
      PsiAssignmentExpression assignment = tryCast(((PsiExpressionStatement)statement).getExpression(), PsiAssignmentExpression.class);
      PsiReferenceExpression holderReference = tryCast(assignment.getLExpression(), PsiReferenceExpression.class);
      if (holderReference == null) return null;
      PsiVariable holder = tryCast(holderReference.resolve(), PsiVariable.class);
      if (holder == null) return null;
      PsiExpression expression = assignment.getRExpression();
      if (expression == null) return null;
      return new Assignment(holder, expression);
    }
    return null;
  }

  @Nullable
  private static ExtremumTerminal extractExtremumTerminal(@NotNull Comparision comparision,
                                                          @NotNull Assignment[] assignments,
                                                          @NotNull PsiVariable nullCheckedHolder,
                                                          @NotNull TerminalBlock terminalBlock) {
    PsiExpression initializer = nullCheckedHolder.getInitializer();
    if (initializer == null || !PsiType.NULL.equals(initializer.getType())) return null;

    //TODO control flow? how to understand if it didn't change?

    final boolean isMax;
    final PsiVariable comparisionExtremumHolder;
    final PsiVariable comparisionCurrent;
    final KeySelector comparisionKeySelector;
    if (comparision.getFirst().getVariable().equals(nullCheckedHolder)) {
      isMax = !comparision.isGreater();
      comparisionExtremumHolder = comparision.getFirst().getVariable();
      comparisionCurrent = comparision.getSecond().getVariable();
      comparisionKeySelector = comparision.getFirst();
    }
    else if (comparision.getSecond().getVariable().equals(nullCheckedHolder)) {
      isMax = comparision.isGreater();
      comparisionExtremumHolder = comparision.getSecond().getVariable();
      comparisionCurrent = comparision.getFirst().getVariable();
      comparisionKeySelector = comparision.getSecond();
    }
    else {
      return null;
    }

    if (assignments.length == 1) {
      Assignment assignment = assignments[0];
      if (!assignment.getExtremumHolder().equals(nullCheckedHolder)) return null;
      if (assignment.hasSameVariables(comparisionExtremumHolder, comparisionCurrent)) return null;
      return new ExtremumTerminal(isMax, comparisionKeySelector, comparisionCurrent, comparisionExtremumHolder, terminalBlock, null);
    }
    else if (assignments.length == 2) {
      //if(max == null || maxAge < current.getAge()) {max =
      Assignment first = assignments[0];
      Assignment second = assignments[1];
      if (first.getExtremumHolder().equals(nullCheckedHolder) && first.hasSameVariables(comparisionExtremumHolder, comparisionCurrent)) {
        KeySelector assignmentKeySelector = KeySelector.extractKeySelector(second.getExpression());
        if (assignmentKeySelector != null && comparisionKeySelector.equals(assignmentKeySelector)) {
          PsiVariable keyExtremumHolder = assignmentKeySelector.getVariable();
          if (keyExtremumHolder.equals(comparisionCurrent)) {

          }
          //TODO
        }
      }
      //else if() // TODO second is extremum holder assignment
    }
    else {
      return null;
    }
    return null;
  }

  @Nullable
  private static Comparision extractComparision(@NotNull PsiBinaryExpression expression) {
    IElementType sign = expression.getOperationSign().getTokenType();
    PsiExpression operand = expression.getROperand();
    if (operand == null) return null;
    if (sign.equals(JavaTokenType.LT) || sign.equals(JavaTokenType.LE)) {
      return extractComparision(expression.getLOperand(), operand, false);
    }
    else if (sign.equals(JavaTokenType.GT) || sign.equals(JavaTokenType.GE)) {
      return extractComparision(expression.getLOperand(), operand, true);
    }
    return null;
  }

  @Nullable
  private static Comparision extractComparision(@NotNull PsiExpression first, @NotNull PsiExpression second, boolean isGreater) {
    KeySelector firstSelector = KeySelector.extractKeySelector(first);
    KeySelector secondSelector = KeySelector.extractKeySelector(second);
    if (firstSelector == null || secondSelector == null || !firstSelector.equals(secondSelector)) return null;
    return new Comparision(firstSelector, secondSelector, isGreater);
  }

  @Nullable
  private static PsiVariable resolveMethodReceiver(@NotNull PsiExpression expression) {
    PsiMethodCallExpression methodCallExpression = tryCast(expression, PsiMethodCallExpression.class);
    PsiExpression qualifierExpression = methodCallExpression.getMethodExpression().getQualifierExpression();
    PsiReferenceExpression reference = tryCast(qualifierExpression, PsiReferenceExpression.class);
    if (reference == null) return null;
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

  /**
   * Used to create Comparator
   */
  interface KeySelector {
    @NotNull
    String getLambdaText(@NotNull PsiVariable variable);

    @NotNull
    PsiType acceptingType();

    @NotNull
    PsiVariable getVariable();

    @Nullable
    static KeySelector extractKeySelector(@NotNull PsiExpression expression) {
      MethodKeySelector methodKeySelector = MethodKeySelector.extract(expression);
      if (methodKeySelector != null) return methodKeySelector;
      VariableKeySelector variableKeySelector = VariableKeySelector.extract(expression);
      if (variableKeySelector != null) return variableKeySelector;
      return null;
    }
  }
  //

  static class MethodKeySelector implements KeySelector {
    private final @NotNull PsiMethod myMethod;
    private final @NotNull PsiVariable myVariable;
    @NotNull private final PsiClass myContainingClass;
    @NotNull private final PsiType myType;

    MethodKeySelector(@NotNull PsiMethod method,
                      @NotNull PsiVariable variable,
                      @NotNull PsiClass containingClass,
                      @NotNull PsiType type) {
      myMethod = method;
      myVariable = variable;
      myContainingClass = containingClass;
      myType = type;
    }

    @NotNull
    @Override
    public String getLambdaText(@NotNull PsiVariable variable) {
      return myContainingClass.getQualifiedName() + "::" + myMethod.getName();
    }

    @NotNull
    @Override
    public PsiType acceptingType() {
      return myType;
    }

    @NotNull
    @Override
    public PsiVariable getVariable() {
      return myVariable;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof MethodKeySelector && ((MethodKeySelector)obj).myMethod == myMethod;
    }

    @Nullable
    static MethodKeySelector extract(@NotNull PsiExpression expression) {
      PsiMethod method = resolveMethod(expression);
      if (method == null) return null;
      PsiVariable receiver = resolveMethodReceiver(expression);
      if (receiver == null) return null;
      PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) return null;
      PsiType returnType = method.getReturnType();
      if (returnType == null) return null;
      return new MethodKeySelector(method, receiver, containingClass, returnType);
    }
  }

  private static class VariableKeySelector implements KeySelector {
    private final PsiVariable myVariable;

    private VariableKeySelector(PsiVariable variable) {myVariable = variable;}

    @NotNull
    @Override
    public String getLambdaText(@NotNull PsiVariable variable) {
      String variableText = myVariable.getText();
      return variableText + "->" + variableText;
    }

    @NotNull
    @Override
    public PsiType acceptingType() {
      return myVariable.getType();
    }

    @NotNull
    @Override
    public PsiVariable getVariable() {
      return myVariable;
    }

    @Nullable
    static VariableKeySelector extract(@NotNull PsiExpression expression) {
      PsiVariable variable = tryCast(expression, PsiVariable.class);
      if (variable == null) return null;
      return new VariableKeySelector(variable);
    }
  }

  private static class Assignment {
    private final @NotNull PsiVariable myExtremumHolder;
    private final @NotNull PsiExpression myExpression;

    private Assignment(@NotNull PsiVariable extremumHolder, @NotNull PsiExpression expression) {
      myExtremumHolder = extremumHolder;
      myExpression = expression;
    }

    @NotNull
    public PsiVariable getExtremumHolder() {
      return myExtremumHolder;
    }

    @NotNull
    public PsiExpression getExpression() {
      return myExpression;
    }

    private boolean hasSameVariables(@NotNull PsiVariable extremumHolder, @NotNull PsiVariable current) {
      PsiVariable rVariable = tryCast(myExpression, PsiVariable.class);
      return rVariable != null & myExtremumHolder.equals(extremumHolder) && rVariable.equals(current);
    }
  }

  static class Comparision {
    private final @NotNull KeySelector myFirst;
    private final @NotNull KeySelector mySecond;
    private final boolean myIsGreater;

    Comparision(@NotNull KeySelector first, @NotNull KeySelector second, boolean greater) {
      myFirst = first;
      mySecond = second;
      myIsGreater = greater;
    }

    public boolean isGreater() {
      return myIsGreater;
    }

    public KeySelector getSecond() {
      return mySecond;
    }

    public KeySelector getFirst() {
      return myFirst;
    }
  }

  static class ExtremumTerminal {
    private final boolean myIsMax;
    private final @NotNull KeySelector myKeySelector; // field or method
    private final @NotNull PsiVariable myCurrentVariable;
    private final @NotNull PsiVariable myExtremumHolder;
    private final @NotNull TerminalBlock myTerminalBlock;
    private final @Nullable PsiExpression myStartingValue;

    public ExtremumTerminal(boolean isMax,
                            @NotNull KeySelector keySelector,
                            @NotNull PsiVariable currentVariable,
                            @NotNull PsiVariable extremumHolder,
                            @NotNull TerminalBlock terminalBlock,
                            @Nullable PsiExpression startingValue) {
      myIsMax = isMax;
      myKeySelector = keySelector;
      myCurrentVariable = currentVariable;
      myExtremumHolder = extremumHolder;
      myStartingValue = startingValue;
      myTerminalBlock = terminalBlock;
    }

    public boolean isMax() {
      return myIsMax;
    }

    @NotNull
    public KeySelector getKeySelector() {
      return myKeySelector;
    }

    @NotNull
    public PsiVariable getCurrentVariable() {
      return myCurrentVariable;
    }

    @NotNull
    public PsiVariable getExtremumHolder() {
      return myExtremumHolder;
    }

    @Nullable
    public PsiExpression getStartingValue() {
      return myStartingValue;
    }

    @NotNull
    public TerminalBlock getTerminalBlock() {
      return myTerminalBlock;
    }
  }
}
