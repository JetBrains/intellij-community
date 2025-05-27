// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.streamMigration;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.ControlFlow;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.*;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection.*;
import static com.intellij.util.ObjectUtils.tryCast;

/**
 * This immutable class represents the code which should be performed
 * as a part of forEach operation of resulting stream possibly with
 * some intermediate operations extracted.
 */
final class TerminalBlock {
  private static final Logger LOG = Logger.getInstance(TerminalBlock.class);

  private final @NotNull PsiVariable myVariable;
  private final Operation @NotNull [] myOperations;
  private final PsiStatement @NotNull [] myStatements;

  private TerminalBlock(Operation @NotNull [] operations, @NotNull PsiVariable variable, PsiStatement @NotNull ... statements) {
    // At least one operation is present (stream source)
    LOG.assertTrue(operations.length > 0);
    for(Operation operation : operations) Objects.requireNonNull(operation);
    for(PsiStatement statement : statements) Objects.requireNonNull(statement);
    myVariable = variable;
    while(true) {
      if(statements.length == 1 && statements[0] instanceof PsiBlockStatement) {
        statements = ((PsiBlockStatement)statements[0]).getCodeBlock().getStatements();
      } else if(statements.length == 1 && statements[0] instanceof PsiLabeledStatement) {
        PsiStatement statement = ((PsiLabeledStatement)statements[0]).getStatement();
        statements = statement == null ? PsiStatement.EMPTY_ARRAY : new PsiStatement[] {statement};
      } else break;
    }
    myStatements = statements;
    myOperations = operations;
  }

  private TerminalBlock(@Nullable TerminalBlock previousBlock,
                        @NotNull Operation operation,
                        @NotNull PsiVariable variable,
                        PsiStatement @NotNull ... statements) {
    this(previousBlock == null ? new Operation[]{operation} : ArrayUtil.append(previousBlock.myOperations, operation), variable,
         statements);
  }

  Collection<PsiStatement> findExitPoints(ControlFlow controlFlow) {
    int startOffset = controlFlow.getStartOffset(myStatements[0]);
    int endOffset = controlFlow.getEndOffset(myStatements[myStatements.length - 1]);
    if (startOffset < 0 || endOffset < 0) return null;
    return ControlFlowUtil.findExitPointsAndStatements(controlFlow, startOffset, endOffset, new IntArrayList(),
                                                       ControlFlowUtil.DEFAULT_EXIT_STATEMENTS_CLASSES);
  }

  PsiStatement getSingleStatement() {
    return myStatements.length == 1 ? myStatements[0] : null;
  }

  PsiStatement @NotNull [] getStatements() {
    return myStatements;
  }

  @Nullable
  <T extends PsiExpression> T getSingleExpression(Class<T> wantedType) {
    PsiStatement statement = getSingleStatement();
    if(statement instanceof PsiExpressionStatement) {
      PsiExpression expression = ((PsiExpressionStatement)statement).getExpression();
      return tryCast(expression, wantedType);
    }
    return null;
  }

  int getOperationCount() {
    return myOperations.length;
  }

  /**
   * @return PsiMethodCallExpression if this TerminalBlock contains single method call, null otherwise
   */
  @Nullable
  PsiMethodCallExpression getSingleMethodCall() {
    return getSingleExpression(PsiMethodCallExpression.class);
  }

  private @Nullable TerminalBlock extractFilter() {
    PsiStatement single = getSingleStatement();
    if (single instanceof PsiIfStatement ifStatement) {
      PsiExpression condition = ifStatement.getCondition();
      if(ifStatement.getElseBranch() == null && condition != null) {
        PsiStatement thenBranch = ifStatement.getThenBranch();
        if(thenBranch != null) {
          return fromCondition(condition, false, thenBranch);
        }
      }
    }
    if(myStatements.length >= 1) {
      PsiStatement first = myStatements[0];
      // extract filter with negation
      if(first instanceof PsiIfStatement ifStatement) {
        PsiExpression condition = ifStatement.getCondition();
        if(condition == null) return null;
        PsiStatement branch = ifStatement.getThenBranch();
        if(branch instanceof PsiBlockStatement) {
          PsiStatement[] statements = ((PsiBlockStatement)branch).getCodeBlock().getStatements();
          if(statements.length == 1)
            branch = statements[0];
        }
        if(!(branch instanceof PsiContinueStatement) || ((PsiContinueStatement)branch).getLabelIdentifier() != null) return null;
        PsiStatement[] statements;
        if(ifStatement.getElseBranch() != null) {
          statements = myStatements.clone();
          statements[0] = ifStatement.getElseBranch();
        } else {
          statements = Arrays.copyOfRange(myStatements, 1, myStatements.length);
        }
        return fromCondition(condition, true, statements);
      }
    }
    return null;
  }

