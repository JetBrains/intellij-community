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
import com.intellij.codeInspection.dataFlow.instructions.BinopInstruction;
import com.intellij.codeInspection.dataFlow.instructions.BranchingInstruction;
import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.EmptyStackException;
import java.util.HashSet;
import java.util.Set;

public class DataFlowRunner {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.DataFlowRunner");
  private static final long ourTimeLimit = 10000;

  private Instruction[] myInstructions;
  private final HashSet<Instruction> myNPEInstructions = new HashSet<Instruction>();
  private DfaVariableValue[] myFields;
  private final HashSet<Instruction> myCCEInstructions = new HashSet<Instruction>();
  private final HashSet<PsiExpression> myNullableArguments = new HashSet<PsiExpression>();
  private final HashSet<PsiExpression> myNullableAssignments = new HashSet<PsiExpression>();
  private final HashSet<PsiReturnStatement> myNullableReturns = new HashSet<PsiReturnStatement>();
  private DfaValueFactory myValueFactory;

  private final boolean mySuggestNullableAnnotations;
  private boolean myInNullableMethod = false;
  private boolean myInNotNullMethod = false;
  private boolean myIsInMethod = false;

  // Maximum allowed attempts to process instruction. Fail as too complex to process if certain instruction
  // is executed more than this limit times.
  public static final int MAX_STATES_PER_BRANCH = 300;
  private Set<PsiExpression> myUnboxedNullables = new THashSet<PsiExpression>();

  public Instruction getInstruction(int index) {
    return myInstructions[index];
  }

  public DataFlowRunner(boolean suggestNullableAnnotations) {
    mySuggestNullableAnnotations = suggestNullableAnnotations;
    myValueFactory = new DfaValueFactory();
  }

  public DfaValueFactory getFactory() {
    return myValueFactory;
  }

  public boolean analyzeMethod(PsiCodeBlock psiBlock) {
    myIsInMethod = psiBlock.getParent() instanceof PsiMethod;

    if (myIsInMethod) {
      PsiMethod method = (PsiMethod)psiBlock.getParent();
      myInNullableMethod = AnnotationUtil.isNullable(method);
      myInNotNullMethod = AnnotationUtil.isNotNull(method);
    }

    try {
      ControlFlow flow = new ControlFlowAnalyzer(myValueFactory).buildControlFlow(psiBlock);
      if (flow == null) return false;

      myInstructions = flow.getInstructions();
      myFields = flow.getFields();

      if (LOG.isDebugEnabled()) {
        for (int i = 0; i < myInstructions.length; i++) {
          Instruction instruction = myInstructions[i];
          LOG.debug(i + ": " + instruction.toString());
        }
      }

      int branchCount = 0;
      for (Instruction instruction : myInstructions) {
        if (instruction instanceof BranchingInstruction) branchCount++;
      }

      if (branchCount > 80) return false; // Do not even try. Definetly will out of time.

      final ArrayList<DfaInstructionState> queue = new ArrayList<DfaInstructionState>();
      final DfaMemoryState initialState = DfaMemoryStateImpl.createEmpty(myValueFactory);

      if (myIsInMethod) {
        PsiMethod method = (PsiMethod)psiBlock.getParent();
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        for (PsiParameter parameter : parameters) {
          if (AnnotationUtil.isNotNull(parameter)) {
            initialState.applyNotNull(myValueFactory.getVarFactory().create(parameter, false));
          }
        }
      }

      queue.add(new DfaInstructionState(myInstructions[0], initialState));
      long timeLimit = ourTimeLimit;
      final boolean unitTestMode = ApplicationManager.getApplication().isUnitTestMode();
      final long before = System.currentTimeMillis();
      while (!queue.isEmpty()) {
        if (!unitTestMode && System.currentTimeMillis() - before > timeLimit) return false;
        ProgressManager.getInstance().checkCanceled();

        DfaInstructionState instructionState = queue.remove(0);
        if (LOG.isDebugEnabled()) {
          LOG.debug(instructionState.toString());
        }

        Instruction instruction = instructionState.getInstruction();
        long distance = instructionState.getDistanceFromStart();

        if (instruction instanceof BranchingInstruction) {
          if (!instruction.setMemoryStateProcessed(instructionState.getMemoryState().createCopy())) {
            return false; // Too complex :(
          }
        }

        DfaInstructionState[] after = instruction.apply(this, instructionState.getMemoryState());
        if (after != null) {
          for (DfaInstructionState state : after) {
            Instruction nextInstruction = state.getInstruction();
            if (!(nextInstruction instanceof BranchingInstruction) || !nextInstruction.isMemoryStateProcessed(state.getMemoryState())) {
              state.setDistanceFromStart(distance + 1);
              queue.add(state);
            }
          }
        }
      }

      return true;
    }
    catch (ArrayIndexOutOfBoundsException e) {
      LOG.error(psiBlock.getText(), e); /* TODO[max] !!! hack (of 18186). Please fix in better times. */
      return false;
    }
    catch (EmptyStackException e) /* TODO[max] !!! hack (of 18186). Please fix in better times. */ {
      return false;
    }
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

  @NotNull public Set<Instruction> getRedundantInstanceofs() {
    HashSet<Instruction> result = new HashSet<Instruction>(1);
    for (Instruction instruction : myInstructions) {
      if (instruction instanceof BinopInstruction) {
        if (((BinopInstruction)instruction).isInstanceofRedundant()) {
          result.add(instruction);
        }
      }
    }

    return result;
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

  public DfaVariableValue[] getFields() {
    return myFields;
  }

  public Pair<Set<Instruction>,Set<Instruction>> getConstConditionalExpressions() {
    Set<Instruction> trueSet = new HashSet<Instruction>();
    Set<Instruction> falseSet = new HashSet<Instruction>();

    for (Instruction instruction : myInstructions) {
      if (instruction instanceof BranchingInstruction) {
        BranchingInstruction branchingInstruction = (BranchingInstruction)instruction;
        if (branchingInstruction.getPsiAnchor() != null && branchingInstruction.isConditionConst()) {
          if (!branchingInstruction.isTrueReachable()) {
            falseSet.add(branchingInstruction);
          }

          if (!branchingInstruction.isFalseReachable()) {
            trueSet.add(branchingInstruction);
          }
        }
      }
    }

    for (Instruction instruction : myInstructions) {
      if (instruction instanceof BranchingInstruction) {
        BranchingInstruction branchingInstruction = (BranchingInstruction)instruction;
        if (branchingInstruction.isTrueReachable()) {
          falseSet.remove(branchingInstruction);
        }
        if (branchingInstruction.isFalseReachable()) {
          trueSet.remove(branchingInstruction);
        }
      }
    }

    return Pair.create(trueSet, falseSet);
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
