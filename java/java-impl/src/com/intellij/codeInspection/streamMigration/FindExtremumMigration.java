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
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.psi.util.PsiUtil.skipParenthesizedExprDown;
import static com.intellij.util.ObjectUtils.tryCast;

class FindExtremumMigration extends BaseStreamApiMigration {

  private static final String MAX_OP = "max";
  private static final String MIN_OP = "min";
  private static final EquivalenceChecker ourEquivalence = EquivalenceChecker.getCanonicalPsiEquivalence();

  protected FindExtremumMigration(boolean shouldWarn, String replacement) {
    super(shouldWarn, replacement);
  }

  @Override
  PsiElement migrate(@NotNull Project project, @NotNull PsiStatement body, @NotNull TerminalBlock tb) {
    ExtremumTerminal terminal = extract(tb, null);
    if (terminal == null) return null;
    return terminal.replace();
  }

  @Contract(pure = true)
  @NotNull
  static String getOperation(boolean isMax) {
    return isMax ? MAX_OP : MIN_OP;
  }

  @Nullable
  static private String getComparingMethod(@NotNull PsiType type) {
    if (type.equals(PsiType.INT)) return "comparingInt";
    if (type.equals(PsiType.DOUBLE)) return "comparingDouble";
    if (type.equals(PsiType.LONG)) return "comparingLong";
    if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_COMPARABLE)) return "comparing";
    return null;
  }

  @Nullable
  static private Object getNonFilterableInitialValue(@NotNull PsiType type, boolean isMax) {
    if (type.equals(PsiType.INT)) {
      return isMax ? Integer.MIN_VALUE : Integer.MAX_VALUE;
    }
    else if (type.equals(PsiType.LONG)) {
      return isMax ? Long.MIN_VALUE : Long.MAX_VALUE;
    }
    return null;
  }

  /**
   * @param nonFinalVariables list of non final variables used in terminal block. If null checks are omitted (intended to be null when
   *                          quick fix applied), empty array means that no non final variables are present in block and have different semantics
   */
  @Nullable
  static ExtremumTerminal extract(@NotNull TerminalBlock terminalBlock, @Nullable List<PsiVariable> nonFinalVariables) {
    PsiStatement[] statements = terminalBlock.getStatements();
    StreamApiMigrationInspection.FilterOp filterOp = terminalBlock.getLastOperation(StreamApiMigrationInspection.FilterOp.class);
    if (filterOp != null) {
      TerminalBlock block = terminalBlock.withoutLastOperation();
      if (block != null) {
        boolean negated = filterOp.isNegated();
        PsiExpression condition = filterOp.getExpression();
        ExtremumTerminal simpleRefCase = extractRefCase(condition, statements, block,
                                                        (nullCheckExpr, comparisonExpr, statements1, terminalBlock1, nonFinalVars, isNegated) -> SimpleRefExtremumTerminal
                                                          .extract(nullCheckExpr, comparisonExpr, statements1, terminalBlock1,
                                                                   nonFinalVars),
                                                        nonFinalVariables, negated);
        if (simpleRefCase != null) return simpleRefCase;
        ExtremumTerminal primitiveCase = PrimitiveExtremumTerminal.extract(condition, statements, block, nonFinalVariables, negated);
        if (primitiveCase != null) return primitiveCase;
        ExtremumTerminal complexRefCase = extractRefCase(condition, statements, block, ComplexExtremumTerminal::extract, nonFinalVariables,
                                                         negated);
        if (complexRefCase != null) return complexRefCase;
      }
    }
    return extractIfElseCase(statements, terminalBlock, nonFinalVariables);
  }

  //if(maxPerson == null) {
  //  maxPerson = current;
  //} else if (maxPerson.getAge() < person.getAge()) {
  //  maxPerson = current;
  //}
  @Nullable
  static private ExtremumTerminal extractIfElseCase(@NotNull PsiStatement[] statements,
                                                    @NotNull TerminalBlock terminalBlock,
                                                    @Nullable List<PsiVariable> nonFinalVariables) {
    if (statements.length != 1) return null;
    PsiStatement statement = statements[0];
    PsiIfStatement ifStatement = tryCast(statement, PsiIfStatement.class);
    if (ifStatement == null) return null;
    PsiExpression thenCondition = ifStatement.getCondition();
    if (thenCondition == null) return null;
    PsiStatement thenBranch = ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
    PsiStatement elseBranch = ControlFlowUtils.stripBraces(ifStatement.getElseBranch());
    if (thenBranch == null || elseBranch == null) return null;
    return extractIfElseCase(thenBranch, thenCondition, elseBranch, terminalBlock, nonFinalVariables);
  }

  @Nullable
  static private ExtremumTerminal extractIfElseCase(@NotNull PsiStatement thenBranch,
                                                    @NotNull PsiExpression thenCondition,
                                                    @NotNull PsiStatement elseBranch,
                                                    @NotNull TerminalBlock terminalBlock,
                                                    @Nullable List<PsiVariable> nonFinalVariables) {
    PsiIfStatement elseIfStatement = tryCast(elseBranch, PsiIfStatement.class);
    if (elseIfStatement == null || elseIfStatement.getElseBranch() != null) return null;
    PsiExpression elseIfCondition = elseIfStatement.getCondition();
    PsiStatement elseIf = ControlFlowUtils.stripBraces(elseIfStatement.getThenBranch());
    if (elseIf == null || elseIfCondition == null) return null;
    ExtremumTerminal firstWay =
      extractIfElseCase(thenBranch, thenCondition, elseIf, elseIfCondition, terminalBlock, nonFinalVariables);
    if (firstWay != null) return firstWay;
    return extractIfElseCase(elseIf, elseIfCondition, thenBranch, thenCondition, terminalBlock, nonFinalVariables);
  }

  @Nullable
  static private ExtremumTerminal extractIfElseCase(@NotNull PsiStatement nullCheckBranch,
                                                    @NotNull PsiExpression nullCheckExpr,
                                                    @NotNull PsiStatement comparisonBranch,
                                                    @NotNull PsiExpression comparisonExpr,
                                                    @NotNull TerminalBlock terminalBlock,
                                                    @Nullable List<PsiVariable> nonFinalVariables) {
    if (!ourEquivalence.statementsAreEquivalent(nullCheckBranch, comparisonBranch)) return null;
    return SimpleRefExtremumTerminal
      .extract(nullCheckExpr, comparisonExpr, new PsiStatement[]{comparisonBranch}, terminalBlock, nonFinalVariables);
  }

  @Nullable
  static private ExtremumTerminal extractRefCase(@NotNull PsiExpression condition,
                                                 @NotNull PsiStatement[] statements,
                                                 @NotNull TerminalBlock terminalBlock,
                                                 @NotNull Extractor extractor,
                                                 @Nullable List<PsiVariable> nonFinalVariables,
                                                 boolean isNegated) {
    PsiBinaryExpression binaryExpression = tryCast(condition, PsiBinaryExpression.class);
    if (binaryExpression == null) return null;
    IElementType sign = binaryExpression.getOperationTokenType();
    PsiExpression lOperand = binaryExpression.getLOperand();
    PsiExpression rOperand = binaryExpression.getROperand();
    if (!sign.equals(JavaTokenType.OROR) || rOperand == null) return null;
    return extractor.extractOriented(lOperand, rOperand, statements, terminalBlock, nonFinalVariables, isNegated);
  }


  static private boolean containsAnyVariable(@NotNull PsiExpression expression, @NotNull List<PsiVariable> variables) {
    for (PsiVariable variable : variables) {
      if (VariableAccessUtils.variableIsUsed(variable, expression)) return true;
    }
    return false;
  }

  static private boolean mayChangeBeforeLoop(@NotNull PsiVariable variable,
                                             @NotNull TerminalBlock terminalBlock) {
    ControlFlowUtils.InitializerUsageStatus status =
      ControlFlowUtils.getInitializerUsageStatus(variable, terminalBlock.getMainLoop());
    return status.equals(ControlFlowUtils.InitializerUsageStatus.UNKNOWN);
  }

  @Nullable
  static PsiVariable resolveVariableReference(@Nullable PsiExpression expression) {
    PsiExpression nakedExpression = skipParenthesizedExprDown(expression);
    PsiReferenceExpression referenceExpression = tryCast(nakedExpression, PsiReferenceExpression.class);
    if (referenceExpression == null) return null;
    PsiLocalVariable localVariable = tryCast(referenceExpression.resolve(), PsiLocalVariable.class);
    if (localVariable != null) return localVariable;
    return tryCast(referenceExpression.resolve(), PsiParameter.class);
  }

  private static boolean equalShape(@NotNull PsiExpression first,
                                    @NotNull PsiExpression second,
                                    @NotNull String firstExprVarName,
                                    @NotNull PsiVariable secondExprVariable) {
    PsiExpression secondCopy = (PsiExpression)second.copy();
    for (PsiReference ref : ReferencesSearch.search(secondExprVariable, new LocalSearchScope(secondCopy))) {
      if (ref instanceof PsiReferenceExpression) ExpressionUtils.bindReferenceTo((PsiReferenceExpression)ref, firstExprVarName);
    }
    return ourEquivalence.expressionsAreEquivalent(first, secondCopy);
  }

  private static boolean hasKnownComparableType(@NotNull Comparison comparison, @Nullable PsiType type) {
    if (!comparison.isExternalComparison()) {
      if (type == null || !(type.equals(PsiType.INT) ||
                            type.equals(PsiType.LONG) ||
                            type.equals(PsiType.DOUBLE) ||
                            InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_COMPARABLE))) {
        return false;
      }
    }
    return true;
  }

  interface ExtremumTerminal {
    @Nullable
    PsiElement replace();

    boolean isMax();
  }

  @FunctionalInterface
  interface Extractor {
    @Nullable
    ExtremumTerminal extractOriented(@NotNull PsiExpression nullCheckExpr,
                                     @NotNull PsiExpression comparisonExpr,
                                     @NotNull PsiStatement[] statements,
                                     @NotNull TerminalBlock terminalBlock,
                                     @Nullable List<PsiVariable> nonFinalVars,
                                     boolean isNegated);
  }

  //Person maxPerson = null;
  //int maxAge = 0;
  //for (Person person : personList) {
  //  if (maxPerson == null || maxAge < person.getAge()) {
  //    maxPerson = person;
  //    maxAge = person.getAge();
  //  }
  //}
  private static class ComplexExtremumTerminal implements ExtremumTerminal {
    private final boolean myMax;
    private final @NotNull PsiVariable myExtremum;
    private final @NotNull PsiVariable myExtremumKey;
    private final @NotNull PsiExpression myExtremumKeyInitializer;
    private final @NotNull PsiExpression myExtremumKeyExpr;
    private final @NotNull TerminalBlock myTerminalBlock;

    private ComplexExtremumTerminal(boolean max,
                                    @NotNull PsiVariable extremum,
                                    @NotNull PsiVariable extremumKey,
                                    @NotNull PsiExpression extremumKeyInitializer,
                                    @NotNull PsiExpression extremumKeyExpr,
                                    @NotNull TerminalBlock block) {
      myMax = max;
      myExtremum = extremum;
      myExtremumKey = extremumKey;
      myExtremumKeyInitializer = extremumKeyInitializer;
      myExtremumKeyExpr = extremumKeyExpr;
      myTerminalBlock = block;
    }


    @Nullable
    @Override
    public PsiElement replace() {
      PsiType loopVarExpressionType = myExtremumKeyExpr.getType();
      if (loopVarExpressionType == null) return null;
      String method = getComparingMethod(loopVarExpressionType);
      if (method == null) return null;

      String inFilterOperation = myMax ? ">=" : "<=";
      PsiLoopStatement loop = myTerminalBlock.getMainLoop();
      PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(loop.getProject());
      String extremumInitializer = myExtremumKeyInitializer.getText();
      String name = myTerminalBlock.getVariable().getName();
      if (name == null) return null;
      PsiExpression condition =
        elementFactory.createExpressionFromText(myExtremumKeyExpr.getText() + inFilterOperation + extremumInitializer, loop);
      TerminalBlock blockWithFilter =
        myTerminalBlock.add(new StreamApiMigrationInspection.FilterOp(condition, myTerminalBlock.getVariable(), false));

      String lambdaText = LambdaUtil.createLambda(myTerminalBlock.getVariable(), myExtremumKeyExpr);
      String comparator = CommonClassNames.JAVA_UTIL_COMPARATOR + "." + method + "(" + lambdaText + ")";
      String stream = blockWithFilter.generate() + "." + getOperation(myMax) + "(" + comparator + ").orElse(null)";
      return replaceWithFindExtremum(myTerminalBlock.getMainLoop(), myExtremum, stream, myExtremumKey);
    }

    @Override
    public boolean isMax() {
      return myMax;
    }

    @Nullable
    private static ComplexExtremumTerminal extract(@NotNull PsiExpression nullCheckExpr,
                                                   @NotNull PsiExpression comparisonExpr,
                                                   @NotNull PsiStatement[] statements,
                                                   @NotNull TerminalBlock terminalBlock,
                                                   @Nullable List<PsiVariable> nonFinalVariables,
                                                   boolean isNegated) {
      Comparison comparison = Comparison.extract(comparisonExpr, terminalBlock.getVariable(), isNegated);
      if (comparison == null) return null;

      PsiBinaryExpression nullCheckBinary = tryCast(skipParenthesizedExprDown(nullCheckExpr), PsiBinaryExpression.class);
      if (nullCheckBinary == null) return null;
      PsiVariable extremumNullChecked = ExpressionUtils.getVariableFromNullComparison(nullCheckBinary, true);

      if (extremumNullChecked == null) return null;
      ComplexAssignment assignment = ComplexAssignment.extract(statements, extremumNullChecked);
      if (assignment == null) return null;
      if (!assignment.getLoopVar().equals(terminalBlock.getVariable())) return null;
      PsiVariable extremumKey = resolveVariableReference(comparison.getExtremumExpr());
      if (extremumKey == null) return null;
      if (!extremumKey.equals(assignment.getExtremumKey())) return null;
      PsiVariable extremum = assignment.getExtremum();
      if (mayChangeBeforeLoop(extremumKey, terminalBlock) ||
          mayChangeBeforeLoop(extremum, terminalBlock)) {
        return null;
      }

      PsiExpression loopVarExpr = comparison.getLoopVarExpr();
      if (!ourEquivalence.expressionsAreEquivalent(assignment.getLoopVarExpression(), loopVarExpr)) return null;
      if (nonFinalVariables != null) {
        if (containsAnyVariable(assignment.getLoopVarExpression(), nonFinalVariables)) return null;
      }
      PsiType loopVarExprType = loopVarExpr.getType();
      if (!hasKnownComparableType(comparison, loopVarExprType)) return null;

      PsiExpression extremumInitializer = extremum.getInitializer();
      PsiExpression extremumKeyInitializer = extremumKey.getInitializer();
      if (extremumInitializer == null || extremumKeyInitializer == null) return null;
      if (!ExpressionUtils.isNullLiteral(extremumInitializer)) return null;
      if (!ExpressionUtils.isEvaluatedAtCompileTime(extremumKeyInitializer)) return null;
      return new ComplexExtremumTerminal(comparison.isMax(), extremum, extremumKey, extremumKeyInitializer, loopVarExpr, terminalBlock
      );
    }

    private static class ComplexAssignment {
      private @NotNull final PsiVariable myExtremum;
      private @NotNull final PsiVariable myExtremumKey;
      private @NotNull final PsiVariable myLoopVar; // extremum = loopVar;
      private @NotNull final PsiExpression myLoopVarExpression;

      private ComplexAssignment(@NotNull PsiVariable extremum,
                                @NotNull PsiVariable extremumKey,
                                @NotNull PsiVariable loopVar,
                                @NotNull PsiExpression loopVarExpression) {
        myExtremum = extremum;
        myExtremumKey = extremumKey;
        myLoopVar = loopVar;
        myLoopVarExpression = loopVarExpression;
      }

      @NotNull
      public PsiVariable getExtremum() {
        return myExtremum;
      }

      @NotNull
      public PsiVariable getExtremumKey() {
        return myExtremumKey;
      }

      @NotNull
      public PsiVariable getLoopVar() {
        return myLoopVar;
      }

      @NotNull
      public PsiExpression getLoopVarExpression() {
        return myLoopVarExpression;
      }

      /**
       * Intended to recognize:
       * extremum = loopVar; // max = current;
       * extremumKey = loopVarExpr;  // maxAge = current.getAge();
       *
       * @param statements          statements that contains only complex assignment
       * @param nullCheckedExtremum extremum that will be used to recognize extremum assignment
       */
      @Nullable
      private static ComplexAssignment extract(@NotNull PsiStatement[] statements, @NotNull PsiVariable nullCheckedExtremum) {
        if (statements.length != 2) return null;
        PsiStatement first = statements[0];
        PsiStatement second = statements[1];
        ComplexAssignment assignment = extract(first, second, nullCheckedExtremum);
        if (assignment != null) return assignment;
        return extract(second, first, nullCheckedExtremum);
      }

      @Nullable
      private static ComplexAssignment extract(@NotNull PsiStatement first,
                                               @NotNull PsiStatement second,
                                               @NotNull PsiVariable nullCheckedExtremum) {
        PsiAssignmentExpression secondAssignment = ExpressionUtils.getAssignment(second);
        if (secondAssignment == null) return null;
        PsiVariable loopVar = resolveVariableReference(ExpressionUtils.getAssignmentTo(first, nullCheckedExtremum));
        PsiVariable extremumKey = resolveVariableReference(secondAssignment.getLExpression());
        PsiExpression loopVarExpr = secondAssignment.getRExpression();
        if (extremumKey == null || loopVar == null || loopVarExpr == null) return null;
        return new ComplexAssignment(nullCheckedExtremum, extremumKey, loopVar, loopVarExpr);
      }
    }
  }

  //if(max < anInt) {
  //max = anInt;
  //}
  private static class PrimitiveExtremumTerminal implements ExtremumTerminal {
    private final boolean myMax;
    private final @NotNull TerminalBlock myTerminalBlock;
    private final @NotNull PsiExpression myLoopVarExpression;
    private final @NotNull PsiVariable myExtremum;
    private final @NotNull PsiExpression myExtremumInitializer;

    private PrimitiveExtremumTerminal(boolean max,
                                      @NotNull TerminalBlock block,
                                      @NotNull PsiExpression loopVarExpression,
                                      @NotNull PsiVariable extremum,
                                      @NotNull PsiExpression extremumInitializer) {
      myMax = max;
      myTerminalBlock = block;
      myLoopVarExpression = loopVarExpression;
      myExtremum = extremum;
      myExtremumInitializer = extremumInitializer;
    }


    @Nullable
    @Override
    public PsiElement replace() {
      String name = myTerminalBlock.getVariable().getName();
      if (name == null) return null;
      PsiType type = myExtremumInitializer.getType();
      if (type == null) return null;
      Object initializerValue = ExpressionUtils.computeConstantExpression(myExtremumInitializer);
      if (initializerValue == null) return null;

      TerminalBlock blockWithMap = myTerminalBlock
        .add(new StreamApiMigrationInspection.MapOp(myLoopVarExpression, myTerminalBlock.getVariable(), myLoopVarExpression.getType()));

      String inFilterOperation = myMax ? ">=" : "<=";
      PsiLoopStatement loop = blockWithMap.getMainLoop();
      PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(loop.getProject());
      String extremumInitializer = myExtremumInitializer.getText();
      Object nonFilterableInitialValue = getNonFilterableInitialValue(type, myMax);
      final TerminalBlock terminalBlock;
      if (nonFilterableInitialValue != null && !nonFilterableInitialValue.equals(initializerValue)) {
        PsiExpression condition =
          elementFactory.createExpressionFromText(name + inFilterOperation + extremumInitializer, loop);

        terminalBlock = blockWithMap.add(new StreamApiMigrationInspection.FilterOp(condition, myTerminalBlock.getVariable(), false));
      }
      else {
        terminalBlock = blockWithMap;
      }


      String stream = terminalBlock.generate() + "." + getOperation(myMax) + "().orElse(" + extremumInitializer + ")";
      return replaceWithFindExtremum(loop, myExtremum, stream, null);
    }


    @Override
    public boolean isMax() {
      return myMax;
    }


    @Nullable
    private static PrimitiveExtremumTerminal extract(@NotNull PsiExpression condition,
                                                     @NotNull PsiStatement[] statements,
                                                     @NotNull TerminalBlock terminalBlock,
                                                     @Nullable List<PsiVariable> nonFinalVariables,
                                                     boolean isNegated) {
      Comparison comparison = Comparison.extract(condition, terminalBlock.getVariable(), isNegated);
      if (comparison == null) return null;
      if (statements.length != 1) return null;
      PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(statements[0]);
      if (assignment == null) return null;
      PsiVariable extremum = resolveVariableReference(assignment.getLExpression());
      if (extremum == null) return null;
      PsiExpression assignmentLoopVarExpr = assignment.getRExpression();
      PsiExpression comparisonLoopVarExpr = comparison.getLoopVarExpr();
      if (!ourEquivalence.expressionsAreEquivalent(assignmentLoopVarExpr, comparisonLoopVarExpr)) return null;
      PsiVariable comparisonExtremum = resolveVariableReference(comparison.getExtremumExpr());
      if (comparisonExtremum == null) return null;
      if (!extremum.equals(comparisonExtremum)) return null;
      if (mayChangeBeforeLoop(extremum, terminalBlock)) return null;
      if (nonFinalVariables != null) {
        if (containsAnyVariable(comparisonLoopVarExpr, nonFinalVariables)) return null;
      }
      PsiType loopVarExprType = comparisonLoopVarExpr.getType();
      if (!hasKnownComparableType(comparison, loopVarExprType)) return null;

      PsiExpression extremumInitializer = extremum.getInitializer();
      if (!ExpressionUtils.isEvaluatedAtCompileTime(extremumInitializer)) return null;
      return new PrimitiveExtremumTerminal(comparison.isMax(), terminalBlock, comparisonLoopVarExpr, extremum, extremumInitializer
      );
    }
  }

  private static class SimpleRefExtremumTerminal implements ExtremumTerminal {
    private final boolean myMax;
    private final @NotNull TerminalBlock myTerminalBlock;
    private final @NotNull PsiExpression myLoopVarExpression;
    private final @NotNull PsiVariable myExtremum;
    private final @Nullable PsiVariable myComparator;

    private SimpleRefExtremumTerminal(boolean max,
                                      @NotNull TerminalBlock block,
                                      @NotNull PsiExpression loopVarExpression,
                                      @NotNull PsiVariable extremum,
                                      @Nullable PsiVariable comparator) {
      myMax = max;
      myTerminalBlock = block;
      myLoopVarExpression = loopVarExpression;
      myExtremum = extremum;
      myComparator = comparator;
    }

    @Nullable
    @Override
    public PsiElement replace() {
      PsiType loopVarExpressionType = myLoopVarExpression.getType();
      if (loopVarExpressionType == null) return null;
      final String comparator;
      if(myComparator == null) {
        String method = getComparingMethod(loopVarExpressionType);
        if (method == null) return null;
        String lambdaText = LambdaUtil.createLambda(myTerminalBlock.getVariable(), myLoopVarExpression);
        comparator = CommonClassNames.JAVA_UTIL_COMPARATOR + "." + method + "(" + lambdaText + ")";
      } else {
        String comparatorName = myComparator.getName();
        if(comparatorName == null) return null;
        comparator = comparatorName;
      }
      String stream = myTerminalBlock.generate() + "." + getOperation(myMax) + "(" + comparator + ").orElse(null)";
      return replaceWithFindExtremum(myTerminalBlock.getMainLoop(), myExtremum, stream, null);
    }

    @Override
    public boolean isMax() {
      return myMax;
    }


    @Nullable
    private static SimpleRefExtremumTerminal extract(@NotNull PsiExpression nullCheckExpr,
                                                     @NotNull PsiExpression comparisonExpr,
                                                     @NotNull PsiStatement[] statements,
                                                     @NotNull TerminalBlock terminalBlock,
                                                     @Nullable List<PsiVariable> nonFinalVars) {
      PsiBinaryExpression nullCheckBinary = tryCast(nullCheckExpr, PsiBinaryExpression.class);
      if (nullCheckBinary == null) return null;
      PsiVariable nullCheckingVar = ExpressionUtils.getVariableFromNullComparison(nullCheckBinary, true);
      if (nullCheckingVar == null) return null;
      Comparison comparison = Comparison.extract(comparisonExpr, terminalBlock.getVariable(), false);
      if (comparison == null) return null;
      if (statements.length != 1) return null;
      PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(statements[0]);
      if (assignment == null) return null;
      PsiVariable extremum = resolveVariableReference(assignment.getLExpression());
      PsiVariable loopVariable = resolveVariableReference(assignment.getRExpression());
      if (extremum == null || loopVariable == null) return null;
      if (nullCheckingVar != extremum) return null;
      if (!loopVariable.equals(terminalBlock.getVariable())) return null;

      PsiExpression extremumExpr = comparison.getExtremumExpr();
      PsiExpression loopVarExpr = comparison.getLoopVarExpr();
      String name = extremum.getName();
      if (name == null) return null;
      if (!equalShape(extremumExpr, loopVarExpr, name, terminalBlock.getVariable())) return null;
      if (mayChangeBeforeLoop(extremum, terminalBlock)) return null;
      if (nonFinalVars != null) {
        if (nonFinalVars.size() != 1) return null;
        if (!nonFinalVars.get(0).equals(extremum)) return null;
        if (containsAnyVariable(loopVarExpr, nonFinalVars)) return null;
      }
      PsiType loopVarExprType = loopVarExpr.getType();
      if (!hasKnownComparableType(comparison, loopVarExprType)) return null;

      boolean max = comparison.isMax();

      PsiExpression initializer = extremum.getInitializer();
      if (!ExpressionUtils.isNullLiteral(initializer)) return null;
      return new SimpleRefExtremumTerminal(max, terminalBlock, loopVarExpr, extremum, comparison.getComparator());
    }
  }

  private static class Comparison {
    private final @NotNull PsiExpression myExtremumExpr;
    private final @NotNull PsiExpression myLoopVarExpr;
    private final boolean myIsMax;
    private final @Nullable PsiVariable myComparator;
    private final boolean myExternalComparison;

    private Comparison(@NotNull PsiExpression extremumExpr,
                       @NotNull PsiExpression loopVarExpr,
                       boolean max,
                       @Nullable PsiVariable comparator,
                       boolean externalComparison) {
      myExtremumExpr = extremumExpr;
      myLoopVarExpr = loopVarExpr;
      myIsMax = max;
      myComparator = comparator;
      myExternalComparison = externalComparison;
    }

    @NotNull
    public PsiExpression getLoopVarExpr() {
      return myLoopVarExpr;
    }

    @NotNull
    public PsiExpression getExtremumExpr() {
      return myExtremumExpr;
    }

    public boolean isMax() {
      return myIsMax;
    }

    @Nullable
    public PsiVariable getComparator() {
      return myComparator;
    }

    public boolean isExternalComparison() {
      return myExternalComparison;
    }


    @Nullable
    static Comparison extract(@NotNull PsiExpression expression, @NotNull PsiVariable loopVariable, boolean isNegated) {
      PsiBinaryExpression binaryExpression = tryCast(skipParenthesizedExprDown(expression), PsiBinaryExpression.class);
      if (binaryExpression == null) return null;
      IElementType sign = binaryExpression.getOperationSign().getTokenType();
      PsiExpression rOperand = binaryExpression.getROperand();
      if (rOperand == null) return null;
      PsiExpression lOperand = binaryExpression.getLOperand();
      if (sign.equals(JavaTokenType.LT) || sign.equals(JavaTokenType.LE)) {
        Comparison extract = extractComparatorLikeComparison(lOperand, rOperand, loopVariable, isNegated);
        if (extract != null) return extract;
        return extract(lOperand, rOperand, loopVariable, isNegated, null, false);
      }
      else if (sign.equals(JavaTokenType.GT) || sign.equals(JavaTokenType.GE)) {
        Comparison extract = extractComparatorLikeComparison(lOperand, rOperand, loopVariable, !isNegated);
        if (extract != null) return extract;
        return extract(lOperand, rOperand, loopVariable, !isNegated, null, false);
      }
      return null;
    }

    @Nullable
    private static Comparison extractComparatorLikeComparison(@NotNull PsiExpression expression,
                                                              @NotNull PsiVariable loopVariable,
                                                              boolean isGreater) {

      PsiMethodCallExpression methodExpression = tryCast(expression, PsiMethodCallExpression.class);
      if (methodExpression == null) return null;
      PsiExpression qualifierExpression = methodExpression.getMethodExpression().getQualifierExpression();
      if (qualifierExpression == null) return null;
      PsiMethod method = methodExpression.resolveMethod();
      if (method == null) return null;
      String methodName = method.getName();
      PsiType qualifierType = qualifierExpression.getType();
      PsiExpressionList argumentList = methodExpression.getArgumentList();
      PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 1) {
        PsiExpression argument = arguments[0];

        if (!methodName.equals("compareTo") || !InheritanceUtil.isInheritor(qualifierType, CommonClassNames.JAVA_LANG_COMPARABLE)) {
          return null;
        }
        return extract(argument, qualifierExpression, loopVariable, isGreater, null, false);
      }
      else if (arguments.length == 2) {
        PsiExpression firstArgument = arguments[0];
        PsiExpression secondArgument = arguments[1];
        if (!methodName.equals("compare") || !InheritanceUtil.isInheritor(qualifierType, CommonClassNames.JAVA_UTIL_COMPARATOR)) {
          return null;
        }
        PsiVariable comparator = resolveVariableReference(qualifierExpression);
        return extract(secondArgument, firstArgument, loopVariable, isGreater, comparator, true);
      }
      return null;
    }

    @Nullable
    private static Comparison extractComparatorLikeComparison(@NotNull PsiExpression lOperand,
                                                              @NotNull PsiExpression rOperand,
                                                              @NotNull PsiVariable loopVariable,
                                                              boolean isGreater) {
      if (ExpressionUtils.isZero(lOperand)) {
        return extractComparatorLikeComparison(rOperand, loopVariable, isGreater);
      }
      else if (ExpressionUtils.isZero(rOperand)) {
        return extractComparatorLikeComparison(lOperand, loopVariable, !isGreater);
      }
      return null;
    }


    @Nullable
    private static Comparison extract(@NotNull PsiExpression lOperand,
                                      @NotNull PsiExpression rOperand,
                                      @NotNull PsiVariable loopVariable,
                                      boolean isGreater,
                                      @Nullable PsiVariable comparator,
                                      boolean externalComparison) {
      final boolean max;
      final PsiExpression extremumExpr;
      final PsiExpression loopVarExpr;
      if (ReferencesSearch.search(loopVariable, new LocalSearchScope(lOperand)).findFirst() != null) {
        max = isGreater;
        loopVarExpr = lOperand;
        extremumExpr = rOperand;
      }
      else if (ReferencesSearch.search(loopVariable, new LocalSearchScope(rOperand)).findFirst() != null) {
        max = !isGreater;
        loopVarExpr = rOperand;
        extremumExpr = lOperand;
      }
      else {
        return null;
      }
      return new Comparison(extremumExpr, loopVarExpr, max, comparator, externalComparison);
    }
  }
}