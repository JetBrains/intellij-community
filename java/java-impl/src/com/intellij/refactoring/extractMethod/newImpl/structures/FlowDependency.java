// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.structures;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.refactoring.extractMethod.newImpl.ControlFlowOnFragment;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

public class FlowDependency {

  public final CodeFragment fragment;
  public final List<PsiVariable> missedDeclarations;
  public final List<PsiVariable> inputVariables;
  public final List<ExpressionGroup> parameterGroups;
  public final List<PsiType> parameterTypes;
  public final List<PsiVariable> outputVariables;
  public final List<PsiType> outputTypes;
  public final List<StatementGroup> exitGroups;
  public final boolean returnsLocalVariable;
  public final boolean canCompleteNormally;
  public final boolean hasSingleExit;
  public final List<PsiClassType> thrownExceptions;

  public static FlowDependency computeDependency(CodeFragment fragment){
    return new FlowDependency(fragment);
  }

  private FlowDependency(CodeFragment fragment){
    final ControlFlowOnFragment flowOnFragment = ControlFlowOnFragment.create(fragment);
    this.fragment = fragment;
    this.inputVariables = flowOnFragment.findInputVariables();
    this.parameterTypes = ContainerUtil.map(inputVariables, variable -> variable.getType());
    this.parameterGroups = findInputGroups(inputVariables, fragment);
    this.outputVariables = flowOnFragment.findOutputVariables();
    this.outputTypes = ContainerUtil.map(outputVariables, variable -> variable.getType());
    final List<PsiStatement> exitStatements = flowOnFragment.findExitStatements();
    this.exitGroups = findExitGroups(exitStatements);
    final List<PsiVariable> writtenVariables = flowOnFragment.findWrittenVariables();
    this.missedDeclarations = findWrittenVariablesWithoutDeclaration(writtenVariables, fragment);
    missedDeclarations.removeAll(inputVariables);
    this.returnsLocalVariable = dependsOnVariables(writtenVariables, exitStatements);
    this.canCompleteNormally = flowOnFragment.canCompleteNormally();
    this.hasSingleExit = flowOnFragment.hasSingleExit();
    this.thrownExceptions = findThrownExceptions(fragment);
  }

  private static List<PsiClassType> findThrownExceptions(CodeFragment fragment){
    return ExceptionUtil.getThrownCheckedExceptions(fragment.elements.toArray(PsiElement.EMPTY_ARRAY));
  }

  public static boolean hasOnlyNotNullAssignments(PsiVariable variable, CodeFragment scope) {
    final boolean[] isNotNull = {true};
    final JavaRecursiveElementWalkingVisitor visitor = new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitAssignmentExpression(PsiAssignmentExpression assignment) {
        if (!isNotNull[0]) return;
        final PsiReference reference = assignment.getLExpression().getReference();
        if (reference == null) return;
        if (reference.isReferenceTo(variable)) {
          isNotNull[0] = isNotNull((PsiCodeBlock)scope.getCommonParent(), assignment.getRExpression());
        }
      }
    };
    scope.elements.forEach(it -> it.accept(visitor));
    return isNotNull[0];
  }

  //TODO optimize
  public static boolean hasOnlyNotNullReturns(CodeFragment fragment, List<PsiReturnStatement> returns){
    return returns.stream().allMatch(ret -> isNotNull((PsiCodeBlock) fragment.getCommonParent(), ret.getReturnValue()));
  }

  private static List<ExpressionGroup> findInputGroups(List<PsiVariable> inputVariables, CodeFragment fragment) {
    return ContainerUtil.map(inputVariables, variable -> findDependentReferences(variable, fragment));
  }

  private static List<PsiVariable> findWrittenVariablesWithoutDeclaration(Collection<PsiVariable> variables, CodeFragment scope){
    return ContainerUtil.filter(variables, variable -> ! scope.getTextRange().contains(variable.getTextRange()));
  }

  private static List<StatementGroup> findExitGroups(List<PsiStatement> controlStatements) {
    final Collection<List<PsiStatement>> statementGroups = controlStatements.stream()
      .collect(groupingBy(statement -> statement.getNode().getElementType())).values();
    return ContainerUtil.map(statementGroups, statements -> StatementGroup.of(statements));
  }

  private static ExpressionGroup findDependentReferences(PsiVariable variable, CodeFragment fragment) {
    return ExpressionGroup.of(findReferencesInFragment(variable, fragment));
  }

  private static List<PsiExpression> findReferencesInFragment(PsiVariable variable, CodeFragment fragment) {
    final Stream<PsiReferenceExpression> references = fragment.elements.stream()
      .flatMap(statement -> VariableAccessUtils.getVariableReferences(variable, statement).stream());
    return references.collect(toList());
  }

  private static boolean dependsOnVariables(Collection<PsiVariable> variables, List<? extends PsiElement> searchContext){
    class Visitor extends JavaRecursiveElementWalkingVisitor {
      boolean hasDependentReference = false;

      @Override
      public void visitReferenceExpression(PsiReferenceExpression reference) {
        super.visitReferenceExpression(reference);
        final boolean isDependentReference = variables.stream().anyMatch(variable -> reference.isReferenceTo(variable));
        if (isDependentReference) hasDependentReference = true;
      }
    }

    final Visitor visitor = new Visitor();
    for (PsiElement expression: searchContext) {
      expression.accept(visitor);
    }
    return visitor.hasDependentReference;
  }

  private static boolean isNotNull(PsiCodeBlock block, PsiExpression expr) {
    final DataFlowRunner dfaRunner = new DataFlowRunner(block.getProject());

    class Visitor extends StandardInstructionVisitor {
      boolean isNotNull = true;

      @Override
      protected void beforeExpressionPush(@NotNull DfaValue value,
                                          @NotNull PsiExpression expression,
                                          @Nullable TextRange range,
                                          @NotNull DfaMemoryState state) {
        if (expression == expr) {
          isNotNull = isNotNull && state.isNotNull(value);
        }
      }
    }
    Visitor visitor = new Visitor();
    final RunnerResult rc = dfaRunner.analyzeCodeBlock(block, visitor);
    return rc == RunnerResult.OK && visitor.isNotNull;
  }
}