  private @Nullable TerminalBlock fromCondition(PsiExpression condition, boolean negated, PsiStatement... statements) {
    TerminalBlock result = new TerminalBlock(this, new FilterOp(condition, myVariable, negated), myVariable, statements);
    List<PsiPatternVariable> vars = JavaPsiPatternUtil.getExposedPatternVariables(condition);
    if (!vars.isEmpty()) {
      List<PsiPatternVariable> used =
        ContainerUtil.filter(vars, var -> ContainerUtil.or(statements, st -> VariableAccessUtils.variableIsUsed(var, st)));
      if (used.size() > 1) return null;
      if (!used.isEmpty()) {
        PsiPatternVariable var = used.get(0);
        String text = JavaPsiPatternUtil.getEffectiveInitializerText(var);
        if (text == null) return null;
        if (ContainerUtil.or(statements, st -> VariableAccessUtils.variableIsUsed(myVariable, st))) return null;
        PsiExpression mappingExpression = JavaPsiFacade.getElementFactory(condition.getProject()).createExpressionFromText(text, var);
        result = new TerminalBlock(result, new MapOp(mappingExpression, myVariable, var.getType()), var, statements);
      }
    }
    return result;
  }

  /**
   * Returns an equivalent {@code TerminalBlock} with one more intermediate operation extracted
   * or null if extraction is not possible.
   *
   * @return extracted operation or null if extraction is not possible
   */
  private @Nullable TerminalBlock extractOperation() {
    TerminalBlock withFilter = extractFilter();
    if(withFilter != null) return withFilter;
    // extract flatMap
    if(getSingleStatement() instanceof PsiLoopStatement loopStatement) {
      StreamSource source = StreamSource.tryCreate(loopStatement);
      final PsiStatement body = loopStatement.getBody();
      if(source == null || body == null) return null;
      FlatMapOp op = new FlatMapOp(source, myVariable);
      TerminalBlock withFlatMap = new TerminalBlock(this, op, source.getVariable(), body);
      if(!VariableAccessUtils.variableIsUsed(myVariable, body)) {
        return withFlatMap;
      } else {
        // Try extract nested filter like this:
        // for(List subList : list) for(T t : subList) if(condition.test(t)) { ...; break; }
        // if t is not used in "...", then this could be converted to
        // list.stream().filter(subList -> subList.stream().anyMatch(condition)).forEach(subList -> ...)
        TerminalBlock withFlatMapFilter = withFlatMap.extractFilter();
        if(withFlatMapFilter != null && !withFlatMapFilter.isEmpty()) {
          PsiStatement[] statements = withFlatMapFilter.getStatements();
          PsiStatement lastStatement = statements[statements.length-1];
          boolean flowBreaks = lastStatement instanceof PsiBreakStatement && op.breaksMe((PsiBreakStatement)lastStatement) ||
                               lastStatement instanceof PsiReturnStatement ||
                               lastStatement instanceof PsiThrowStatement;
          if (flowBreaks && ReferencesSearch.search(withFlatMapFilter.getVariable(), new LocalSearchScope(statements)).findFirst() == null) {
            FilterOp filterOp = (FilterOp)withFlatMapFilter.getLastOperation();
            return new TerminalBlock(this, new CompoundFilterOp(op.getSource(), op.getVariable(), filterOp),
                                     myVariable,
                                     lastStatement instanceof PsiReturnStatement || lastStatement instanceof PsiThrowStatement
                                     ? statements
                                     : Arrays.copyOf(statements, statements.length - 1));
          }
        }
      }
    }
    if(myStatements.length >= 1) {
      PsiStatement first = myStatements[0];
      if(PsiUtil.isLanguageLevel9OrHigher(myVariable.getContainingFile()) && first instanceof PsiIfStatement ifStatement) {
        PsiExpression condition = ifStatement.getCondition();
        if(ifStatement.getElseBranch() == null && condition != null) {
          PsiStatement thenStatement = ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
          PsiStatement sourceStatement = getStreamSourceStatement();
          if(sourceStatement instanceof PsiLoopStatement loop && ControlFlowUtils.statementBreaksLoop(thenStatement, loop)) {
            TakeWhileOp op = new TakeWhileOp(condition, myVariable, true);
            PsiStatement[] leftOver = Arrays.copyOfRange(myStatements, 1, myStatements.length);
            return new TerminalBlock(this, op, myVariable, leftOver);
          }
        }
      }
      // extract map
      if(first instanceof PsiDeclarationStatement decl) {
        PsiElement[] elements = decl.getDeclaredElements();
        if(elements.length == 1) {
          PsiLocalVariable declaredVar = tryCast(elements[0], PsiLocalVariable.class);
          if (declaredVar != null && StreamApiUtil.isSupportedStreamElement(declaredVar.getType())) {
            PsiExpression initializer = declaredVar.getInitializer();
            PsiStatement[] leftOver = Arrays.copyOfRange(myStatements, 1, myStatements.length);
            if (initializer != null && ReferencesSearch.search(myVariable, new LocalSearchScope(leftOver)).findFirst() == null) {
              MapOp op = new MapOp(initializer, myVariable, declaredVar.getType());
              return new TerminalBlock(this, op, declaredVar, leftOver);
            }
          }
        }
      }
      PsiExpression rValue = ExpressionUtils.getAssignmentTo(first, myVariable);
      if(rValue != null && operations().allMatch(op -> op.canReassignVariable(myVariable))) {
        PsiStatement[] leftOver = Arrays.copyOfRange(myStatements, 1, myStatements.length);
        MapOp op = new MapOp(rValue, myVariable, myVariable.getType());
        return new TerminalBlock(this, op, myVariable, leftOver);
      }
    }
    TerminalBlock withLimit = tryPeelLimit(true);
    return withLimit == this ? null : withLimit;
  }

