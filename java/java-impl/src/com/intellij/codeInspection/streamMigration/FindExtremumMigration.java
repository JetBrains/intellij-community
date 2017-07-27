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

public class FindExtremumMigration extends BaseStreamApiMigration {

  static final String MAX_REPLACEMENT = "max()";
  static final String MIN_REPLACEMENT = "min()";

  private static final EquivalenceChecker ourEquivalence = EquivalenceChecker.getCanonicalPsiEquivalence();

  protected FindExtremumMigration(boolean shouldWarn, String replacement) {
    super(shouldWarn, replacement);
  }

  @Override
  PsiElement migrate(@NotNull Project project, @NotNull PsiStatement body, @NotNull TerminalBlock tb) {
    ExtremumTerminal terminal = extract(tb);
    if (terminal == null) return null;
    String operation = terminal.isMax() ? "max" : "min";

    KeySelector keySelector = terminal.getKeySelector();
    PsiVariable currentVariable =
      terminal.getLoopVariable();
    JavaCodeStyleManager javaStyle = JavaCodeStyleManager.getInstance(project);
    String name = currentVariable.getName();
    if (name == null) name = "x";
    javaStyle.suggestUniqueVariableName(name, body, true);
    String lambdaText = keySelector.buildLambda(name);
    PsiType lambdaReturnType = keySelector.selectedKeyType();
    String comparingMethod = getComparingMethod(lambdaReturnType);
    if (comparingMethod == null) return null;
    String comparator = CommonClassNames.JAVA_UTIL_COMPARATOR + "." + comparingMethod + "(" + lambdaText + ")";

    TerminalBlock terminalBlock = terminal.getTerminalBlock();

    String stream = buildStreamText(terminal, operation, comparator, name, terminalBlock);
    PsiLoopStatement loop = terminalBlock.getMainLoop();
    return replaceWithFindExtremum(loop, terminal.getExtremumHolder(), stream, terminal.getKeyExtremum());
  }

  @NotNull
  String buildStreamText(@NotNull ExtremumTerminal terminal,
                         @NotNull String operation,
                         @NotNull String comparator,
                         @NotNull String varName,
                         @NotNull TerminalBlock terminalBlock) {
    String startingValue;
    String filterOp;
    if (terminal.getKeySelectorInitializer() != null) {
      PsiExpression initializer = terminal.getKeySelectorInitializer();
      startingValue = initializer.getText();
      String keySelectorText = terminal.getKeySelector().buildExpression(varName).getText();
      String inFilterOperation = terminal.isMax() ? ">=" : "<=";
      filterOp = ".filter(" + varName + "->" + keySelectorText + inFilterOperation + startingValue + ")";
    }
    else {
      startingValue = "";
      filterOp = "";
    }
    if (terminal.isPrimitive()) {
      return terminalBlock.generate() + filterOp + "." + operation + "().orElse(" + startingValue + ")";
    }
    return terminalBlock.generate() + filterOp + "." + operation + "(" + comparator + ").orElse(null)";
  }

  @Nullable
  static private String getComparingMethod(@NotNull PsiType type) {
    if (type.equals(PsiType.INT)) return "comparingInt";
    if (type.equals(PsiType.DOUBLE)) return "comparingDouble";
    if (type.equals(PsiType.LONG)) return "comparingLong";
    return null;
  }

  @Nullable
  static ExtremumTerminal extract(@NotNull TerminalBlock terminalBlock) {
    PsiStatement[] statements = terminalBlock.getStatements();
    StreamApiMigrationInspection.Operation operation = terminalBlock.getLastOperation();
    StreamApiMigrationInspection.FilterOp filterOp = tryCast(operation, StreamApiMigrationInspection.FilterOp.class);
    if (filterOp != null) {
      TerminalBlock block = terminalBlock.withoutLastOperation();
      if (block != null) {
        PsiExpression condition = filterOp.getExpression();
        ExtremumTerminal simpleRefCase = extractSimpleRefCase(condition, statements, block);
        if (simpleRefCase != null) return simpleRefCase;
        ExtremumTerminal primitiveCase = extractSimplePrimitiveCase(condition, statements, block);
        if (primitiveCase != null) return primitiveCase;
        ExtremumTerminal complexRefCase = extractComplexRefCase(condition, statements, block);
        if (complexRefCase != null) return complexRefCase;
      }
    }
    return extractIfElseCase(statements, terminalBlock);
  }

