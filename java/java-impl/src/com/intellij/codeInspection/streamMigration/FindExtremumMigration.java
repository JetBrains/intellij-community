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
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.tryCast;

/**
 * Created by Roman Ivanov.
 */
public class FindExtremumMigration extends BaseStreamApiMigration {

  static final String MAX_REPLACEMENT = "max()";
  static final String MIN_REPLACEMENT = "min()";

  private static final EquivalenceChecker ourEquivalence = EquivalenceChecker.getCanonicalPsiEquivalence();

  protected FindExtremumMigration(boolean shouldWarn, String replacement) {
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
      terminal.getCurrentVariable();

    JavaCodeStyleManager javaStyle = JavaCodeStyleManager.getInstance(project);
    String name = currentVariable.getName();
    if (name == null) name = "x";
    javaStyle.suggestUniqueVariableName(name, body, true);
    String lambdaText = keySelector.getLambdaText(name);
    PsiType lambdaReturnType = keySelector.acceptingType();
    String comparingMethod = getComparingMethod(lambdaReturnType);
    if (comparingMethod == null) return null;
    comparator = CommonClassNames.JAVA_UTIL_COMPARATOR + "." + comparingMethod + "(" + lambdaText + ")";

    TerminalBlock terminalBlock = terminal.getTerminalBlock();

    String stream = createStreamText(terminal, operation, comparator, name, terminalBlock);
    PsiLoopStatement loop = terminalBlock.getMainLoop();
    return replaceWithFindExtremum(loop, terminal.getExtremumHolder(), stream);
  }

  @NotNull
  private String createStreamText(ExtremumTerminal terminal,
                                  String operation,
                                  String comparator,
                                  String name,
                                  TerminalBlock terminalBlock) {
    String stream;
    if (!terminal.isPrimitive()) {
      stream = terminalBlock.generate() + "." + operation + "(" + comparator + ").orElse(null)";
    }
    else {
      String startingValue;
      String filterOp;
      if (terminal.getStartingValue() != null) {
        startingValue = terminal.getStartingValue().toString();
        String inFilterOperation = terminal.isMax() ? ">=" : "<=";
        filterOp = ".filter(" + name + "->" + name + inFilterOperation + terminal.getStartingValue().toString() + ")";
      }
      else {
        startingValue = "";
        filterOp = "";
      }
      stream = terminalBlock.generate() + filterOp + "." + operation + "().orElse(" + startingValue + ")";
    }
    return stream;
  }


  @Nullable
  static ExtremumTerminal extractExtremumTerminal(@NotNull TerminalBlock tb) {
    PsiStatement[] statements = tb.getStatements();
    StreamApiMigrationInspection.Operation operation = tb.getLastOperation();
    StreamApiMigrationInspection.FilterOp filterOp = tryCast(operation, StreamApiMigrationInspection.FilterOp.class);
    if (filterOp != null) {
      TerminalBlock block = tb.withoutLastOperation();
      if (block == null) return null;
      ExtremumTerminal terminal = extractSimpleRefCase(filterOp.getExpression(), statements, block);
      if (terminal != null) return terminal;
      ExtremumTerminal primitiveCase = extractSimplePrimitiveCase(filterOp.getExpression(), statements, block);
      if (primitiveCase != null) return primitiveCase;
    }
    switch (statements.length) {
      case 1: // if() .. else if() ..
        PsiStatement statement = statements[0];
        PsiIfStatement ifStatement = tryCast(statement, PsiIfStatement.class);
        if (ifStatement == null) return null;
        PsiExpression condition = ifStatement.getCondition();
        if (condition == null) return null;
        PsiStatement thenBranch = ifStatement.getThenBranch();
        PsiStatement elseBranch = ifStatement.getElseBranch();
        ExtremumTerminal terminal = extractIfElseCase(condition, unwrapIfBranch(thenBranch), unwrapIfBranch(elseBranch), tb);
        if (terminal != null) return terminal;
        break;
      case 2: // TODO if () .. if() ..
        break;
    }
    return null;
  }

