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
import com.intellij.psi.*;

import java.util.ArrayList;
import java.util.EmptyStackException;
import java.util.HashSet;
import java.util.Set;

public class DataFlowRunner {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.DataFlowRunner");
  private static final long ourTimeLimit = 10000;

  private Instruction[] myInstructions;
  private final HashSet<Instruction> myNPEInstructions;
  private DfaVariableValue[] myFields;
  private final HashSet<Instruction> myCCEInstructions;
  private final HashSet<PsiExpression> myNullableArguments;
  private final HashSet<PsiExpression> myNullableAssignments;
  private final HashSet<PsiReturnStatement> myNullableReturns;
  private DfaValueFactory myValueFactory;

  private final boolean mySuggestNullableAnnotations;
  private boolean myInNullableMethod = false;
  private boolean myInNotNullMethod = false;
  private boolean myIsInMethod = false;

  // Maximum allowed attempts to process instruction. Fail as too complex to process if certain instruction
  // is executed more than this limit times.
  public static final int MAX_STATES_PER_BRANCH = 300;

  public Instruction getInstruction(int index) {
    return myInstructions[index];
  }

  public DataFlowRunner(boolean suggestNullableAnnotations) {
    mySuggestNullableAnnotations = suggestNullableAnnotations;
    myNPEInstructions = new HashSet<Instruction>();
    myCCEInstructions = new HashSet<Instruction>();
    myNullableArguments = new HashSet<PsiExpression>();
    myNullableAssignments = new HashSet<PsiExpression>();
    myNullableReturns = new HashSet<PsiReturnStatement>();
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
          LOG.debug("" + i + ": " + instruction.toString());
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

      final boolean unitTestMode = ApplicationManager.getApplication().isUnitTestMode();
      final long before = System.currentTimeMillis();
      while (queue.size() > 0) {
        if (!unitTestMode && System.currentTimeMillis() - before > ourTimeLimit) return false;
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

        DfaInstructionState[] after = instruction.apply(DataFlowRunner.this, instructionState.getMemoryState());
        if (after != null) {
          for (DfaInstructionState state : after) {
            Instruction nextInstruction = state.getInstruction();
            if (!(nextInstruction instanceof BranchingInstruction) ||
                !nextInstruction.isMemoryStateProcessed(state.getMemoryState())) {
              state.setDistanceFromStart(distance + 1);
              queue.add(state);
            }
          }
        }
      }

      return true;
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

  public Set<Instruction> getCCEInstructions() {
    return myCCEInstructions;
  }

  public Set<Instruction> getNPEInstructions() {
    return myNPEInstructions;
  }

  public Set<Instruction> getRedundantInstanceofs() {
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

  public HashSet<PsiReturnStatement> getNullableReturns() {
    return myNullableReturns;
  }

  public boolean isInNullableMethod() {
    return myInNullableMethod;
  }

  public boolean isInNotNullMethod() {
    return myInNotNullMethod;
  }

  public Set<PsiExpression> getNullableArguments() {
    return myNullableArguments;
  }

  public HashSet<PsiExpression> getNullableAssignments() {
    return myNullableAssignments;
  }

  public DfaVariableValue[] getFields() {
    return myFields;
  }

  public HashSet<Instruction>[] getConstConditionalExpressions() {
    HashSet<BranchingInstruction> trueSet = new HashSet<BranchingInstruction>();
    HashSet<BranchingInstruction> falseSet = new HashSet<BranchingInstruction>();

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

    return new HashSet[]{trueSet, falseSet};
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
    final HashSet[] constConditions = getConstConditionalExpressions();
    return constConditions[0].size() > 0 ||
        constConditions[1].size() > 0 ||
        myNPEInstructions.size() > 0 ||
        myCCEInstructions.size() > 0 ||
        getRedundantInstanceofs().size() > 0 ||
        myNullableArguments.size() > 0 ||
        myNullableAssignments.size() > 0 ||
        myNullableReturns.size() > 0;
  }
}