  //if(maxPerson == null) {
  //  maxPerson = current;
  //} else if (maxPerson.getAge() < person.getAge()) {
  //  maxPerson = current;
  //}
  @Nullable
  static private ExtremumTerminal extractIfElseCase(@NotNull PsiStatement[] statements,
                                                    @NotNull TerminalBlock terminalBlock) {
    if (statements.length != 1) return null;
    PsiStatement statement = statements[0];
    PsiIfStatement ifStatement = tryCast(statement, PsiIfStatement.class);
    if (ifStatement == null) return null;
    PsiExpression thenCondition = ifStatement.getCondition();
    if (thenCondition == null) return null;
    PsiStatement thenBranch = unwrapIfBranchToStatement(ifStatement.getThenBranch());
    PsiStatement elseBranch = unwrapIfBranchToStatement(ifStatement.getElseBranch());
    if (thenBranch == null || elseBranch == null) return null;
    return extractIfElseCase(thenBranch, thenCondition, elseBranch, terminalBlock);
  }

  @Nullable
  static private ExtremumTerminal extractIfElseCase(@NotNull PsiStatement thenBranch,
                                                    @NotNull PsiExpression thenCondition,
                                                    @NotNull PsiStatement elseBranch,
                                                    @NotNull TerminalBlock terminalBlock) {
    PsiIfStatement elseIfStatement = tryCast(elseBranch, PsiIfStatement.class);
    if (elseIfStatement == null || elseIfStatement.getElseBranch() != null) return null;
    PsiExpression elseIfCondition = elseIfStatement.getCondition();
    PsiStatement elseIf = unwrapIfBranchToStatement(elseIfStatement.getThenBranch());
    if (elseIf == null || elseIfCondition == null) return null;
    ExtremumTerminal firstWay = extractIfElseCase(thenBranch, thenCondition, elseIf, elseIfCondition, terminalBlock);
    if (firstWay != null) return firstWay;
    return extractIfElseCase(elseIf, elseIfCondition, thenBranch, thenCondition, terminalBlock);
  }

  @Nullable
  static private ExtremumTerminal extractIfElseCase(@NotNull PsiStatement nullCheckBranch,
                                                    @NotNull PsiExpression nullCheckExpr,
                                                    @NotNull PsiStatement comparisionBranch,
                                                    @NotNull PsiExpression comparisionExpr,
                                                    @NotNull TerminalBlock terminalBlock) {
    if (!ourEquivalence.statementsAreEquivalent(nullCheckBranch, comparisionBranch)) return null;
    return extractSimpleRefCase(nullCheckExpr, comparisionExpr, new PsiStatement[]{comparisionBranch}, terminalBlock);
  }

  @Nullable
  static private PsiStatement unwrapIfBranchToStatement(@Nullable PsiStatement statement) {
    PsiStatement[] statements = unwrapIfBranch(statement);
    if (statements.length != 1) return null;
    return statements[0];
  }

  @NotNull
  static private PsiStatement[] unwrapIfBranch(@Nullable PsiStatement statement) {
    PsiBlockStatement blockStatement = tryCast(statement, PsiBlockStatement.class);
    if (blockStatement != null) {
      return blockStatement.getCodeBlock().getStatements();
    }
    return new PsiStatement[]{statement};
  }

  //Person maxPerson = null;
  //int maxAge = Integer.MIN_VALUE;
  //for (Person person : personList) {
  //  if (maxPerson == null || maxAge < person.getAge()) {
  //    maxPerson = person;
  //    maxAge = person.getAge();
  //  }
  //}
  @Nullable
  static private ExtremumTerminal extractComplexRefCase(@NotNull PsiExpression condition,
                                                        @NotNull PsiStatement[] statements,
                                                        @NotNull TerminalBlock terminalBlock) {
    return extractRefCase(condition, statements, terminalBlock, FindExtremumMigration::extractComplexRefCase);
  }