  //It looks like repeating for refs, but can't understand how to generalize for now
  @Nullable
  private static ExtremumTerminal extractSimplePrimitiveCase(@NotNull PsiExpression filterExpression,
                                                             @NotNull PsiStatement[] statements,
                                                             @NotNull TerminalBlock terminalBlock) {
    PsiBinaryExpression binaryExpression = tryCast(filterExpression, PsiBinaryExpression.class);
    if (binaryExpression == null) return null;
    Comparision comparision = extractComparision(binaryExpression);
    if (comparision == null) return null;
    KeySelector second = comparision.getSecond();
    KeySelector first = comparision.getFirst();
    if (!hasSuitableType(first.getVariable().getType()) || !hasSuitableType(second.getVariable().getType())) return null;

    Assignment[] assignments = extractAssignments(statements);
    if (assignments == null || assignments.length != 1) return null;
    Assignment assignment = assignments[0];
    PsiVariable extremumHolder = assignment.getVariable();

    ControlFlowUtils.InitializerUsageStatus status =
      ControlFlowUtils.getInitializerUsageStatus(extremumHolder, terminalBlock.getMainLoop());
    if (!status.equals(ControlFlowUtils.InitializerUsageStatus.DECLARED_JUST_BEFORE)) return null; // TODO can't it be weaker?

    PsiExpression initializer = extremumHolder.getInitializer();
    Object expressionInitializer = ExpressionUtils.computeConstantExpression(initializer);
    if (expressionInitializer == null) return null;

    final boolean isMax;
    PsiVariable current;
    if (extremumHolder.equals(comparision.getFirst().getVariable())) {
      isMax = !comparision.isGreater();
      current = comparision.getSecond().getVariable();
    }
    else if (extremumHolder.equals(comparision.getSecond().getVariable())) {
      isMax = comparision.isGreater();
      current = comparision.getFirst().getVariable();
    }
    else {
      return null;
    }

    //TODO check that maxHolder is same for comparision

    return new ExtremumTerminal(isMax, true, comparision.getFirst(), current, extremumHolder, terminalBlock, expressionInitializer);
  }

  private static boolean hasSuitableType(@NotNull PsiType type) {
    return type.equals(PsiType.INT) || type.equals(PsiType.LONG) || type.equals(PsiType.DOUBLE);
  }

  @NotNull
  private static PsiStatement[] unwrapIfBranch(@Nullable PsiStatement statement) {
    PsiBlockStatement blockStatement = tryCast(statement, PsiBlockStatement.class);
    if (blockStatement != null) {
      return blockStatement.getCodeBlock().getStatements();
    }
    return new PsiStatement[]{statement};
  }

  //if(maxPerson == null) {
  //  maxPerson = current;
  //} else if (maxPerson.getAge() < person.getAge()) {
  //  maxPerson = current;
  //}
  @Nullable
  private static ExtremumTerminal extractIfElseCase(@NotNull PsiExpression condition,
                                                    @NotNull PsiStatement[] thenStatements,
                                                    @NotNull PsiStatement[] elseStatements,
                                                    @NotNull TerminalBlock terminalBlock) {
    PsiBinaryExpression conditionExpression = tryCast(condition, PsiBinaryExpression.class);
    if (conditionExpression == null) return null;
    PsiVariable extremumHolder = extractNullCheckingVar(conditionExpression);
    if (extremumHolder == null) return null;

    if (thenStatements.length != 1) return null;
    PsiStatement firstWayStatement = thenStatements[0];

    if (elseStatements.length != 1) return null;
    PsiStatement elseStatement = elseStatements[0];
    PsiIfStatement nestedIf = tryCast(elseStatement, PsiIfStatement.class);
    if (nestedIf == null) return null;
    PsiExpression nestedIfCondition = nestedIf.getCondition();
    if (nestedIfCondition == null) return null;
    PsiStatement nestedThenBranch = nestedIf.getThenBranch();
    if (nestedThenBranch == null) return null;
    PsiStatement nestedElseBranch = nestedIf.getElseBranch();
    if (nestedElseBranch != null) return null;
    PsiStatement[] nestedElseStatements = unwrapIfBranch(nestedThenBranch);
    if (nestedElseStatements.length != 1) return null;
    PsiStatement maxReplacingStatement = nestedElseStatements[0];


    if (!ourEquivalence.statementsAreEquivalent(maxReplacingStatement, firstWayStatement)) return null;
    return extractSimpleRefCaseOriented(conditionExpression, nestedIfCondition, nestedElseStatements, terminalBlock);
  }

