/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 28, 2002
 * Time: 10:16:39 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReturnStatement;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class StandardDataFlowRunner extends DataFlowRunner {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.DataFlowRunner");

  private final HashSet<Instruction> myNPEInstructions = new HashSet<Instruction>();
  private final HashSet<Instruction> myCCEInstructions = new HashSet<Instruction>();
  private final HashSet<PsiExpression> myNullableArguments = new HashSet<PsiExpression>();
  private final HashSet<PsiExpression> myNullableAssignments = new HashSet<PsiExpression>();
  private final HashSet<PsiReturnStatement> myNullableReturns = new HashSet<PsiReturnStatement>();

  private final boolean mySuggestNullableAnnotations;
  private boolean myInNullableMethod = false;
  private boolean myInNotNullMethod = false;
  private boolean myIsInMethod = false;

  private Set<PsiExpression> myUnboxedNullables = new THashSet<PsiExpression>();

  public StandardDataFlowRunner(boolean suggestNullableAnnotations) {
    super(new StandardInstructionFactory());
    mySuggestNullableAnnotations = suggestNullableAnnotations;
  }

  public RunnerResult analyzeMethod(PsiCodeBlock psiBlock) {
    myIsInMethod = psiBlock.getParent() instanceof PsiMethod;

    if (myIsInMethod) {
      PsiMethod method = (PsiMethod)psiBlock.getParent();
      myInNullableMethod = AnnotationUtil.isNullable(method);
      myInNotNullMethod = AnnotationUtil.isNotNull(method);
    }
    return super.analyzeMethod(psiBlock);
  }

  public void onInstructionProducesNPE(Instruction instruction) {
    myNPEInstructions.add(instruction);
  }

  public void onInstructionProducesCCE(Instruction instruction) {
    myCCEInstructions.add(instruction);
  }

  @NotNull public Set<Instruction> getCCEInstructions() {
    return myCCEInstructions;
  }

  @NotNull public Set<Instruction> getNPEInstructions() {
    return myNPEInstructions;
  }

  @NotNull public Set<PsiReturnStatement> getNullableReturns() {
    return myNullableReturns;
  }

  public boolean isInNullableMethod() {
    return myInNullableMethod;
  }

  public boolean isInNotNullMethod() {
    return myInNotNullMethod;
  }

  @NotNull public Set<PsiExpression> getNullableArguments() {
    return myNullableArguments;
  }

  @NotNull public Set<PsiExpression> getNullableAssignments() {
    return myNullableAssignments;
  }

  @NotNull public Set<PsiExpression> getUnboxedNullables() {
    return myUnboxedNullables;
  }

  public void onUnboxingNullable(@NotNull PsiExpression expression) {
    LOG.assertTrue(expression.isValid());
    if (expression.isPhysical()) {
      myUnboxedNullables.add(expression);
    }
  }

  public void onPassingNullParameter(PsiExpression expr) {
    myNullableArguments.add(expr);
  }

  public void onAssigningToNotNullableVariable(final PsiExpression expr) {
    myNullableAssignments.add(expr);
  }

  public void onNullableReturn(final PsiReturnStatement statement) {
    if (myInNullableMethod || !myIsInMethod) return;
    if (myInNotNullMethod || mySuggestNullableAnnotations) {
      myNullableReturns.add(statement);
    }
  }

  public boolean problemsDetected() {
    final Pair<Set<Instruction>, Set<Instruction>> constConditions = getConstConditionalExpressions();
    return !constConditions.getFirst().isEmpty()
           || !constConditions.getSecond().isEmpty()
           || !myNPEInstructions.isEmpty()
           || !myCCEInstructions.isEmpty()
           || !getRedundantInstanceofs().isEmpty()
           || !myNullableArguments.isEmpty()
           || !myNullableAssignments.isEmpty()
           || !myNullableReturns.isEmpty()
           || !myUnboxedNullables.isEmpty();
  }
}