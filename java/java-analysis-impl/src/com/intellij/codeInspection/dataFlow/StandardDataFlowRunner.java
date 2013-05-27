/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 28, 2002
 * Time: 10:16:39 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.instructions.InstanceofInstruction;
import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class StandardDataFlowRunner extends AnnotationsAwareDataFlowRunner {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.DataFlowRunner");

  private final Set<Instruction> myNPEInstructions = new HashSet<Instruction>();
  private final Set<Instruction> myCCEInstructions = new HashSet<Instruction>();
  private final Set<PsiExpression> myNullableArguments = new HashSet<PsiExpression>();
  private final Set<PsiExpression> myNullableArgumentsPassedToNonAnnotatedParam = new HashSet<PsiExpression>();
  private final Set<PsiExpression> myNullableAssignments = new HashSet<PsiExpression>();
  private final Set<PsiReturnStatement> myNullableReturns = new HashSet<PsiReturnStatement>();

  private final boolean mySuggestNullableAnnotations;
  private boolean myInNullableMethod = false;
  private boolean myInNotNullMethod = false;
  private boolean myIsInMethod = false;

  private final Set<PsiExpression> myUnboxedNullables = new THashSet<PsiExpression>();

  public StandardDataFlowRunner(boolean suggestNullableAnnotations) {
    mySuggestNullableAnnotations = suggestNullableAnnotations;
  }

  @Override
  protected Collection<DfaMemoryState> createInitialStates(@NotNull PsiElement psiBlock, InstructionVisitor visitor) {
    final Collection<DfaMemoryState> initialStates = super.createInitialStates(psiBlock, visitor);

    myIsInMethod = psiBlock.getParent() instanceof PsiMethod;
    if (myIsInMethod) {
      PsiMethod method = (PsiMethod)psiBlock.getParent();
      PsiType returnType = method.getReturnType();
      myInNullableMethod = NullableNotNullManager.isNullable(method) ||
                           returnType != null && returnType.equalsToText(CommonClassNames.JAVA_LANG_VOID);
      myInNotNullMethod = NullableNotNullManager.isNotNull(method);
    }

    myNPEInstructions.clear();
    myCCEInstructions.clear();
    myNullableArguments.clear();
    myNullableArgumentsPassedToNonAnnotatedParam.clear();
    myNullableAssignments.clear();
    myNullableReturns.clear();
    myUnboxedNullables.clear();

    return initialStates;
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

  public boolean isInNotNullMethod() {
    return myInNotNullMethod;
  }

  @NotNull public Set<PsiExpression> getNullableArguments() {
    return myNullableArguments;
  }

  public Set<PsiExpression> getNullableArgumentsPassedToNonAnnotatedParam() {
    return myNullableArgumentsPassedToNonAnnotatedParam;
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

  public void onPassingNullParameterToNonAnnotated(PsiExpression expr) {
    if (mySuggestNullableAnnotations) {
      myNullableArgumentsPassedToNonAnnotatedParam.add(expr);
    }
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

  public boolean problemsDetected(StandardInstructionVisitor visitor) {
    final Pair<Set<Instruction>, Set<Instruction>> constConditions = getConstConditionalExpressions();
    return !constConditions.getFirst().isEmpty()
           || !constConditions.getSecond().isEmpty()
           || !myNPEInstructions.isEmpty()
           || !myCCEInstructions.isEmpty()
           || !getRedundantInstanceofs(this, visitor).isEmpty()
           || !myNullableArguments.isEmpty()
           || !myNullableArgumentsPassedToNonAnnotatedParam.isEmpty()
           || !myNullableAssignments.isEmpty()
           || !myNullableReturns.isEmpty()
           || !myUnboxedNullables.isEmpty();
  }

  @NotNull public static Set<Instruction> getRedundantInstanceofs(final DataFlowRunner runner, StandardInstructionVisitor visitor) {
    HashSet<Instruction> result = new HashSet<Instruction>(1);
    for (Instruction instruction : runner.getInstructions()) {
      if (instruction instanceof InstanceofInstruction && visitor.isInstanceofRedundant((InstanceofInstruction)instruction)) {
        result.add(instruction);
      }
    }

    return result;
  }
}