  /**
   * Try to peel off the condition like if(count > limit) break; from the end of current terminal block.
   *
   * <p>It's not guaranteed that the peeled condition actually could be translated to the limit operation:
   * additional checks will be necessary</p>
   *
   * @param dedicatedCounter whether peeled counter must be an increment statement for dedicated local variable
   * @return new terminal block with additional limit operation or self if peeling is failed.
   */
  private TerminalBlock tryPeelLimit(boolean dedicatedCounter) {
    if(myStatements.length == 0) return this;
    TerminalBlock tb = this;
    PsiStatement[] statements = {};
    if(myStatements.length > 1) {
      int count = myStatements.length - 1;
      if (myStatements[count] instanceof PsiBreakStatement || myStatements[count] instanceof PsiReturnStatement) {
        // to support conditions like if(...) continue; break; or if(...) continue; return ...;
        count--;
      }
      statements = Arrays.copyOf(myStatements, count);
      tb = new TerminalBlock(myOperations, myVariable, Arrays.copyOfRange(myStatements, count, myStatements.length)).extractFilter();
    }
    PsiStatement sourceStatement = getStreamSourceStatement();
    if (tb == null || (sourceStatement instanceof PsiLoopStatement loop && 
                       !ControlFlowUtils.statementBreaksLoop(tb.getSingleStatement(), loop))) return this;
    FilterOp filter = tb.getLastOperation(FilterOp.class);
    if (filter == null) return this;
    PsiBinaryExpression binOp = tryCast(PsiUtil.skipParenthesizedExprDown(filter.getExpression()), PsiBinaryExpression.class);
    if (!ComparisonUtils.isComparison(binOp)) return this;
    String comparison = filter.isNegated() ? ComparisonUtils.getNegatedComparison(binOp.getOperationTokenType())
                        : binOp.getOperationSign().getText();
    boolean flipped = false;
    int delta = 0;
    switch (comparison) {
      case "==", ">=" -> { }
      case ">" -> delta = 1;
      case "<" -> {
        delta = 1;
        flipped = true;
      }
      case "<=" -> flipped = true;
      default -> {
        return this;
      }
    }
    PsiExpression countExpression = PsiUtil.skipParenthesizedExprDown(flipped ? binOp.getROperand() : binOp.getLOperand());
    if(countExpression == null || VariableAccessUtils.variableIsUsed(myVariable, countExpression)) return this;
    PsiExpression incrementedValue = extractIncrementedLValue(countExpression);
    PsiLocalVariable var = null;
    if (dedicatedCounter) {
      if (!(incrementedValue instanceof PsiReferenceExpression ref)) return this;
      var = tryCast(ref.resolve(), PsiLocalVariable.class);
      if (var == null || !ExpressionUtils.isZero(var.getInitializer()) || VariableAccessUtils.getVariableReferences(var).size() != 1) return this;
    }
    PsiExpression limit = flipped ? binOp.getLOperand() : binOp.getROperand();
    if(!ExpressionUtils.isSafelyRecomputableExpression(limit) || VariableAccessUtils.variableIsUsed(myVariable, limit)) return this;
    PsiType type = limit.getType();
    if(!PsiTypes.intType().equals(type) && !PsiTypes.longType().equals(type)) return this;
    if(countExpression instanceof PsiPostfixExpression) {
      delta++;
    }

    TerminalBlock block = new TerminalBlock(Arrays.copyOf(tb.myOperations, tb.myOperations.length-1), myVariable, statements);
    if (incrementedValue == null) {
      // when countExpression does not change the counter, we may try to continue extracting ops from the remaining statement
      // this is helpful to cover cases like for(...) { if(...) list.add(x); if(list.size == limit) break; }
      while (true) {
        TerminalBlock newBlock = block.extractOperation();
        if (newBlock == null || newBlock.getLastOperation() instanceof FlatMapOp) break;
        block = newBlock;
      }
    }
    return block.add(new LimitOp(block.getVariable(), countExpression, limit, var, delta));
  }

