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
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.util.ObjectUtils.tryCast;

public class FindExtremumMigration extends BaseStreamApiMigration {

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
  static String getOpertion(boolean isMax) {
    return isMax ? MAX_OP : MIN_OP;
  }

  @Nullable
  static private String getComparingMethod(@NotNull PsiType type) {
    if (type.equals(PsiType.INT)) return "comparingInt";
    if (type.equals(PsiType.DOUBLE)) return "comparingDouble";
    if (type.equals(PsiType.LONG)) return "comparingLong";
    return null;
  }

  @Nullable
  static ExtremumTerminal extract(@NotNull TerminalBlock terminalBlock, @Nullable List<PsiVariable> nonFinalVariables) {
    PsiStatement[] statements = terminalBlock.getStatements();
    StreamApiMigrationInspection.Operation operation = terminalBlock.getLastOperation();
    StreamApiMigrationInspection.FilterOp filterOp = tryCast(operation, StreamApiMigrationInspection.FilterOp.class);
    if (filterOp != null) {
      TerminalBlock block = terminalBlock.withoutLastOperation();
      if (block != null) {
        PsiExpression condition = filterOp.getExpression();
        ExtremumTerminal simpleRefCase = extractSimpleRefCase(condition, statements, block, nonFinalVariables);
        if (simpleRefCase != null) return simpleRefCase;
        ExtremumTerminal primitiveCase = PrimitiveExtremumTerminal.extract(condition, statements, block, nonFinalVariables);
        if (primitiveCase != null) return primitiveCase;
        ExtremumTerminal complexRefCase = extractComplexRefCase(condition, statements, block, nonFinalVariables);
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
                                                    @Nullable List<PsiVariable> nonNullVariables) {
    if (statements.length != 1) return null;
    PsiStatement statement = statements[0];
    PsiIfStatement ifStatement = tryCast(statement, PsiIfStatement.class);
    if (ifStatement == null) return null;
    PsiExpression thenCondition = ifStatement.getCondition();
    if (thenCondition == null) return null;
    PsiStatement thenBranch = unwrapIfBranchToStatement(ifStatement.getThenBranch());
    PsiStatement elseBranch = unwrapIfBranchToStatement(ifStatement.getElseBranch());
    if (thenBranch == null || elseBranch == null) return null;
    return extractIfElseCase(thenBranch, thenCondition, elseBranch, terminalBlock, nonNullVariables);
  }

  @Nullable
  static private ExtremumTerminal extractIfElseCase(@NotNull PsiStatement thenBranch,
                                                    @NotNull PsiExpression thenCondition,
                                                    @NotNull PsiStatement elseBranch,
                                                    @NotNull TerminalBlock terminalBlock,
                                                    @Nullable List<PsiVariable> nonNullVariables) {
    PsiIfStatement elseIfStatement = tryCast(elseBranch, PsiIfStatement.class);
    if (elseIfStatement == null || elseIfStatement.getElseBranch() != null) return null;
    PsiExpression elseIfCondition = elseIfStatement.getCondition();
    PsiStatement elseIf = unwrapIfBranchToStatement(elseIfStatement.getThenBranch());
    if (elseIf == null || elseIfCondition == null) return null;
    ExtremumTerminal firstWay = extractIfElseCase(thenBranch, thenCondition, elseIf, elseIfCondition, terminalBlock, nonNullVariables);
    if (firstWay != null) return firstWay;
    return extractIfElseCase(elseIf, elseIfCondition, thenBranch, thenCondition, terminalBlock, nonNullVariables);
  }

  @Nullable
  static private ExtremumTerminal extractIfElseCase(@NotNull PsiStatement nullCheckBranch,
                                                    @NotNull PsiExpression nullCheckExpr,
                                                    @NotNull PsiStatement comparisionBranch,
                                                    @NotNull PsiExpression comparisionExpr,
                                                    @NotNull TerminalBlock terminalBlock,
                                                    @Nullable List<PsiVariable> nonNullVariables) {
    if (!ourEquivalence.statementsAreEquivalent(nullCheckBranch, comparisionBranch)) return null;
    return SimpleRefExtremumTerminal
      .extract(nullCheckExpr, comparisionExpr, new PsiStatement[]{comparisionBranch}, terminalBlock, nonNullVariables);
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

  @Nullable
  static private ExtremumTerminal extractComplexRefCase(@NotNull PsiExpression condition,
                                                        @NotNull PsiStatement[] statements,
                                                        @NotNull TerminalBlock terminalBlock,
                                                        @Nullable List<PsiVariable> nonFinalVariables) {
    return extractRefCase(condition, statements, terminalBlock, FindExtremumMigration.ComplexExtremumTerminal::extract, nonFinalVariables);
  }

  @Nullable
  static private ExtremumTerminal extractSimpleRefCase(@NotNull PsiExpression condition,
                                                       @NotNull PsiStatement[] statements,
                                                       @NotNull TerminalBlock terminalBlock,
                                                       @Nullable List<PsiVariable> nonFinalVariables) {
    return extractRefCase(condition, statements, terminalBlock, FindExtremumMigration.SimpleRefExtremumTerminal::extract,
                          nonFinalVariables);
  }

  @Nullable
  static private ExtremumTerminal extractRefCase(@NotNull PsiExpression condition,
                                                 @NotNull PsiStatement[] statements,
                                                 @NotNull TerminalBlock terminalBlock,
                                                 @NotNull Extractor extractor,
                                                 @Nullable List<PsiVariable> nonFinalVariables) {
    PsiBinaryExpression binaryExpression = tryCast(condition, PsiBinaryExpression.class);
    if (binaryExpression == null) return null;
    IElementType sign = binaryExpression.getOperationSign().getTokenType();
    PsiExpression lOperand = binaryExpression.getLOperand();
    PsiExpression rOperand = binaryExpression.getROperand();
    if (!sign.equals(JavaTokenType.OROR) || rOperand == null) return null;
    ExtremumTerminal firstWay = extractor.extractOriented(lOperand, rOperand, statements, terminalBlock, nonFinalVariables);
    if (firstWay != null) return firstWay;
    return extractor.extractOriented(rOperand, lOperand, statements, terminalBlock, nonFinalVariables);
  }


  static private boolean containsAnyVariable(@NotNull PsiExpression expression, @NotNull List<PsiVariable> variables) {
    for (PsiVariable variable : variables) {
      if (ReferencesSearch.search(variable, new LocalSearchScope(expression)).findFirst() != null) return true;
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
  static PsiVariable getVariable(@Nullable PsiExpression expression) {
    PsiReferenceExpression referenceExpression = tryCast(expression, PsiReferenceExpression.class);
    if (referenceExpression == null) return null;
    return tryCast(referenceExpression.resolve(), PsiVariable.class);
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

  interface ExtremumTerminal {
    @Nullable
    PsiElement replace();

    boolean isMax();
  }

  @FunctionalInterface
  interface Extractor {
    @Nullable
    ExtremumTerminal extractOriented(@NotNull PsiExpression nullCheckExpr,
                                     @NotNull PsiExpression comparisionExpr,
                                     @NotNull PsiStatement[] statements,
                                     @NotNull TerminalBlock terminalBlock,
                                     @Nullable List<PsiVariable> nonFinalVars);
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

      PsiType loopVarExpressionType = myExtremumKeyExpr.getType();
      if (loopVarExpressionType == null) return null;
      String method = getComparingMethod(loopVarExpressionType);
      if (method == null) return null;
      String lambdaText = name + "->" + myExtremumKeyExpr.getText();
      String comparator = CommonClassNames.JAVA_UTIL_COMPARATOR + "." + method + "(" + lambdaText + ")";
      String stream = blockWithFilter.generate() + "." + getOpertion(myMax) + "(" + comparator + ").orElse(null)";
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
                                                   @Nullable List<PsiVariable> nonFinalVariables) {
      Comparison comparison = Comparison.extract(comparisonExpr, terminalBlock.getVariable());
      if (comparison == null) return null;

      PsiBinaryExpression nullCheckBinary = tryCast(nullCheckExpr, PsiBinaryExpression.class);
      if (nullCheckBinary == null) return null;
      PsiVariable extremumNullChecked = ExpressionUtils.getVariableFromNullComparison(nullCheckBinary, true);

      if (extremumNullChecked == null) return null;
      ComplexAssignment assignment = ComplexAssignment.extract(statements, extremumNullChecked);
      if (assignment == null) return null;
      if (!assignment.getLoopVar().equals(terminalBlock.getVariable())) return null;
      PsiVariable extremumKey = getVariable(comparison.getExtremumExpr());
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

      PsiExpression extremumInitializer = extremum.getInitializer();
      PsiExpression extremumKeyInitializer = extremumKey.getInitializer();
      if (extremumInitializer == null || extremumKeyInitializer == null) return null;
      if (!ExpressionUtils.isNullLiteral(extremumInitializer)) return null;
      if (!ExpressionUtils.isEvaluatedAtCompileTime(extremumKeyInitializer)) return null;
      return new ComplexExtremumTerminal(comparison.isMax(), extremum, extremumKey, extremumKeyInitializer, loopVarExpr, terminalBlock);
    }

    private static class ComplexAssignment {
      private @NotNull PsiVariable myExtremum;
      private @NotNull PsiVariable myExtremumKey;
      private @NotNull PsiVariable myLoopVar; // extremum = loopVar;
      private @NotNull PsiExpression myLoopVarExpression;

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
        PsiAssignmentExpression firstAssignment = ExpressionUtils.getAssignment(first);
        PsiAssignmentExpression secondAssignment = ExpressionUtils.getAssignment(second);
        PsiVariable extremum = getVariable(firstAssignment.getLExpression());
        PsiVariable loopVar = getVariable(firstAssignment.getRExpression());
        PsiVariable extremumKey = getVariable(secondAssignment.getLExpression());
        PsiExpression loopVarExpr = secondAssignment.getRExpression();
        if (extremum == null || extremumKey == null || loopVar == null || loopVarExpr == null) return null;
        if (extremum != nullCheckedExtremum) return null;
        return new ComplexAssignment(extremum, extremumKey, loopVar, loopVarExpr);
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
      //myTerminalBlock.generate()
      TerminalBlock blockWithMap = myTerminalBlock
        .add(new StreamApiMigrationInspection.MapOp(myLoopVarExpression, myTerminalBlock.getVariable(), myLoopVarExpression.getType()));

      String inFilterOperation = myMax ? ">=" : "<=";
      PsiLoopStatement loop = blockWithMap.getMainLoop();
      PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(loop.getProject());
      String extremumInitializer = myExtremumInitializer.getText();
      // TODO not creating filter when Integer.MIN...
      String name = myTerminalBlock.getVariable().getName();
      if (name == null) return null;
      PsiExpression condition =
        elementFactory.createExpressionFromText(name + inFilterOperation + extremumInitializer, loop);

      TerminalBlock blockWithFilter =
        blockWithMap.add(new StreamApiMigrationInspection.FilterOp(condition, myTerminalBlock.getVariable(), false));
      String stream = blockWithFilter.generate() + "." + getOpertion(myMax) + "().orElse(" + extremumInitializer + ")";
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
                                                     @Nullable List<PsiVariable> nonFinalVariables) {
      Comparison comparison = Comparison.extract(condition, terminalBlock.getVariable());
      if (comparison == null) return null;
      if (statements.length != 1) return null;
      PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(statements[0]);
      if (assignment == null) return null;
      PsiVariable extremum = getVariable(assignment.getLExpression());
      if (extremum == null) return null;
      PsiExpression assignmentLoopVarExpr = assignment.getRExpression();
      PsiExpression comparisonloopVarExpr = comparison.getLoopVarExpr();
      if (!ourEquivalence.expressionsAreEquivalent(assignmentLoopVarExpr, comparisonloopVarExpr)) return null;
      PsiVariable comparisonExtremum = getVariable(comparison.getExtremumExpr());
      if (comparisonExtremum == null) return null;
      if (!extremum.equals(comparisonExtremum)) return null;
      if (mayChangeBeforeLoop(extremum, terminalBlock)) return null;
      if (nonFinalVariables != null) {
        if (containsAnyVariable(comparisonloopVarExpr, nonFinalVariables)) return null;
      }

      PsiExpression extremumInitializer = extremum.getInitializer();
      if (!ExpressionUtils.isEvaluatedAtCompileTime(extremumInitializer)) return null;
      return new PrimitiveExtremumTerminal(comparison.isMax(), terminalBlock, comparisonloopVarExpr, extremum, extremumInitializer);
    }
  }

  private static class SimpleRefExtremumTerminal implements ExtremumTerminal {
    private final boolean myMax;
    private final @NotNull TerminalBlock myTerminalBlock;
    private final @NotNull PsiExpression myLoopVarExpression;
    private final @NotNull PsiVariable myExtremum;

    private SimpleRefExtremumTerminal(boolean max,
                                      @NotNull TerminalBlock block,
                                      @NotNull PsiExpression loopVarExpression,
                                      @NotNull PsiVariable extremum) {
      myMax = max;
      myTerminalBlock = block;
      myLoopVarExpression = loopVarExpression;
      myExtremum = extremum;
    }

    @Nullable
    @Override
    public PsiElement replace() {
      PsiType loopVarExpressionType = myLoopVarExpression.getType();
      if (loopVarExpressionType == null) return null;
      String method = getComparingMethod(loopVarExpressionType);
      if (method == null) return null;
      String name = myTerminalBlock.getVariable().getName();
      if (name == null) return null;
      String lambdaText = name + "->" + myLoopVarExpression.getText();
      String comparator = CommonClassNames.JAVA_UTIL_COMPARATOR + "." + method + "(" + lambdaText + ")";
      String stream = myTerminalBlock.generate() + "." + getOpertion(myMax) + "(" + comparator + ").orElse(null)";
      return replaceWithFindExtremum(myTerminalBlock.getMainLoop(), myExtremum, stream, null);
    }

    @Override
    public boolean isMax() {
      return myMax;
    }


    @Nullable
    private static SimpleRefExtremumTerminal extract(@NotNull PsiExpression nullCheckExpr,
                                                     @NotNull PsiExpression comparisionExpr,
                                                     @NotNull PsiStatement[] statements,
                                                     @NotNull TerminalBlock terminalBlock,
                                                     @Nullable List<PsiVariable> nonFinalVars) {
      PsiBinaryExpression nullCheckBinary = tryCast(nullCheckExpr, PsiBinaryExpression.class);
      if (nullCheckBinary == null) return null;
      PsiVariable nullCheckingVar = ExpressionUtils.getVariableFromNullComparison(nullCheckBinary, true);
      if (nullCheckingVar == null) return null;
      Comparison comparison = Comparison.extract(comparisionExpr, terminalBlock.getVariable());
      if (comparison == null) return null;
      if (statements.length != 1) return null;
      PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(statements[0]);
      if (assignment == null) return null;
      PsiVariable extremum = getVariable(assignment.getLExpression());
      PsiVariable loopVariable = getVariable(assignment.getRExpression());
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

      boolean max = comparison.isMax();

      PsiExpression initializer = extremum.getInitializer();
      if (!ExpressionUtils.isNullLiteral(initializer)) return null;
      return new SimpleRefExtremumTerminal(max, terminalBlock, loopVarExpr, extremum);
    }
  }

  private static class Comparison {
    private final @NotNull PsiExpression myExtremumExpr;
    private final @NotNull PsiExpression myLoopVarExpr;
    private final boolean myIsMax;

    private Comparison(@NotNull PsiExpression extremumExpr, @NotNull PsiExpression loopVarExpr, boolean max) {
      myExtremumExpr = extremumExpr;
      myLoopVarExpr = loopVarExpr;
      myIsMax = max;
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
    static Comparison extract(@NotNull PsiExpression expression, @NotNull PsiVariable loopVariable) {
      PsiBinaryExpression binaryExpression = tryCast(expression, PsiBinaryExpression.class);
      IElementType sign = binaryExpression.getOperationSign().getTokenType();
      PsiExpression rOperand = binaryExpression.getROperand();
      if (rOperand == null) return null;
      PsiExpression lOperand = binaryExpression.getLOperand();
      if (sign.equals(JavaTokenType.LT) || sign.equals(JavaTokenType.LE)) {
        return extract(lOperand, rOperand, loopVariable, false);
      }
      else if (sign.equals(JavaTokenType.GT) || sign.equals(JavaTokenType.GE)) {
        return extract(lOperand, rOperand, loopVariable, true);
      }
      return null;
    }

    @Nullable
    private static Comparison extract(@NotNull PsiExpression lOperand,
                                      @NotNull PsiExpression rOperand,
                                      @NotNull PsiVariable loopVariable,
                                      boolean isGreater) {
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
      return new Comparison(extremumExpr, loopVarExpr, max);
    }
  }
}