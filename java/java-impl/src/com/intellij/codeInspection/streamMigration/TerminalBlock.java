/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.ControlFlow;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.IntArrayList;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import one.util.streamex.MoreCollectors;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

import static com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection.*;

/**
 * This immutable class represents the code which should be performed
 * as a part of forEach operation of resulting stream possibly with
 * some intermediate operations extracted.
 */
class TerminalBlock {
  private static final Logger LOG = Logger.getInstance(TerminalBlock.class);

  private final @NotNull Operation myPreviousOp;
  private final @NotNull PsiVariable myVariable;
  private final @NotNull PsiStatement[] myStatements;

  // At least one previous operation is present (stream source)
  private TerminalBlock(@NotNull Operation previousOp, @NotNull PsiVariable variable, @NotNull PsiStatement... statements) {
    for(PsiStatement statement : statements) Objects.requireNonNull(statement);
    myVariable = variable;
    while(true) {
      if(statements.length == 1 && statements[0] instanceof PsiBlockStatement) {
        statements = ((PsiBlockStatement)statements[0]).getCodeBlock().getStatements();
      } else if(statements.length == 1 && statements[0] instanceof PsiLabeledStatement) {
        statements = new PsiStatement[] {((PsiLabeledStatement)statements[0]).getStatement()};
      } else break;
    }
    myStatements = statements;
    myPreviousOp = previousOp;
  }

  Collection<PsiStatement> findExitPoints(ControlFlow controlFlow) {
    int startOffset = controlFlow.getStartOffset(myStatements[0]);
    int endOffset = controlFlow.getEndOffset(myStatements[myStatements.length - 1]);
    if (startOffset < 0 || endOffset < 0) return null;
    return ControlFlowUtil
      .findExitPointsAndStatements(controlFlow, startOffset, endOffset, new IntArrayList(), PsiContinueStatement.class,
                                   PsiBreakStatement.class, PsiReturnStatement.class, PsiThrowStatement.class);
  }

  PsiStatement getSingleStatement() {
    return myStatements.length == 1 ? myStatements[0] : null;
  }

  @NotNull
  PsiStatement[] getStatements() {
    return myStatements;
  }

  @Nullable
  <T extends PsiExpression> T getSingleExpression(Class<T> wantedType) {
    PsiStatement statement = getSingleStatement();
    if(statement instanceof PsiExpressionStatement) {
      PsiExpression expression = ((PsiExpressionStatement)statement).getExpression();
      if(wantedType.isInstance(expression))
        return wantedType.cast(expression);
    }
    return null;
  }

  /**
   * @return PsiMethodCallExpression if this TerminalBlock contains single method call, null otherwise
   */
  @Nullable
  PsiMethodCallExpression getSingleMethodCall() {
    return getSingleExpression(PsiMethodCallExpression.class);
  }

  @Nullable
  private TerminalBlock extractFilter() {
    if(getSingleStatement() instanceof PsiIfStatement) {
      PsiIfStatement ifStatement = (PsiIfStatement)getSingleStatement();
      if(ifStatement.getElseBranch() == null && ifStatement.getCondition() != null) {
        PsiStatement thenBranch = ifStatement.getThenBranch();
        if(thenBranch != null) {
          return new TerminalBlock(new FilterOp(myPreviousOp, ifStatement.getCondition(), myVariable, false), myVariable, thenBranch);
        }
      }
    }
    if(myStatements.length >= 1) {
      PsiStatement first = myStatements[0];
      // extract filter with negation
      if(first instanceof PsiIfStatement) {
        PsiIfStatement ifStatement = (PsiIfStatement)first;
        if(ifStatement.getCondition() == null) return null;
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
        return new TerminalBlock(new FilterOp(myPreviousOp, ifStatement.getCondition(), myVariable, true), myVariable, statements);
      }
    }
    return null;
  }