  // maxPerson == null || maxPerson.getAge() < person.getAge()
  @Nullable
  private static ExtremumTerminal extractSimpleRefCase(@NotNull PsiExpression filterExpression,
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
    if (rOperand == null) return null;
    ExtremumTerminal terminal = extractSimpleRefCaseOriented(lOperand, rOperand, statements, terminalBlock);
    if (terminal != null) return terminal;
    return extractSimpleRefCaseOriented(rOperand, lOperand, statements, terminalBlock);
  }

  @Nullable
  private static ExtremumTerminal extractSimpleRefCaseOriented(@NotNull PsiExpression nullCheck,
                                                               @NotNull PsiExpression comparisionExpression,
                                                               @NotNull PsiStatement[] statements,
                                                               @NotNull TerminalBlock terminalBlock) {
    if (nullCheck instanceof PsiBinaryExpression) {
      PsiVariable extremumHolder = extractNullCheckingVar((PsiBinaryExpression)nullCheck);
      if (comparisionExpression instanceof PsiBinaryExpression && extremumHolder != null) {

        Comparision comparision = extractComparision((PsiBinaryExpression)comparisionExpression);
        if (comparision == null) return null;
        Assignment[] assignments = extractAssignments(statements);
        if (assignments == null) return null;
        return extractRefExtremumTerminal(comparision, assignments, extremumHolder, terminalBlock);
      }
    }
    return null;
  }