  private @Nullable PsiLocalVariable extractCollectionAdditionVariable() {
    PsiMethodCallExpression call = getSingleMethodCall();
    if (!isCallOf(call, CommonClassNames.JAVA_UTIL_COLLECTION, "add")) return null;
    PsiExpression[] args = call.getArgumentList().getExpressions();
    if (args.length != 1 || !ExpressionUtils.isReferenceTo(args[0], myVariable)) return null;
    PsiReferenceExpression qualifier = tryCast(call.getMethodExpression().getQualifierExpression(), PsiReferenceExpression.class);
    if (qualifier == null) return null;
    PsiLocalVariable var = tryCast(qualifier.resolve(), PsiLocalVariable.class);
    if (var == null) return null;
    PsiNewExpression initializer = tryCast(var.getInitializer(), PsiNewExpression.class);
    if (initializer == null) return null;
    PsiExpressionList argumentList = initializer.getArgumentList();
    if (argumentList == null || !argumentList.isEmpty() ||
        ControlFlowUtils.getInitializerUsageStatus(var, getStreamSourceStatement()) == ControlFlowUtils.InitializerUsageStatus.UNKNOWN) {
      return null;
    }
    return var;
  }

  private @NotNull TerminalBlock replace(Operation orig, Operation replacement) {
    int idx = ArrayUtil.indexOf(myOperations, orig);
    if(idx == -1) {
      throw new NoSuchElementException(orig.toString());
    }
    Operation[] ops = myOperations.clone();
    ops[idx] = replacement;
    return new TerminalBlock(ops, myVariable, myStatements);
  }

  TerminalBlock add(Operation op) {
    return new TerminalBlock(this, op, myVariable, myStatements);
  }

  private TerminalBlock tryExtractDistinct() {
    PsiLocalVariable collectionVariable = extractCollectionAdditionVariable();
    if(collectionVariable == null) return this;
    for(int idx = myOperations.length-1; idx > 0; idx--) {
      Operation op = myOperations[idx];
      if (op instanceof FilterOp filter) {
        PsiExpression condition = filter.getExpression();
        if (BoolUtils.isNegation(condition)) {
          if (filter.isNegated()) continue;
          condition = BoolUtils.getNegated(condition);
        }
        else if (!filter.isNegated()) continue;
        if (!(condition instanceof PsiMethodCallExpression conditionCall)) continue;
        if (!ExpressionUtils.isReferenceTo(conditionCall.getMethodExpression().getQualifierExpression(), collectionVariable) ||
            !isCallOf(conditionCall, CommonClassNames.JAVA_UTIL_COLLECTION, "contains")) {
          continue;
        }
        PsiExpression[] conditionArgs = conditionCall.getArgumentList().getExpressions();
        if (conditionArgs.length == 1 && ExpressionUtils.isReferenceTo(conditionArgs[0], myVariable)) {
          return replace(op, new DistinctOp(myVariable));
        }
      } else if (!(op instanceof LimitOp)) {
        break;
      }
    }
    return this;
  }

  @NotNull
  Operation getLastOperation() {
    return myOperations[myOperations.length-1];
  }

  @Nullable
  TerminalBlock withoutLastOperation() {
    if(myOperations.length == 1) return null;
    Operation[] operations = new Operation[myOperations.length - 1];
    System.arraycopy(myOperations, 0, operations, 0, operations.length);
    return new TerminalBlock(operations, operations[operations.length - 1].getVariable(), myStatements);
  }

  @Nullable
  <T extends Operation> T getLastOperation(Class<T> clazz) {
    return tryCast(getLastOperation(), clazz);
  }