  /**
   * Returns an equivalent {@code TerminalBlock} with one more intermediate operation extracted
   * or null if extraction is not possible.
   *
   * @return extracted operation or null if extraction is not possible
   */
  @Nullable
  private TerminalBlock extractOperation() {
    TerminalBlock withFilter = extractFilter();
    if(withFilter != null) return withFilter;
    // extract flatMap
    if(getSingleStatement() instanceof PsiLoopStatement) {
      PsiLoopStatement loopStatement = (PsiLoopStatement)getSingleStatement();
      StreamSource source = StreamSource.tryCreate(loopStatement);
      final PsiStatement body = loopStatement.getBody();
      if(source == null || body == null) return null;
      // flatMap from primitive to primitive is supported only if primitive types match
      // otherwise it would be necessary to create bogus step like
      // .mapToObj(var -> collection.stream()).flatMap(Function.identity())
      if(myVariable.getType() instanceof PsiPrimitiveType && !myVariable.getType().equals(source.getVariable().getType())) return null;
      FlatMapOp op = new FlatMapOp(myPreviousOp, source, myVariable);
      TerminalBlock withFlatMap = new TerminalBlock(op, source.getVariable(), body);
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
          if (lastStatement instanceof PsiBreakStatement && op.breaksMe((PsiBreakStatement)lastStatement) &&
              ReferencesSearch.search(withFlatMapFilter.getVariable(), new LocalSearchScope(statements)).findFirst() == null) {
            return new TerminalBlock(new CompoundFilterOp((FilterOp)withFlatMapFilter.getLastOperation(), op),
                                     myVariable, Arrays.copyOfRange(statements, 0, statements.length-1));
          }
        }
      }
    }
    if(myStatements.length >= 1) {
      PsiStatement first = myStatements[0];
      // extract map
      if(first instanceof PsiDeclarationStatement) {
        PsiDeclarationStatement decl = (PsiDeclarationStatement)first;
        PsiElement[] elements = decl.getDeclaredElements();
        if(elements.length == 1) {
          PsiElement element = elements[0];
          if(element instanceof PsiLocalVariable) {
            PsiLocalVariable declaredVar = (PsiLocalVariable)element;
            if (isSupported(declaredVar.getType())) {
              PsiExpression initializer = declaredVar.getInitializer();
              PsiStatement[] leftOver = Arrays.copyOfRange(myStatements, 1, myStatements.length);
              if (initializer != null && ReferencesSearch.search(myVariable, new LocalSearchScope(leftOver)).findFirst() == null) {
                MapOp op = new MapOp(myPreviousOp, initializer, myVariable, declaredVar.getType());
                return new TerminalBlock(op, declaredVar, leftOver);
              }
            }
          }
        }
      }
      PsiExpression rValue = ExpressionUtils.getAssignmentTo(first, myVariable);
      if(rValue != null) {
        PsiStatement[] leftOver = Arrays.copyOfRange(myStatements, 1, myStatements.length);
        MapOp op = new MapOp(myPreviousOp, rValue, myVariable, myVariable.getType());
        return new TerminalBlock(op, myVariable, leftOver);
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
      statements = Arrays.copyOfRange(myStatements, 0, count);
      tb = new TerminalBlock(myPreviousOp, myVariable, Arrays.copyOfRange(myStatements, count, myStatements.length)).extractFilter();
    }
    if (tb == null || !ControlFlowUtils.statementBreaksLoop(tb.getSingleStatement(), getMainLoop())) return this;
    FilterOp filter = tb.getLastOperation(FilterOp.class);
    if(filter == null) return this;
    PsiExpression condition = PsiUtil.skipParenthesizedExprDown(filter.getExpression());
    if(!(condition instanceof PsiBinaryExpression)) return this;
    PsiBinaryExpression binOp = (PsiBinaryExpression)condition;
    if(!ComparisonUtils.isComparison(binOp)) return this;
    String comparison = filter.isNegated() ? ComparisonUtils.getNegatedComparison(binOp.getOperationTokenType())
                        : binOp.getOperationSign().getText();
    boolean flipped = false;
    int delta = 0;
    switch (comparison) {
      case "==":
      case ">=":
        break;
      case ">":
        delta = 1;
        break;
      case "<":
        delta = 1;
        flipped = true;
        break;
      case "<=":
        flipped = true;
        break;
      default:
        return this;
    }
    PsiExpression countExpression = PsiUtil.skipParenthesizedExprDown(flipped ? binOp.getROperand() : binOp.getLOperand());
    if(countExpression == null || VariableAccessUtils.variableIsUsed(myVariable, countExpression)) return this;
    PsiExpression incrementedValue = extractIncrementedLValue(countExpression);
    PsiLocalVariable var = null;
    if (dedicatedCounter) {
      if (!(incrementedValue instanceof PsiReferenceExpression)) return this;
      PsiElement element = ((PsiReferenceExpression)incrementedValue).resolve();
      if (!(element instanceof PsiLocalVariable)) return this;
      var = (PsiLocalVariable)element;
      if (!ExpressionUtils.isZero(var.getInitializer()) || ReferencesSearch.search(var).findAll().size() != 1) return this;
    }
    PsiExpression limit = flipped ? binOp.getLOperand() : binOp.getROperand();
    if(!ExpressionUtils.isSimpleExpression(limit) || VariableAccessUtils.variableIsUsed(myVariable, limit)) return this;
    PsiType type = limit.getType();
    if(!PsiType.INT.equals(type) && !PsiType.LONG.equals(type)) return this;
    if(countExpression instanceof PsiPostfixExpression) {
      delta++;
    }

    Operation prev = filter.getPreviousOp();
    LOG.assertTrue(prev != null);
    TerminalBlock block = new TerminalBlock(prev, myVariable, statements);
    if (incrementedValue == null) {
      // when countExpression does not change the counter, we may try to continue extracting ops from the remaining statement
      // this is helpful to cover cases like for(...) { if(...) list.add(x); if(list.size == limit) break; }
      while (true) {
        TerminalBlock newBlock = block.extractOperation();
        if (newBlock == null || newBlock.getLastOperation() instanceof FlatMapOp) break;
        block = newBlock;
      }
    }
    LimitOp limitOp = new LimitOp(block.getLastOperation(), block.getVariable(), countExpression, limit, var, delta);
    return new TerminalBlock(limitOp, block.getVariable(), block.getStatements());
  }

  @NotNull
  Operation getLastOperation() {
    return myPreviousOp;
  }

  @Nullable
  <T extends Operation> T getLastOperation(Class<T> clazz) {
    return clazz.isInstance(myPreviousOp) ? clazz.cast(myPreviousOp) : null;
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
  @NotNull
  TerminalBlock extractOperations() {
    return StreamEx.iterate(this, Objects::nonNull, TerminalBlock::extractOperation).reduce((a, b) -> b).orElse(this);
  }

  @NotNull
  PsiVariable getVariable() {
    return myVariable;
  }

  boolean hasOperations() {
    return !(myPreviousOp instanceof StreamSource);
  }

  boolean isEmpty() {
    return myStatements.length == 0;
  }

  @NotNull
  StreamEx<Operation> operations() {
    return StreamEx.iterate(myPreviousOp, Objects::nonNull, Operation::getPreviousOp);
  }

  Collection<Operation> getOperations() {
    ArrayDeque<Operation> ops = new ArrayDeque<>();
    operations().forEach(ops::addFirst);
    return ops;
  }

  StreamSource getSource() {
    return operations().select(StreamSource.class).collect(MoreCollectors.onlyOne()).orElseThrow(IllegalStateException::new);
  }

  PsiLoopStatement getMainLoop() {
    return getSource().getLoop();
  }

  /**
   * @return stream of physical expressions used in intermediate operations in arbitrary order
   */
  StreamEx<PsiExpression> intermediateExpressions() {
    return operations().remove(StreamSource.class::isInstance).flatMap(Operation::expressions);
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
   * @param factory factory to use to create new element if necessary
   * @return the PsiElement
   */
  PsiElement convertToElement(PsiElementFactory factory) {
    if (myStatements.length == 1) {
      return myStatements[0];
    }
    PsiCodeBlock block = factory.createCodeBlock();
    for (PsiStatement statement : myStatements) {
      block.add(statement);
    }
    return block;
  }

  @NotNull
  static TerminalBlock from(StreamSource source, @NotNull PsiStatement body) {
    return new TerminalBlock(source, source.myVariable, body).extractOperations().tryPeelLimit(false);
  }

  boolean dependsOn(PsiExpression qualifier) {
    return intermediateExpressions().anyMatch(expression -> isExpressionDependsOnUpdatedCollections(expression, qualifier));
  }

  boolean isReferencedInOperations(PsiVariable variable) {
    return intermediateAndSourceExpressions().anyMatch(expr -> VariableAccessUtils.variableIsUsed(variable, expr));
  }

  private static boolean isExpressionDependsOnUpdatedCollections(PsiExpression condition,
                                                                 PsiExpression qualifierExpression) {
    final PsiElement collection = qualifierExpression instanceof PsiReferenceExpression
                                  ? ((PsiReferenceExpression)qualifierExpression).resolve()
                                  : null;
    if (collection != null) {
      return collection instanceof PsiVariable && VariableAccessUtils.variableIsUsed((PsiVariable)collection, condition);
    }

    final boolean[] dependsOnCollection = {false};
    condition.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        final PsiExpression callQualifier = expression.getMethodExpression().getQualifierExpression();
        if (callQualifier == null ||
            callQualifier instanceof PsiThisExpression && ((PsiThisExpression)callQualifier).getQualifier() == null ||
            callQualifier instanceof PsiSuperExpression && ((PsiSuperExpression)callQualifier).getQualifier() == null) {
          dependsOnCollection[0] = true;
        }
      }

      @Override
      public void visitThisExpression(PsiThisExpression expression) {
        super.visitThisExpression(expression);
        if (expression.getQualifier() == null && expression.getParent() instanceof PsiExpressionList) {
          dependsOnCollection[0] = true;
        }
      }

      @Override
      public void visitClass(PsiClass aClass) {}

      @Override
      public void visitLambdaExpression(PsiLambdaExpression expression) {}
    });

    return dependsOnCollection[0];
  }
}