  @Nullable
  static private ExtremumTerminal extractComplexRefCase(@NotNull PsiExpression nullCheckExpr,
                                                        @NotNull PsiExpression comparisionExpr,
                                                        @NotNull PsiStatement[] statements,
                                                        @NotNull TerminalBlock terminalBlock) {
    PsiBinaryExpression nullCheckBinary = tryCast(nullCheckExpr, PsiBinaryExpression.class);
    if (nullCheckBinary == null) return null;
    PsiVariable nullCheckingVar = extractNullCheckingVar(nullCheckBinary);
    if (nullCheckingVar == null) return null;
    Comparision comparision = Comparision.extract(comparisionExpr);
    if (comparision == null) return null;
    ComplexAssignment complexAssignment = ComplexAssignment.extractComplex(statements);
    if (complexAssignment == null) return null;
    Boolean isMax = isMax(complexAssignment, comparision);
    if (isMax == null ||
        !useSameVariables(complexAssignment, comparision, isMax) ||
        changedBeforeLoop(complexAssignment.getExtremumHolder(), terminalBlock) ||
        changedBeforeLoop(complexAssignment.getExtremumKey(), terminalBlock) ||
        !terminalBlock.getVariable().equals(complexAssignment.getLoopVariable())) {
      return null;
    }

    PsiExpression keyInitializer = complexAssignment.getExtremumKey().getInitializer();
    PsiExpression extremumHolderInitializer = complexAssignment.getExtremumHolder().getInitializer();
    if (!ExpressionUtils.isNullLiteral(extremumHolderInitializer)) return null;
    if (ExpressionUtils.computeConstantExpression(keyInitializer) == null) return null;

    return new ExtremumTerminal(2, false, isMax, complexAssignment.getExtremumKeySelector(), complexAssignment.getLoopVariable(),
                                complexAssignment.getExtremumHolder(), terminalBlock, keyInitializer, complexAssignment.getExtremumKey());
  }

  //if(max < anInt) {
  //max = anInt;
  //}
  @Nullable
  private static ExtremumTerminal extractSimplePrimitiveCase(@NotNull PsiExpression condition,
                                                             @NotNull PsiStatement[] statements,
                                                             @NotNull TerminalBlock terminalBlock) {
    Comparision comparision = Comparision.extract(condition);
    if (comparision == null) return null;
    SimpleAssignment assignment = SimpleAssignment.extractSimple(statements);
    if (assignment == null) return null;
    if (!(comparision.getFirstSelector() instanceof VariableKeySelector) ||
        !(comparision.getSecondSelector() instanceof VariableKeySelector)) {
      return null;
    }
    Boolean max = isMax(assignment, comparision);
    if (max == null ||
        !useSameVariables(assignment, comparision, max) ||
        changedBeforeLoop(comparision.getExtremumHolderSelector(max).getVariable(), terminalBlock) ||
        !terminalBlock.getVariable().equals(assignment.getLoopVariable())) {
      return null;
    }

    KeySelector extremumHolderSelector = comparision.getExtremumHolderSelector(max);
    PsiVariable extremumHolder = extremumHolderSelector.getVariable();
    PsiExpression initializer = extremumHolder.getInitializer();
    if (ExpressionUtils.computeConstantExpression(initializer) == null) return null;
    return new ExtremumTerminal(1, true, max, extremumHolderSelector, assignment.getLoopVariable(),
                                assignment.getExtremumHolder(), terminalBlock, initializer, null);
  }

  @Nullable
  static private ExtremumTerminal extractSimpleRefCase(@NotNull PsiExpression condition,
                                                       @NotNull PsiStatement[] statements,
                                                       @NotNull TerminalBlock terminalBlock) {
    return extractRefCase(condition, statements, terminalBlock, FindExtremumMigration::extractSimpleRefCase);
  }

  @Nullable
  static private ExtremumTerminal extractRefCase(@NotNull PsiExpression condition,
                                                 @NotNull PsiStatement[] statements,
                                                 @NotNull TerminalBlock terminalBlock,
                                                 @NotNull Extractor extractor) {
    PsiBinaryExpression binaryExpression = tryCast(condition, PsiBinaryExpression.class);
    if (binaryExpression == null) return null;
    IElementType sign = binaryExpression.getOperationSign().getTokenType();
    PsiExpression lOperand = binaryExpression.getLOperand();
    PsiExpression rOperand = binaryExpression.getROperand();
    if (!sign.equals(JavaTokenType.OROR) || rOperand == null) return null;
    ExtremumTerminal firstWay = extractor.extractOriented(lOperand, rOperand, statements, terminalBlock);
    if (firstWay != null) return firstWay;
    return extractor.extractOriented(rOperand, lOperand, statements, terminalBlock);
  }