  @Nullable
  static private String getComparingMethod(@NotNull PsiType type) {
    if (type.equals(PsiType.INT)) return "comparingInt";
    if (type.equals(PsiType.DOUBLE)) return "comparingDouble";
    if (type.equals(PsiType.LONG)) return "comparingLong";
    return null;
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
  private static ExtremumTerminal extractRefExtremumTerminal(@NotNull Comparision comparision,
                                                             @NotNull Assignment[] assignments,
                                                             @NotNull PsiVariable nullCheckedHolder,
                                                             @NotNull TerminalBlock terminalBlock) {
    PsiExpression initializer = nullCheckedHolder.getInitializer();
    if (initializer == null || !PsiType.NULL.equals(initializer.getType())) return null;

    ControlFlowUtils.InitializerUsageStatus status =
      ControlFlowUtils.getInitializerUsageStatus(nullCheckedHolder, terminalBlock.getMainLoop());
    if (!status.equals(ControlFlowUtils.InitializerUsageStatus.DECLARED_JUST_BEFORE)) return null; // TODO can't it be weaker?

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

    PsiType lambdaType = comparisionKeySelector.acceptingType();
    if (!hasSuitableType(lambdaType)) return null;

    if (assignments.length == 1) {
      Assignment assignment = assignments[0];
      if (!assignment.getVariable().equals(nullCheckedHolder)) return null;
      if (assignment.hasSameVariables(comparisionExtremumHolder, comparisionCurrent)) return null;
      return new ExtremumTerminal(isMax, false, comparisionKeySelector, comparisionCurrent, comparisionExtremumHolder, terminalBlock, null);
    }
    else if (assignments.length == 2) {
      //if(max == null || maxAge < current.getAge()) {max =
      Assignment first = assignments[0];
      Assignment second = assignments[1];
      if (first.getVariable().equals(nullCheckedHolder) && first.hasSameVariables(comparisionExtremumHolder, comparisionCurrent)) {
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
    if (methodCallExpression == null) return null;
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
    /**
     * @param varName that can be used in comparator lambda
     * @return lambda text to create comparator
     */
    @NotNull
    String getLambdaText(@NotNull String varName);


    /**
     * @return type of comparator function
     */
    @NotNull
    PsiType acceptingType();

    /**
     * @return variable used in original key selection expression
     */
    @NotNull
    PsiVariable getVariable();

    @Nullable
    static KeySelector extractKeySelector(@NotNull PsiExpression expression) {
      MethodKeySelector methodKeySelector = MethodKeySelector.extract(expression);
      if (methodKeySelector != null) return methodKeySelector;
      FieldKeySelector fieldKeySelector = FieldKeySelector.extract(expression);
      if (fieldKeySelector != null) return fieldKeySelector;
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
    public String getLambdaText(@NotNull String varName) {
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
    public String getLambdaText(@NotNull String varName) {
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

    @Override
    public boolean equals(Object obj) {
      return obj instanceof VariableKeySelector;
    }

    @Nullable
    static VariableKeySelector extract(@NotNull PsiExpression expression) {
      PsiReferenceExpression referenceExpression = tryCast(expression, PsiReferenceExpression.class);
      if (referenceExpression == null) return null;
      PsiVariable variable = tryCast(referenceExpression.resolve(), PsiVariable.class);
      if (variable == null) return null;
      return new VariableKeySelector(variable);
    }
  }

  private static class FieldKeySelector implements KeySelector {
    private final @NotNull PsiField myField;
    private final @NotNull PsiVariable myVariable;

    private FieldKeySelector(@NotNull PsiField field, @NotNull PsiVariable variable) {
      myField = field;
      myVariable = variable;
    }

    @NotNull
    @Override
    public String getLambdaText(@NotNull String varName) {
      return varName + "->" + varName + "." + myField.getName();
    }

    @NotNull
    @Override
    public PsiType acceptingType() {
      return myField.getType();
    }

    @NotNull
    @Override
    public PsiVariable getVariable() {
      return myVariable;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof FieldKeySelector && ((FieldKeySelector)obj).myField.equals(this.myField);
    }

    @Nullable
    static FieldKeySelector extract(@NotNull PsiExpression expression) {
      PsiReferenceExpression referenceExpression = tryCast(expression, PsiReferenceExpression.class);
      if (referenceExpression == null) return null;
      PsiField field = tryCast(referenceExpression.resolve(), PsiField.class);
      if (field == null) return null;
      PsiElement qualifier = referenceExpression.getQualifier();
      PsiReference reference = tryCast(qualifier, PsiReference.class);
      if (reference == null) return null;
      PsiVariable variable = tryCast(reference.resolve(), PsiVariable.class);
      if (variable == null) return null;
      return new FieldKeySelector(field, variable);
    }
  }

  private static class Assignment {
    private final @NotNull PsiVariable myVariable;
    private final @NotNull PsiExpression myExpression;

    private Assignment(@NotNull PsiVariable variable, @NotNull PsiExpression expression) {
      myVariable = variable;
      myExpression = expression;
    }

    @NotNull
    public PsiVariable getVariable() {
      return myVariable;
    }

    @NotNull
    public PsiExpression getExpression() {
      return myExpression;
    }

    @Override
    public int hashCode() {
      int result = myVariable.hashCode();
      result = 31 * result + myExpression.hashCode();
      return result;
    }

    private boolean hasSameVariables(@NotNull PsiVariable extremumHolder, @NotNull PsiVariable current) {
      PsiVariable rVariable = tryCast(myExpression, PsiVariable.class);
      return rVariable != null & myVariable.equals(extremumHolder) && rVariable.equals(current);
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

    @NotNull
    public KeySelector getSecond() {
      return mySecond;
    }

    @NotNull
    public KeySelector getFirst() {
      return myFirst;
    }
  }

  static class ExtremumTerminal {
    private final boolean myIsMax;
    private final boolean myIsPrimitive;
    private final @NotNull KeySelector myKeySelector; // field or method
    private final @NotNull PsiVariable myCurrentVariable;
    private final @NotNull PsiVariable myExtremumHolder;
    private final @NotNull TerminalBlock myTerminalBlock;
    private final @Nullable Object myStartingValue;

    public ExtremumTerminal(boolean isMax,
                            boolean isPrimitive, @NotNull KeySelector keySelector,
                            @NotNull PsiVariable currentVariable,
                            @NotNull PsiVariable extremumHolder,
                            @NotNull TerminalBlock terminalBlock,
                            @Nullable Object startingValue) {
      myIsMax = isMax;
      myIsPrimitive = isPrimitive;
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
    public Object getStartingValue() {
      return myStartingValue;
    }

    @NotNull
    public TerminalBlock getTerminalBlock() {
      return myTerminalBlock;
    }

    public boolean isPrimitive() {
      return myIsPrimitive;
    }
  }
}