  @Nullable
  PsiExpression getCountExpression() {
    LimitOp limitOp = getLastOperation(LimitOp.class);
    if (limitOp != null && limitOp.getCounterVariable() == null) {
      return limitOp.getCountExpression();
    }
    return null;
  }

  /**
   * Extract all possible intermediate operations
   * @return the terminal block with all possible terminal operations extracted (may return this if no operations could be extracted)
   */
  private @NotNull TerminalBlock extractOperations() {
    return StreamEx.iterate(this, Objects::nonNull, TerminalBlock::extractOperation).reduce((a, b) -> b).orElse(this);
  }

  @NotNull
  PsiVariable getVariable() {
    return myVariable;
  }

  boolean hasOperations() {
    return myOperations.length > 1;
  }

  boolean isEmpty() {
    return myStatements.length == 0;
  }

  @NotNull
  StreamEx<Operation> operations() {
    return StreamEx.ofReversed(myOperations);
  }

  /**
   * @return generally {@link PsiLoopStatement} - main loop
   */
  PsiStatement getStreamSourceStatement() {
    return ((StreamSource)myOperations[0]).getMainStatement();
  }

  /**
   * @return stream of physical expressions used in intermediate operations in arbitrary order
   */
  private StreamEx<PsiExpression> intermediateExpressions() {
    return StreamEx.of(myOperations, 1, myOperations.length).flatMap(Operation::expressions);
  }

  /**
   * @return stream of physical expressions used in stream source and intermediate operations in arbitrary order
   */
  StreamEx<PsiExpression> intermediateAndSourceExpressions() {
    return operations().flatMap(Operation::expressions);
  }

  /**
   * Converts this TerminalBlock to PsiElement (either PsiStatement or PsiCodeBlock)
   *
   * @param ct CommentTracker to mark statements as unchanged
   * @param factory factory to use to create new element if necessary
   * @return the PsiElement
   */
  PsiElement convertToElement(CommentTracker ct, PsiElementFactory factory) {
    if (myStatements.length == 1) {
      return myStatements[0];
    }
    PsiCodeBlock block = factory.createCodeBlockFromText("{}", myVariable);
    for (PsiStatement statement : myStatements) {
      block.add(ct.markUnchanged(statement));
    }
    return block;
  }


  /**
   * method replaces continue statement (without labels) to return statement
   * @param factory factory to use to create new element if necessary
   */
  void replaceContinueWithReturn(PsiElementFactory factory) {
    PsiLoopStatement currentLoop = PsiTreeUtil.getParentOfType(myStatements[0], PsiLoopStatement.class);
    if (currentLoop == null) return;
    for (int i = 0, length = myStatements.length; i < length; i++) {
      PsiStatement statement = myStatements[i];
      if(statement instanceof PsiContinueStatement) {
        myStatements[i] = factory.createStatementFromText("return;", null);
        continue;
      }
      StreamEx.ofTree(statement, (PsiElement s) -> StreamEx.of(s.getChildren()))
        .select(PsiContinueStatement.class)
        .filter(stmt -> stmt.findContinuedStatement() == currentLoop)
        .forEach(stmt -> new CommentTracker().replaceAndRestoreComments(stmt, "return;"));
    }
  }

  String generate(CommentTracker ct) {
    return generate(ct, false);
  }

  String generate(CommentTracker ct, boolean noStreamForEmpty) {
    if(noStreamForEmpty && myOperations.length == 1 && myOperations[0] instanceof CollectionStream) {
      return ParenthesesUtils.getText(myOperations[0].getExpression(), ParenthesesUtils.POSTFIX_PRECEDENCE);
    }
    return StreamEx.of(myOperations).map(operation -> operation.createReplacement(ct)).joining();
  }

  static @NotNull TerminalBlock from(StreamSource source, @NotNull PsiStatement body) {
    return fromStatements(source, body);
  }

  static @NotNull TerminalBlock fromStatements(StreamSource source, PsiStatement @NotNull ... statements) {
    return new TerminalBlock(null, source, source.myVariable, statements).extractOperations().tryPeelLimit(false).tryExtractDistinct();
  }

  static @NotNull TerminalBlock from(StreamSource source, @NotNull PsiCodeBlock block) {
    return fromStatements(source, block.getStatements());
  }

  boolean dependsOn(PsiExpression qualifier) {
    return intermediateExpressions().anyMatch(expression -> isExpressionDependsOnUpdatedCollections(expression, qualifier));
  }

  boolean isReferencedInOperations(PsiVariable variable) {
    return intermediateAndSourceExpressions().anyMatch(expr -> VariableAccessUtils.variableIsUsed(variable, expr));
  }
}