  @Nullable
  static private ExtremumTerminal extractSimpleRefCase(@NotNull PsiExpression nullCheckExpr,
                                                       @NotNull PsiExpression comparisionExpr,
                                                       @NotNull PsiStatement[] statements,
                                                       @NotNull TerminalBlock terminalBlock) {
    PsiBinaryExpression nullCheckBinary = tryCast(nullCheckExpr, PsiBinaryExpression.class);
    if (nullCheckBinary == null) return null;
    PsiVariable nullCheckingVar = extractNullCheckingVar(nullCheckBinary);
    if (nullCheckingVar == null) return null;
    Comparision comparision = Comparision.extract(comparisionExpr);
    if (comparision == null) return null;
    SimpleAssignment simpleAssignment = SimpleAssignment.extractSimple(statements);
    if (simpleAssignment == null) return null;

    Boolean isMax = isMax(simpleAssignment, comparision);
    if (isMax == null ||
        !useSameVariables(simpleAssignment, comparision, isMax) ||
        changedBeforeLoop(simpleAssignment.getExtremumHolder(), terminalBlock) ||
        !terminalBlock.getVariable().equals(simpleAssignment.getLoopVariable())) return null;
    PsiVariable loopVariable = comparision.getLoopVariableKeySelector(isMax).getVariable();
    PsiVariable extremumHolder = comparision.getExtremumHolderSelector(isMax).getVariable();
    PsiExpression initializer = extremumHolder.getInitializer();
    if (!ExpressionUtils.isNullLiteral(initializer)) return null;
    return new ExtremumTerminal(1, false, isMax, comparision.getFirstSelector(), loopVariable, extremumHolder,
                                terminalBlock, null, null);
  }

  static private boolean changedBeforeLoop(@NotNull PsiVariable variable,
                                           @NotNull TerminalBlock terminalBlock) {
    ControlFlowUtils.InitializerUsageStatus status =
      ControlFlowUtils.getInitializerUsageStatus(variable, terminalBlock.getMainLoop());
    return status.equals(ControlFlowUtils.InitializerUsageStatus.UNKNOWN);
  }

  //  if (maxPerson == null || maxAge < person.getAge()) {
  //    maxPerson = person;
  //    maxAge = person.getAge();
  static private boolean useSameVariables(ComplexAssignment complexAssignment, Comparision comparision, boolean isMax) {
    PsiExpression extremumKeySelectorExpression = complexAssignment.getExtremumKeySelector().getExpression();
    PsiExpression loopVarKeySelector = comparision.getLoopVariableKeySelector(isMax).getExpression();
    PsiVariable loopVariable = comparision.getLoopVariableKeySelector(isMax).getVariable();
    PsiVariable extremumHolder = comparision.getExtremumHolderSelector(isMax).getVariable(); // maxAge
    return complexAssignment.getLoopVariable().equals(loopVariable) &&
           complexAssignment.getExtremumKey().equals(extremumHolder) &&
           comparision.getExtremumHolderSelector(isMax) instanceof VariableKeySelector &&
           ourEquivalence.expressionsAreEquivalent(extremumKeySelectorExpression, loopVarKeySelector);
  }

  static private boolean useSameVariables(SimpleAssignment simpleAssignment, Comparision comparision, boolean isMax) {
    return simpleAssignment.getExtremumHolder().equals(comparision.getExtremumHolderSelector(isMax).getVariable()) &&
           simpleAssignment.getLoopVariable().equals(comparision.getLoopVariableKeySelector(isMax).getVariable());
  }

  @Nullable
  static private Boolean isMax(SimpleAssignment assignment, Comparision comparision) {
    if (assignment.getExtremumHolder().equals(comparision.getFirstSelector().getVariable())) {
      return !comparision.isGreater();
    }
    else if (assignment.getExtremumHolder().equals(comparision.getSecondSelector().getVariable())) {
      return comparision.isGreater();
    }
    else {
      return null;
    }
  }

  @Nullable
  static private Boolean isMax(ComplexAssignment assignment, Comparision comparision) {
    if (assignment.getExtremumKey().equals(comparision.getFirstSelector().getVariable())) {
      return !comparision.isGreater();
    }
    else if (assignment.getExtremumKey().equals(comparision.getSecondSelector().getVariable())) {
      return comparision.isGreater();
    }
    else {
      return null;
    }
  }

  @Nullable
  private static PsiVariable extractNullCheckingVar(@NotNull PsiExpression expression) {
    PsiBinaryExpression binaryExpression = tryCast(expression, PsiBinaryExpression.class);
    if(binaryExpression == null) return null;
    PsiExpression valueComparedWithNull = ExpressionUtils.getValueComparedWithNull(binaryExpression);
    PsiReferenceExpression referenceExpression = tryCast(valueComparedWithNull, PsiReferenceExpression.class);
    if (referenceExpression == null) return null;
    return tryCast(referenceExpression.resolve(), PsiVariable.class);
  }

  @Nullable
  static PsiVariable getVariable(@NotNull PsiExpression expression) {
    PsiReferenceExpression referenceExpression = tryCast(expression, PsiReferenceExpression.class);
    if (referenceExpression == null) return null;
    return tryCast(referenceExpression.resolve(), PsiVariable.class);
  }


  @Nullable
  private static PsiVariable resolveMethodReceiver(@NotNull PsiExpression expression) {
    PsiMethodCallExpression methodCallExpression = tryCast(expression, PsiMethodCallExpression.class);
    if (methodCallExpression == null) return null;
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

  private static boolean isCorrectType(@NotNull PsiType type) {
    return type.equals(PsiType.INT) || type.equals(PsiType.LONG) || type.equals(PsiType.DOUBLE);
  }

  interface Extractor {
    @Nullable
    ExtremumTerminal extractOriented(@NotNull PsiExpression nullCheckExpr,
                                     @NotNull PsiExpression comparisionExpr,
                                     @NotNull PsiStatement[] statements,
                                     @NotNull TerminalBlock terminalBlock);
  }

  interface KeySelector {
    /**
     * @param varName that can be used in comparator lambda
     * @return lambda text to create comparator
     */
    @NotNull
    String buildLambda(@NotNull String varName);

    /**
     * @return type of selected key to be compared
     */
    @NotNull
    PsiType selectedKeyType();

    /**
     * @return variable used in original key selection expression
     */
    @NotNull
    PsiVariable getVariable();

    /**
     * @return original key selection expression
     */
    @NotNull
    PsiExpression getExpression();

    @NotNull
    PsiExpression buildExpression(@NotNull String qualifierName);

    boolean equalShape(@NotNull KeySelector keySelector);

    @Nullable
    static KeySelector extractSelector(@NotNull PsiExpression expression) {
      MethodKeySelector methodKeySelector = MethodKeySelector.extract(expression);
      if (methodKeySelector != null && isCorrectType(methodKeySelector.selectedKeyType())) return methodKeySelector;
      FieldKeySelector fieldKeySelector = FieldKeySelector.extract(expression);
      if (fieldKeySelector != null && isCorrectType(fieldKeySelector.selectedKeyType())) return fieldKeySelector;
      VariableKeySelector variableKeySelector = VariableKeySelector.extract(expression);
      if (variableKeySelector != null && isCorrectType(variableKeySelector.selectedKeyType())) return variableKeySelector;
      return null;
    }
  }

  static class MethodKeySelector implements KeySelector {
    private final @NotNull PsiMethod myMethod;
    private final @NotNull PsiMethodCallExpression myExpression;
    private final @NotNull PsiVariable myVariable;
    @NotNull private final PsiClass myContainingClass;
    @NotNull private final PsiType myType;

    MethodKeySelector(@NotNull PsiMethod method,
                      @NotNull PsiMethodCallExpression expression,
                      @NotNull PsiVariable variable,
                      @NotNull PsiClass containingClass,
                      @NotNull PsiType type) {
      myMethod = method;
      myExpression = expression;
      myVariable = variable;
      myContainingClass = containingClass;
      myType = type;
    }

    @NotNull
    @Override
    public String buildLambda(@NotNull String varName) {
      return myContainingClass.getQualifiedName() + "::" + myMethod.getName();
    }

    @NotNull
    @Override
    public PsiType selectedKeyType() {
      return myType;
    }

    @NotNull
    @Override
    public PsiVariable getVariable() {
      return myVariable;
    }

    @NotNull
    @Override
    public PsiExpression getExpression() {
      return myExpression;
    }

    @NotNull
    @Override
    public PsiExpression buildExpression(@NotNull String qualifierName) {
      PsiMethodCallExpression callExpression = (PsiMethodCallExpression)myExpression.copy();
      PsiReferenceExpression qualifierReference =
        tryCast(callExpression.getMethodExpression().getQualifierExpression(), PsiReferenceExpression.class);
      //noinspection ConstantConditions checked  at creation
      ExpressionUtils.bindReferenceTo(qualifierReference, qualifierName);
      return callExpression;
    }

    @Override
    public boolean equalShape(@NotNull KeySelector keySelector) {
      return keySelector instanceof MethodKeySelector &&
             ((MethodKeySelector)keySelector).myMethod.equals(myMethod);
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
      PsiMethodCallExpression methodCallExpression = tryCast(expression, PsiMethodCallExpression.class);
      if (methodCallExpression == null) return null;
      PsiExpression qualifierExpression = methodCallExpression.getMethodExpression().getQualifierExpression();
      if (qualifierExpression == null) return null;
      if(tryCast(qualifierExpression, PsiReferenceExpression.class) == null) return null;
      return new MethodKeySelector(method, methodCallExpression, receiver, containingClass, returnType);
    }
  }

  private static class VariableKeySelector implements KeySelector {
    private final PsiVariable myVariable;
    private final PsiReferenceExpression myReferenceExpression;

    private VariableKeySelector(PsiVariable variable, PsiReferenceExpression expression) {
      myVariable = variable;
      myReferenceExpression = expression;
    }

    @NotNull
    @Override
    public String buildLambda(@NotNull String varName) {
      String variableText = myVariable.getText();
      return variableText + "->" + variableText;
    }

    @NotNull
    @Override
    public PsiType selectedKeyType() {
      return myVariable.getType();
    }

    @NotNull
    @Override
    public PsiVariable getVariable() {
      return myVariable;
    }

    @NotNull
    @Override
    public PsiExpression getExpression() {
      return myReferenceExpression;
    }

    @NotNull
    @Override
    public PsiExpression buildExpression(@NotNull String qualifierName) {
      PsiReferenceExpression referenceExpression = (PsiReferenceExpression)myReferenceExpression.copy();
      ExpressionUtils.bindReferenceTo(referenceExpression, qualifierName);
      return referenceExpression;
    }

    @Override
    public boolean equalShape(@NotNull KeySelector keySelector) {
      return keySelector instanceof VariableKeySelector;
    }

    @Nullable
    static VariableKeySelector extract(@NotNull PsiExpression expression) {
      PsiReferenceExpression referenceExpression = tryCast(expression, PsiReferenceExpression.class);
      if (referenceExpression == null) return null;
      PsiVariable variable = tryCast(referenceExpression.resolve(), PsiVariable.class);
      if (variable == null) return null;
      return new VariableKeySelector(variable, referenceExpression);
    }
  }

  private static class FieldKeySelector implements KeySelector {
    private final @NotNull PsiField myField;
    private final @NotNull PsiVariable myVariable;
    private final @NotNull PsiReferenceExpression myReferenceExpression;

    private FieldKeySelector(@NotNull PsiField field,
                             @NotNull PsiVariable variable,
                             @NotNull PsiReferenceExpression referenceExpression) {
      myField = field;
      myVariable = variable;
      myReferenceExpression = referenceExpression;
    }

    @NotNull
    @Override
    public String buildLambda(@NotNull String varName) {
      return varName + "->" + varName + "." + myField.getName();
    }

    @NotNull
    @Override
    public PsiType selectedKeyType() {
      return myField.getType();
    }

    @NotNull
    @Override
    public PsiVariable getVariable() {
      return myVariable;
    }

    @NotNull
    @Override
    public PsiExpression getExpression() {
      return myReferenceExpression;
    }

    @NotNull
    @Override
    public PsiExpression buildExpression(@NotNull String referenceName) {
      PsiReferenceExpression copy = (PsiReferenceExpression)myReferenceExpression.copy();
      //noinspection ConstantConditions - checked at creation
      ExpressionUtils.bindReferenceTo(copy, referenceName);
      return copy;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof FieldKeySelector && ((FieldKeySelector)obj).myField.equals(myField);
    }

    @Override
    public boolean equalShape(@NotNull KeySelector keySelector) {
      return keySelector instanceof FieldKeySelector && ((FieldKeySelector)keySelector).myField.equals(myField);
    }

    @Nullable
    static FieldKeySelector extract(@NotNull PsiExpression expression) {
      PsiReferenceExpression referenceExpression = tryCast(expression, PsiReferenceExpression.class);
      if (referenceExpression == null) return null;
      PsiField field = tryCast(referenceExpression.resolve(), PsiField.class);
      if (field == null) return null;
      PsiElement qualifier = referenceExpression.getQualifier();
      if (referenceExpression.getQualifierExpression() == null) return null;
      PsiReference reference = tryCast(qualifier, PsiReference.class);
      if (reference == null) return null;
      PsiVariable variable = tryCast(reference.resolve(), PsiVariable.class);
      if (variable == null) return null;
      return new FieldKeySelector(field, variable, referenceExpression);
    }
  }

  static class ExtremumTerminal {
    private final int myNonFinalVariableCount;
    private final boolean isPrimitive;
    private final boolean isMax;
    private final @NotNull KeySelector myKeySelector;
    private final @NotNull PsiVariable myLoopVariable;
    private final @NotNull PsiVariable myExtremumHolder;
    private final @NotNull TerminalBlock myTerminalBlock;
    private final @Nullable PsiExpression myKeySelectorInitializer;
    private final @Nullable PsiVariable myKeyExtremum;

    public ExtremumTerminal(int nonFinalVariableCount,
                            boolean isPrimitive,
                            boolean isMax,
                            @NotNull KeySelector keySelector,
                            @NotNull PsiVariable loopVariable,
                            @NotNull PsiVariable extremumHolder,
                            @NotNull TerminalBlock block, @Nullable PsiExpression initializer, @Nullable PsiVariable keyExtremum) {
      myNonFinalVariableCount = nonFinalVariableCount;
      this.isPrimitive = isPrimitive;
      this.isMax = isMax;
      myKeySelector = keySelector;
      myLoopVariable = loopVariable;
      myExtremumHolder = extremumHolder;
      myTerminalBlock = block;
      myKeySelectorInitializer = initializer;
      myKeyExtremum = keyExtremum;
    }

    public int getNonFinalVariableCount() {
      return myNonFinalVariableCount;
    }

    public boolean isPrimitive() {
      return isPrimitive;
    }

    public boolean isMax() {
      return isMax;
    }

    @NotNull
    public KeySelector getKeySelector() {
      return myKeySelector;
    }

    @NotNull
    public PsiVariable getLoopVariable() {
      return myLoopVariable;
    }

    @NotNull
    public PsiVariable getExtremumHolder() {
      return myExtremumHolder;
    }

    @Nullable
    public PsiExpression getKeySelectorInitializer() {
      return myKeySelectorInitializer;
    }

    @NotNull
    public TerminalBlock getTerminalBlock() {
      return myTerminalBlock;
    }

    @Nullable
    public PsiVariable getKeyExtremum() {
      return myKeyExtremum;
    }
  }

  private static abstract class Assignment {
    private final @NotNull PsiVariable myExtremumHolder;
    private final @NotNull PsiVariable myLoopVariable;

    public Assignment(@NotNull PsiVariable extremumHolder, @NotNull PsiVariable loopVariable) {
      myExtremumHolder = extremumHolder;
      myLoopVariable = loopVariable;
    }

    @NotNull
    public PsiVariable getExtremumHolder() {
      return myExtremumHolder;
    }

    @NotNull
    public PsiVariable getLoopVariable() {
      return myLoopVariable;
    }
  }

  private static class SimpleAssignment extends Assignment {
    private SimpleAssignment(@NotNull PsiVariable extremum, @NotNull PsiVariable loopVariable) {
      super(extremum, loopVariable);
    }

    @Nullable
    static SimpleAssignment extractSimple(@NotNull PsiStatement[] statements) {
      if (statements.length != 1) return null;
      PsiStatement statement = statements[0];
      PsiExpressionStatement exprStatement = tryCast(statement, PsiExpressionStatement.class);
      if (exprStatement == null) return null;
      PsiAssignmentExpression assignment = tryCast(exprStatement.getExpression(), PsiAssignmentExpression.class);
      PsiVariable extremumHolder = getVariable(assignment.getLExpression());
      if (extremumHolder == null) return null;
      PsiExpression rExpression = assignment.getRExpression();
      if (rExpression == null) return null;
      PsiVariable loopVar = getVariable(rExpression);
      if (loopVar == null) return null;
      return new SimpleAssignment(extremumHolder, loopVar);
    }
  }

  private static class ComplexAssignment extends Assignment {
    private final @NotNull PsiVariable myExtremumKey;
    private final @NotNull KeySelector myExtremumKeySelector;

    public ComplexAssignment(@NotNull PsiVariable extremum,
                             @NotNull PsiVariable loopVariable,
                             @NotNull PsiVariable extremumKey,
                             @NotNull KeySelector selector) {
      super(extremum, loopVariable);
      myExtremumKey = extremumKey;
      myExtremumKeySelector = selector;
    }

    @NotNull
    public PsiVariable getExtremumKey() {
      return myExtremumKey;
    }

    @NotNull
    public KeySelector getExtremumKeySelector() {
      return myExtremumKeySelector;
    }

    @Nullable
    static ComplexAssignment extractComplex(@NotNull PsiStatement[] statements) {
      if (statements.length != 2) return null;
      PsiStatement first = statements[0];
      PsiStatement second = statements[1];
      ComplexAssignment firstWay = extractComplex(first, second);
      if (firstWay != null) return firstWay;
      return extractComplex(second, first);
    }

    @Nullable
    static ComplexAssignment extractComplex(@NotNull PsiStatement first, @NotNull PsiStatement second) {
      PsiExpressionStatement firstExpr = tryCast(first, PsiExpressionStatement.class);
      PsiExpressionStatement secondExpr = tryCast(second, PsiExpressionStatement.class);
      if (firstExpr == null || secondExpr == null) return null;
      PsiAssignmentExpression firstAssignment = tryCast(firstExpr.getExpression(), PsiAssignmentExpression.class);
      PsiAssignmentExpression secondAssignment = tryCast(secondExpr.getExpression(), PsiAssignmentExpression.class);
      if (firstAssignment == null || secondAssignment == null) return null;
      ComplexAssignment firstWay = extractComplex(firstAssignment, secondAssignment);
      if (firstWay != null) return firstWay;
      return extractComplex(secondAssignment, firstAssignment);
    }

    @Nullable
    static ComplexAssignment extractComplex(@NotNull PsiAssignmentExpression first, @NotNull PsiAssignmentExpression second) {
      PsiExpression firstRExpression = first.getRExpression();
      PsiExpression secondRExpression = second.getRExpression();
      if (firstRExpression == null || secondRExpression == null) return null;
      PsiVariable extremum = getVariable(first.getLExpression());
      if (extremum == null) return null;
      PsiVariable loopVar = getVariable(firstRExpression);
      if (loopVar == null) return null;
      PsiVariable extremumKeyHolder = getVariable(second.getLExpression());
      if (extremumKeyHolder == null) return null;
      KeySelector loopVarKeySelector = KeySelector.extractSelector(secondRExpression);
      if (loopVarKeySelector == null) return null;
      return new ComplexAssignment(extremum, loopVar, extremumKeyHolder, loopVarKeySelector);
    }
  }

  private static class Comparision {
    private final @NotNull KeySelector myFirstSelector;
    private final @NotNull KeySelector mySecondSelector;
    private final boolean myIsGreater;

    private Comparision(@NotNull KeySelector firstSelector, @NotNull KeySelector secondSelector, boolean greater) {
      myFirstSelector = firstSelector;
      mySecondSelector = secondSelector;
      myIsGreater = greater;
    }

    @NotNull
    public KeySelector getFirstSelector() {
      return myFirstSelector;
    }

    @NotNull
    public KeySelector getSecondSelector() {
      return mySecondSelector;
    }

    public boolean isGreater() {
      return myIsGreater;
    }

    public KeySelector getExtremumHolderSelector(boolean isMax) {
      if (myIsGreater) {
        return !isMax ? myFirstSelector : mySecondSelector;
      }
      else {
        return isMax ? myFirstSelector : mySecondSelector;
      }
    }

    public KeySelector getLoopVariableKeySelector(boolean isMax) {
      if (myIsGreater) {
        return isMax ? myFirstSelector : mySecondSelector;
      }
      else {
        return !isMax ? myFirstSelector : mySecondSelector;
      }
    }

    @Nullable
    static Comparision extract(@NotNull PsiExpression expression) {
      PsiBinaryExpression binaryExpression = tryCast(expression, PsiBinaryExpression.class);
      IElementType sign = binaryExpression.getOperationSign().getTokenType();
      PsiExpression rOperand = binaryExpression.getROperand();
      if (rOperand == null) return null;
      PsiExpression lOperand = binaryExpression.getLOperand();
      if (sign.equals(JavaTokenType.LT) || sign.equals(JavaTokenType.LE)) {
        return extract(lOperand, rOperand, false);
      }
      else if (sign.equals(JavaTokenType.GT) || sign.equals(JavaTokenType.GE)) {
        return extract(lOperand, rOperand, true);
      }
      return null;
    }

    @Nullable
    private static Comparision extract(@NotNull PsiExpression first, @NotNull PsiExpression second, boolean isGreater) {
      KeySelector firstSelector = KeySelector.extractSelector(first);
      KeySelector secondSelector = KeySelector.extractSelector(second);
      if (firstSelector == null || secondSelector == null) return null;
      return new Comparision(firstSelector, secondSelector, isGreater);
    }
  }
}