/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.controlFlow;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.IntArrayList;
import gnu.trove.THashSet;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ControlFlowUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.controlFlow.ControlFlowUtil");

  private static class SSAInstructionState implements Cloneable {
    private final int myWriteCount;
    private final int myInstructionIdx;

    public SSAInstructionState(int writeCount, int instructionIdx) {
      myWriteCount = writeCount;
      myInstructionIdx = instructionIdx;
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof SSAInstructionState)) return false;

      final SSAInstructionState ssaInstructionState = (SSAInstructionState)o;

      if (myInstructionIdx != ssaInstructionState.myInstructionIdx) return false;
      if (Math.min(2, myWriteCount) != Math.min(2, ssaInstructionState.myWriteCount)) return false;

      return true;
    }

    public int hashCode() {
      int result = Math.min(2, myWriteCount);
      result = 29 * result + myInstructionIdx;
      return result;
    }

    public int getWriteCount() {
      return myWriteCount;
    }

    public int getInstructionIdx() {
      return myInstructionIdx;
    }
  }

  public static List<PsiVariable> getSSAVariables(ControlFlow flow) {
    return getSSAVariables(flow, 0, flow.getSize(), false);
  }

  public static List<PsiVariable> getSSAVariables(ControlFlow flow, int from, int to,
                                                  boolean reportVarsIfNonInitializingPathExists) {
    List<Instruction> instructions = flow.getInstructions();
    Collection<PsiVariable> writtenVariables = getWrittenVariables(flow, from, to, false);
    ArrayList<PsiVariable> result = new ArrayList<PsiVariable>(1);

    variables:
    for (PsiVariable psiVariable : writtenVariables) {

      final List<SSAInstructionState> queue = new ArrayList<SSAInstructionState>();
      queue.add(new SSAInstructionState(0, from));
      Set<SSAInstructionState> processedStates = new THashSet<SSAInstructionState>();

      while (!queue.isEmpty()) {
        final SSAInstructionState state = queue.remove(0);
        if (state.getWriteCount() > 1) continue variables;
        if (!processedStates.contains(state)) {
          processedStates.add(state);
          int i = state.getInstructionIdx();
          if (i < to) {
            Instruction instruction = instructions.get(i);

            if (instruction instanceof ReturnInstruction) {
              int[] offsets = ((ReturnInstruction)instruction).getPossibleReturnOffsets();
              for (int offset : offsets) {
                queue.add(new SSAInstructionState(state.getWriteCount(), Math.min(offset, to)));
              }
            }
            else if (instruction instanceof GoToInstruction) {
              int nextOffset = ((GoToInstruction)instruction).offset;
              nextOffset = Math.min(nextOffset, to);
              queue.add(new SSAInstructionState(state.getWriteCount(), nextOffset));
            }
            else if (instruction instanceof ThrowToInstruction) {
              int nextOffset = ((ThrowToInstruction)instruction).offset;
              nextOffset = Math.min(nextOffset, to);
              queue.add(new SSAInstructionState(state.getWriteCount(), nextOffset));
            }
            else if (instruction instanceof ConditionalGoToInstruction) {
              int nextOffset = ((ConditionalGoToInstruction)instruction).offset;
              nextOffset = Math.min(nextOffset, to);
              queue.add(new SSAInstructionState(state.getWriteCount(), nextOffset));
              queue.add(new SSAInstructionState(state.getWriteCount(), i + 1));
            }
            else if (instruction instanceof ConditionalThrowToInstruction) {
              int nextOffset = ((ConditionalThrowToInstruction)instruction).offset;
              nextOffset = Math.min(nextOffset, to);
              queue.add(new SSAInstructionState(state.getWriteCount(), nextOffset));
              queue.add(new SSAInstructionState(state.getWriteCount(), i + 1));
            }
            else if (instruction instanceof WriteVariableInstruction) {
              WriteVariableInstruction write = (WriteVariableInstruction)instruction;
              queue.add(new SSAInstructionState(state.getWriteCount() + (write.variable == psiVariable ? 1 : 0), i + 1));
            }
            else if (instruction instanceof ReadVariableInstruction) {
              ReadVariableInstruction read = (ReadVariableInstruction)instruction;
              if (read.variable == psiVariable && state.getWriteCount() == 0) continue variables;
              queue.add(new SSAInstructionState(state.getWriteCount(), i + 1));
            }
            else {
              queue.add(new SSAInstructionState(state.getWriteCount(), i + 1));
            }
          }
          else if (!reportVarsIfNonInitializingPathExists && state.getWriteCount() == 0) continue variables;
        }
      }

      result.add(psiVariable);
    }

    return result;
  }

  private static boolean needVariableValueAt(final PsiVariable variable, final ControlFlow flow, final int offset) {
    InstructionClientVisitor<Boolean> visitor = new InstructionClientVisitor<Boolean>() {
      final boolean[] neededBelow = new boolean[flow.getSize() + 1];

      @Override
      public void procedureEntered(int startOffset, int endOffset) {
        for (int i = startOffset; i < endOffset; i++) neededBelow[i] = false;
      }

      @Override
      public void visitReadVariableInstruction(ReadVariableInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        boolean needed = neededBelow[nextOffset];
        if (instruction.variable.equals(variable)) {
          needed = true;
        }
        neededBelow[offset] |= needed;
      }

      @Override
      public void visitWriteVariableInstruction(WriteVariableInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        boolean needed = neededBelow[nextOffset];
        if (instruction.variable.equals(variable)) {
          needed = false;
        }
        neededBelow[offset] = needed;
      }

      @Override
      public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        boolean needed = neededBelow[nextOffset];
        neededBelow[offset] |= needed;
      }

      @Override
      public Boolean getResult() {
        return neededBelow[offset];
      }
    };
    depthFirstSearch(flow, visitor, offset, flow.getSize());
    return visitor.getResult().booleanValue();
  }

  public static Collection<PsiVariable> getWrittenVariables(ControlFlow flow, int start, int end, final boolean ignoreNotReachingWrites) {
    final HashSet<PsiVariable> set = new HashSet<PsiVariable>();
    getWrittenVariables(flow, start, end, ignoreNotReachingWrites, set);
    return set;
  }

  public static void getWrittenVariables(ControlFlow flow,
                                         int start,
                                         int end,
                                         final boolean ignoreNotReachingWrites,
                                         final Collection<PsiVariable> set) {
    List<Instruction> instructions = flow.getInstructions();
    for (int i = start; i < end; i++) {
      Instruction instruction = instructions.get(i);
      if (instruction instanceof WriteVariableInstruction && (!ignoreNotReachingWrites || isInstructionReachable(flow, end, i))) {
        set.add(((WriteVariableInstruction)instruction).variable);
      }
    }
  }

  public static List<PsiVariable> getUsedVariables(ControlFlow flow, int start, int end) {
    ArrayList<PsiVariable> array = new ArrayList<PsiVariable>();
    if (start < 0) return array;
    List<Instruction> instructions = flow.getInstructions();
    for (int i = start; i < end; i++) {
      Instruction instruction = instructions.get(i);
      if (instruction instanceof ReadVariableInstruction) {
        PsiVariable variable = ((ReadVariableInstruction)instruction).variable;
        if (!array.contains(variable)) {
          array.add(variable);
        }
      }
      else if (instruction instanceof WriteVariableInstruction) {
        PsiVariable variable = ((WriteVariableInstruction)instruction).variable;
        if (!array.contains(variable)) {
          array.add(variable);
        }
      }
    }
    return array;
  }

  public static List<PsiVariable> getInputVariables(ControlFlow flow, int start, int end) {
    List<PsiVariable> usedVariables = getUsedVariables(flow, start, end);
    ArrayList<PsiVariable> array = new ArrayList<PsiVariable>(usedVariables.size());
    for (PsiVariable variable : usedVariables) {
      if (needVariableValueAt(variable, flow, start)) {
        array.add(variable);
      }
    }
    return array;
  }

  public static PsiVariable[] getOutputVariables(ControlFlow flow, int start, int end, int[] exitPoints) {
    Collection<PsiVariable> writtenVariables = getWrittenVariables(flow, start, end, false);
    ArrayList<PsiVariable> array = new ArrayList<PsiVariable>();
    for (PsiVariable variable : writtenVariables) {
      for (int exitPoint : exitPoints) {
        if (needVariableValueAt(variable, flow, exitPoint)) {
          array.add(variable);
        }
      }
    }
    PsiVariable[] outputVariables = array.toArray(new PsiVariable[array.size()]);
    if (LOG.isDebugEnabled()) {
      LOG.debug("output variables:");
      for (PsiVariable variable : outputVariables) {
        LOG.debug("  " + variable.toString());
      }
    }
    return outputVariables;
  }

  public static Collection<PsiStatement> findExitPointsAndStatements(final ControlFlow flow, final int start, final int end, final IntArrayList exitPoints,
                                                                     final Class... classesFilter) {
    if (end == start) {
      exitPoints.add(end);
      return Collections.emptyList();
    }
    final Collection<PsiStatement> exitStatements = new THashSet<PsiStatement>();
    InstructionClientVisitor visitor = new InstructionClientVisitor() {
      @Override
      public void visitThrowToInstruction(ThrowToInstruction instruction, int offset, int nextOffset) {
        //[ven]This is a hack since Extract Method doesn't want to see throw's exit points
        processGotoStatement(classesFilter, exitStatements, findStatement(flow, offset));
      }

      @Override
      public void visitBranchingInstruction(BranchingInstruction instruction, int offset, int nextOffset) {
        processGoto(flow, start, end, exitPoints, exitStatements, instruction, classesFilter, findStatement(flow, offset));
      }

      // call/return do not incur exit points
      @Override
      public void visitReturnInstruction(ReturnInstruction instruction, int offset, int nextOffset) {
      }

      @Override
      public void visitCallInstruction(CallInstruction instruction, int offset, int nextOffset) {
      }

      @Override
      public void visitConditionalThrowToInstruction(ConditionalThrowToInstruction instruction, int offset, int nextOffset) {
        visitInstruction(instruction, offset, nextOffset);
      }

      @Override
      public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
        if (offset >= end - 1) {
          int exitOffset = end;
          exitOffset = promoteThroughGotoChain(flow, exitOffset);
          if (!exitPoints.contains(exitOffset)) {
            exitPoints.add(exitOffset);
          }
        }
      }

      @Override
      public Object getResult() {
        return null;
      }
    };
    depthFirstSearch(flow, visitor, start, end);
    return exitStatements;
  }

  private static void processGoto(ControlFlow flow, int start, int end,
                                  IntArrayList exitPoints,
                                  Collection<PsiStatement> exitStatements, BranchingInstruction instruction, Class[] classesFilter, final PsiStatement statement) {
    if (statement == null) return;
    int gotoOffset = instruction.offset;
    if (start > gotoOffset || gotoOffset >= end || isElementOfClass(statement, classesFilter)) {
      // process chain of goto's
      gotoOffset = promoteThroughGotoChain(flow, gotoOffset);

      if (!exitPoints.contains(gotoOffset) && (gotoOffset >= end || gotoOffset < start) && gotoOffset > 0) {
        exitPoints.add(gotoOffset);
      }
      if (gotoOffset >= end || gotoOffset < start) {
        processGotoStatement(classesFilter, exitStatements, statement);
      }
      else {
        boolean isReturn = instruction instanceof GoToInstruction && ((GoToInstruction)instruction).isReturn;
        final Instruction gotoInstruction = flow.getInstructions().get(gotoOffset);
        isReturn |= gotoInstruction instanceof GoToInstruction && ((GoToInstruction)gotoInstruction).isReturn;
        if (isReturn) {
          processGotoStatement(classesFilter, exitStatements, statement);
        }
      }
    }
  }

  private static void processGotoStatement(Class[] classesFilter, Collection<PsiStatement> exitStatements, PsiStatement statement) {
    if (statement != null && isElementOfClass(statement, classesFilter)) {
      exitStatements.add(statement);
    }
  }

  private static boolean isElementOfClass(PsiElement element, Class[] classesFilter) {
    if (classesFilter == null) return true;
    for (Class aClassesFilter : classesFilter) {
      if (ReflectionUtil.isAssignable(aClassesFilter, element.getClass())) {
        return true;
      }
    }
    return false;
  }

  private static int promoteThroughGotoChain(ControlFlow flow, int offset) {
    List<Instruction> instructions = flow.getInstructions();
    while (true) {
      if (offset >= instructions.size()) break;
      Instruction instruction = instructions.get(offset);
      if (!(instruction instanceof GoToInstruction) || ((GoToInstruction)instruction).isReturn) break;
      offset = ((BranchingInstruction)instruction).offset;
    }
    return offset;
  }

  public static final Class[] DEFAULT_EXIT_STATEMENTS_CLASSES =
    {PsiReturnStatement.class, PsiBreakStatement.class, PsiContinueStatement.class};

  private static PsiStatement findStatement(ControlFlow flow, int offset) {
    PsiElement element = flow.getElement(offset);
    return PsiTreeUtil.getParentOfType(element, PsiStatement.class, false);
  }

  @NotNull
  public static PsiElement findCodeFragment(@NotNull PsiElement element) {
    PsiElement codeFragment = element;
    PsiElement parent = codeFragment.getParent();
    while (parent != null) {
      if (parent instanceof PsiDirectory
          || parent instanceof PsiMethod
          || parent instanceof PsiField || parent instanceof PsiClassInitializer
          || parent instanceof DummyHolder
          || parent instanceof PsiLambdaExpression) {
        break;
      }
      codeFragment = parent;
      parent = parent.getParent();
    }
    return codeFragment;
  }

  private static boolean checkReferenceExpressionScope(final PsiReferenceExpression ref, @NotNull PsiElement targetClassMember) {
    final JavaResolveResult resolveResult = ref.advancedResolve(false);
    final PsiElement def = resolveResult.getElement();
    if (def != null) {
      PsiElement parent = def.getParent();
      PsiElement commonParent = parent == null ? null : PsiTreeUtil.findCommonParent(parent, targetClassMember);
      if (commonParent == null) {
        parent = resolveResult.getCurrentFileResolveScope();
      }
      if (parent instanceof PsiClass) {
        final PsiClass clss = (PsiClass)parent;
        if (PsiTreeUtil.isAncestor(targetClassMember, clss, false)) return false;
        PsiClass containingClass = PsiTreeUtil.getParentOfType(ref, PsiClass.class);
        while (containingClass != null) {
          if (containingClass.isInheritor(clss, true) &&
              PsiTreeUtil.isAncestor(targetClassMember, containingClass, false)) {
            return false;
          }
          containingClass = containingClass.getContainingClass();
        }
      }
    }

    return true;
  }

  /**
   * Checks possibility of extracting code fragment outside containing anonymous (local) class.
   * Also collects variables to be passed as additional parameters.
   *
   * @param array             Vector to collect variables to be passed as additional parameters
   * @param scope             scope to be scanned (part of code fragement to be extracted)
   * @param member            member containing the code to be extracted
   * @param targetClassMember member in target class containing code fragement
   * @return true if code fragement can be extracted outside
   */
  public static boolean collectOuterLocals(List<PsiVariable> array, PsiElement scope, PsiElement member,
                                           PsiElement targetClassMember) {
    if (scope instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression call = (PsiMethodCallExpression)scope;
      if (!checkReferenceExpressionScope(call.getMethodExpression(), targetClassMember)) {
        return false;
      }
    }
    else if (scope instanceof PsiReferenceExpression) {
      if (!checkReferenceExpressionScope((PsiReferenceExpression)scope, targetClassMember)) {
        return false;
      }
    }

    if (scope instanceof PsiJavaCodeReferenceElement) {

      final PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)scope;
      final JavaResolveResult result = ref.advancedResolve(false);
      final PsiElement refElement = result.getElement();

      if (refElement != null) {

        PsiElement parent = refElement.getParent();
        parent = parent != null ? PsiTreeUtil.findCommonParent(parent, member) : null;
        if (parent == null) {
          parent = result.getCurrentFileResolveScope();
        }

        if (parent != null && !member.equals(parent)) { // not local in member
          parent = PsiTreeUtil.findCommonParent(parent, targetClassMember);
          if (targetClassMember.equals(parent)) { //something in anonymous class
            if (refElement instanceof PsiVariable) {
              if (scope instanceof PsiReferenceExpression &&
                  PsiUtil.isAccessedForWriting((PsiReferenceExpression)scope)) {
                return false;
              }
              PsiVariable variable = (PsiVariable)refElement;
              if (!array.contains(variable)) {
                array.add(variable);
              }
            }
            else {
              return false;
            }
          }
        }
      }
    }
    else if (scope instanceof PsiThisExpression) {
      PsiJavaCodeReferenceElement qualifier = ((PsiThisExpression)scope).getQualifier();
      if (qualifier == null) {
        return false;
      }
    }
    else if (scope instanceof PsiSuperExpression) {
      if (((PsiSuperExpression)scope).getQualifier() == null) {
        return false;
      }
    }

    for (PsiElement child = scope.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (!collectOuterLocals(array, child, member, targetClassMember)) return false;
    }
    return true;
  }


  /**
   * @return true if each control flow path results in return statement or exception thrown
   */
  public static boolean returnPresent(final ControlFlow flow) {
    InstructionClientVisitor<Boolean> visitor = new ReturnPresentClientVisitor(flow);

    depthFirstSearch(flow, visitor);
    return visitor.getResult().booleanValue();
  }

  public static boolean processReturns(final ControlFlow flow, final ReturnStatementsVisitor afterVisitor) throws IncorrectOperationException {
    final ConvertReturnClientVisitor instructionsVisitor = new ConvertReturnClientVisitor(flow, afterVisitor);

    depthFirstSearch(flow, instructionsVisitor);

    instructionsVisitor.afterProcessing();
    return instructionsVisitor.getResult().booleanValue();
  }

  private static class ConvertReturnClientVisitor extends ReturnPresentClientVisitor {
    private final List<PsiReturnStatement> myAffectedReturns;
    private final ReturnStatementsVisitor myVisitor;

    ConvertReturnClientVisitor(final ControlFlow flow, final ReturnStatementsVisitor visitor) {
      super(flow);
      myAffectedReturns = new ArrayList<PsiReturnStatement>();
      myVisitor = visitor;
    }

    @Override
    public void visitGoToInstruction(final GoToInstruction instruction, final int offset, final int nextOffset) {
      super.visitGoToInstruction(instruction, offset, nextOffset);

      if (instruction.isReturn) {
        final PsiElement element = myFlow.getElement(offset);
        if (element instanceof PsiReturnStatement) {
          final PsiReturnStatement returnStatement = (PsiReturnStatement)element;
          myAffectedReturns.add(returnStatement);
        }
      }
    }

    public void afterProcessing() throws IncorrectOperationException {
      myVisitor.visit(myAffectedReturns);
    }
  }

  private static class ReturnPresentClientVisitor extends InstructionClientVisitor<Boolean> {
    // false if control flow at this offset terminates either by return called or exception thrown
    private final boolean[] isNormalCompletion;
    protected final ControlFlow myFlow;

    public ReturnPresentClientVisitor(ControlFlow flow) {
      myFlow = flow;
      isNormalCompletion = new boolean[myFlow.getSize() + 1];
      isNormalCompletion[myFlow.getSize()] = true;
    }

    @Override
    public void visitConditionalThrowToInstruction(ConditionalThrowToInstruction instruction, int offset, int nextOffset) {
      if (nextOffset > myFlow.getSize()) nextOffset = myFlow.getSize();
      boolean isNormal = instruction.offset == nextOffset && nextOffset != offset + 1 ?
                         !isLeaf(nextOffset) && isNormalCompletion[nextOffset] :
                         isLeaf(nextOffset) || isNormalCompletion[nextOffset];

      isNormalCompletion[offset] |= isNormal;
    }

    @Override
    public void visitThrowToInstruction(ThrowToInstruction instruction, int offset, int nextOffset) {
      if (nextOffset > myFlow.getSize()) nextOffset = myFlow.getSize();
      isNormalCompletion[offset] |= !isLeaf(nextOffset) && isNormalCompletion[nextOffset];
    }

    @Override
    public void visitGoToInstruction(GoToInstruction instruction, int offset, int nextOffset) {
      if (nextOffset > myFlow.getSize()) nextOffset = myFlow.getSize();
      isNormalCompletion[offset] |= !instruction.isReturn && isNormalCompletion[nextOffset];
    }

    @Override
    public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
      if (nextOffset > myFlow.getSize()) nextOffset = myFlow.getSize();

      boolean isNormal = isLeaf(nextOffset) || isNormalCompletion[nextOffset];
      isNormalCompletion[offset] |= isNormal;
    }

    @Override
    public Boolean getResult() {
      return !isNormalCompletion[0];
    }
  }

  public static boolean returnPresentBetween(final ControlFlow flow, final int startOffset, final int endOffset) {
    class MyVisitor extends InstructionClientVisitor<Boolean> {
      // false if control flow at this offset terminates either by return called or exception thrown
      final boolean[] isNormalCompletion = new boolean[flow.getSize() + 1];

      public MyVisitor() {
        int i;
        final int length = flow.getSize();
        for (i = 0; i < startOffset; i++) {
          isNormalCompletion[i] = true;
        }
        for (i = endOffset; i <= length; i++) {
          isNormalCompletion[i] = true;
        }
      }

      @Override
      public void visitConditionalThrowToInstruction(ConditionalThrowToInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        if (offset > endOffset) return;
        int throwToOffset = instruction.offset;
        boolean isNormal;
        if (throwToOffset == nextOffset) {
          if (throwToOffset <= endOffset) {
            isNormal = !isLeaf(nextOffset) && isNormalCompletion[nextOffset];
          }
          else {
            return;
          }
        }
        else {
          isNormal = isLeaf(nextOffset) || isNormalCompletion[nextOffset];
        }
        isNormalCompletion[offset] |= isNormal;
      }

      @Override
      public void visitThrowToInstruction(ThrowToInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        if (offset > endOffset) return;
        if (nextOffset <= endOffset) {
          boolean isNormal = !isLeaf(nextOffset) && isNormalCompletion[nextOffset];
          isNormalCompletion[offset] |= isNormal;
        }
      }

      @Override
      public void visitCallInstruction(CallInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        if (offset > endOffset) return;
        if (nextOffset > endOffset && nextOffset != offset + 1) {
          return;
        }
        boolean isNormal = isNormalCompletion[nextOffset];
        isNormalCompletion[offset] |= isNormal;
      }

      @Override
      public void visitGoToInstruction(GoToInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        if (offset > endOffset) return;
        boolean isRethrowFromFinally = instruction instanceof ReturnInstruction && ((ReturnInstruction)instruction).isRethrowFromFinally();
        boolean isNormal = !instruction.isReturn && isNormalCompletion[nextOffset] && !isRethrowFromFinally;
        isNormalCompletion[offset] |= isNormal;
      }

      @Override
      public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        if (offset > endOffset) return;
        final boolean isNormal = isLeaf(nextOffset) || isNormalCompletion[nextOffset];
        isNormalCompletion[offset] |= isNormal;
      }

      @Override
      public Boolean getResult() {
        return !isNormalCompletion[startOffset];
      }
    }
    final MyVisitor visitor = new MyVisitor();
    depthFirstSearch(flow, visitor, startOffset, endOffset);
    return visitor.getResult().booleanValue();
  }

  public static Object[] getAllWorldProblemsAtOnce(final ControlFlow flow) {
    InstructionClientVisitor[] visitors = {
      new ReturnPresentClientVisitor(flow),
      new UnreachableStatementClientVisitor(flow),
      new ReadBeforeWriteClientVisitor(flow, true),
      new InitializedTwiceClientVisitor(flow, 0),
    };
    CompositeInstructionClientVisitor visitor = new CompositeInstructionClientVisitor(visitors);
    depthFirstSearch(flow, visitor);
    return visitor.getResult();
  }

  /**
   * returns true iff exists controlflow path completing normally, i.e. not resulting in return,break,continue or exception thrown.
   * In other words, if we add instruction after controlflow specified, it should be reachable
   */
  public static boolean canCompleteNormally(final ControlFlow flow, final int startOffset, final int endOffset) {
    class MyVisitor extends InstructionClientVisitor<Boolean> {
      // false if control flow at this offset terminates abruptly
      final boolean[] canCompleteNormally = new boolean[flow.getSize() + 1];

      @Override
      public void visitConditionalGoToInstruction(ConditionalGoToInstruction instruction, int offset, int nextOffset) {
        checkInstruction(offset, nextOffset, false);
      }

      @Override
      public void visitGoToInstruction(GoToInstruction instruction, int offset, int nextOffset) {
        checkInstruction(offset, nextOffset, instruction.isReturn);
      }

      private void checkInstruction(int offset, int nextOffset, boolean isReturn) {
        if (offset > endOffset) return;
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        boolean isNormal = nextOffset <= endOffset && !isReturn && (nextOffset == endOffset || canCompleteNormally[nextOffset]);
        if (isNormal && nextOffset == endOffset) {
          PsiElement element = flow.getElement(offset);
          if (element instanceof PsiBreakStatement || element instanceof PsiContinueStatement) {
            isNormal = false;
          }
        }
        canCompleteNormally[offset] |= isNormal;
      }

      @Override
      public void visitConditionalThrowToInstruction(ConditionalThrowToInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        if (offset > endOffset) return;
        int throwToOffset = instruction.offset;
        boolean isNormal = false;
        if (throwToOffset == nextOffset) {

          if (nextOffset == endOffset) {
            int lastOffset = endOffset - 1;
            Instruction lastInstruction = flow.getInstructions().get(lastOffset);
            while (lastInstruction instanceof GoToInstruction &&
                ((GoToInstruction)lastInstruction).role == BranchingInstruction.Role.END &&
                !((GoToInstruction)lastInstruction).isReturn) {
              if (((GoToInstruction)lastInstruction).offset == startOffset) {
                lastOffset = -1;
                break;
              } 
              else {
                lastOffset--;
                if (lastOffset < 0) {
                  break;
                }
                lastInstruction = flow.getInstructions().get(lastOffset);
              }
            }

            if (lastOffset >= 0) {
              isNormal = !(lastInstruction instanceof GoToInstruction && ((GoToInstruction)lastInstruction).isReturn) &&
                         !(lastInstruction instanceof ThrowToInstruction);
            }
          }

          isNormal |= throwToOffset <= endOffset && !isLeaf(nextOffset) && canCompleteNormally[nextOffset];
        }
        else {
          isNormal = canCompleteNormally[nextOffset];
        }
        canCompleteNormally[offset] |= isNormal;
      }

      @Override
      public void visitThrowToInstruction(ThrowToInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        if (offset > endOffset) return;
        if (nextOffset <= endOffset) {
          boolean isNormal = !isLeaf(nextOffset) && canCompleteNormally[nextOffset];
          canCompleteNormally[offset] |= isNormal;
        }
      }

      @Override
      public void visitCallInstruction(CallInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        if (offset > endOffset) return;
        if (nextOffset > endOffset && nextOffset != offset + 1) {
          return;
        }
        boolean isNormal = canCompleteNormally[nextOffset];
        canCompleteNormally[offset] |= isNormal;
      }

      @Override
      public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
        checkInstruction(offset, nextOffset, false);
      }

      @Override
      public Boolean getResult() {
        return canCompleteNormally[startOffset];
      }
    }
    final MyVisitor visitor = new MyVisitor();
    depthFirstSearch(flow, visitor, startOffset, endOffset);
    return visitor.getResult().booleanValue();
  }

  /**
   * @return any unreachable statement or null
   */
  public static PsiElement getUnreachableStatement(final ControlFlow flow) {
    final InstructionClientVisitor<PsiElement> visitor = new UnreachableStatementClientVisitor(flow);
    depthFirstSearch(flow, visitor);
    return visitor.getResult();
  }

  private static class UnreachableStatementClientVisitor extends InstructionClientVisitor<PsiElement> {
    private final ControlFlow myFlow;

    public UnreachableStatementClientVisitor(ControlFlow flow) {
      myFlow = flow;
    }

    @Override
    public PsiElement getResult() {
      for (int i = 0; i < processedInstructions.length; i++) {
        if (!processedInstructions[i]) {
          PsiElement element = myFlow.getElement(i);
          if (element == null || !PsiUtil.isStatement(element)) continue;
          if (element.getParent() instanceof PsiExpression) continue;

          // ignore for(;;) statement unreachable update
          while (element instanceof PsiExpression) {
            element = element.getParent();
          }
          if (element instanceof PsiStatement
              && element.getParent() instanceof PsiForStatement
              && element == ((PsiForStatement)element.getParent()).getUpdate()) {
            continue;
          }
          //filter out generated stmts
          final int endOffset = myFlow.getEndOffset(element);
          if (endOffset != i + 1) continue;
          final int startOffset = myFlow.getStartOffset(element);
          // this offset actually is a part of reachable statement
          if (0 <= startOffset && startOffset < processedInstructions.length && processedInstructions[startOffset]) continue;
          return element;
        }
      }
      return null;
    }
  }

  private static PsiReferenceExpression getEnclosingReferenceExpression(PsiElement element, PsiVariable variable) {
    final PsiReferenceExpression reference = findReferenceTo(element, variable);
    if (reference != null) return reference;
    while (element != null) {
      if (element instanceof PsiReferenceExpression) {
        return (PsiReferenceExpression)element;
      }
      else if (element instanceof PsiMethod || element instanceof PsiClass) {
        return null;
      }
      element = element.getParent();
    }
    return null;
  }

  private static PsiReferenceExpression findReferenceTo(PsiElement element, PsiVariable variable) {
    if (element instanceof PsiReferenceExpression
        && !((PsiReferenceExpression)element).isQualified()
        && ((PsiReferenceExpression)element).resolve() == variable) {
      return (PsiReferenceExpression)element;
    }
    final PsiElement[] children = element.getChildren();
    for (PsiElement child : children) {
      final PsiReferenceExpression reference = findReferenceTo(child, variable);
      if (reference != null) return reference;
    }
    return null;
  }


  public static boolean isVariableDefinitelyAssigned(@NotNull final PsiVariable variable, @NotNull final ControlFlow flow) {
    class MyVisitor extends InstructionClientVisitor<Boolean> {
      // true if from this point below there may be branch with no variable assignment
      final boolean[] maybeUnassigned = new boolean[flow.getSize() + 1];

      {
        maybeUnassigned[maybeUnassigned.length - 1] = true;
      }

      @Override
      public void visitWriteVariableInstruction(WriteVariableInstruction instruction, int offset, int nextOffset) {
        if (instruction.variable == variable) {
          maybeUnassigned[offset] = false;
        }
        else {
          visitInstruction(instruction, offset, nextOffset);
        }
      }

      @Override
      public void visitConditionalThrowToInstruction(ConditionalThrowToInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        boolean unassigned = offset == flow.getSize() - 1
                             || !isLeaf(nextOffset) && maybeUnassigned[nextOffset];

        maybeUnassigned[offset] |= unassigned;
      }

      @Override
      public void visitCallInstruction(CallInstruction instruction, int offset, int nextOffset) {
        visitInstruction(instruction, offset, nextOffset);
        // clear return statements after procedure as well
        for (int i = instruction.procBegin; i < instruction.procEnd + 3; i++) {
          maybeUnassigned[i] = false;
        }
      }

      @Override
      public void visitThrowToInstruction(ThrowToInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        boolean unassigned = !isLeaf(nextOffset) && maybeUnassigned[nextOffset];
        maybeUnassigned[offset] |= unassigned;
      }

      @Override
      public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();

        boolean unassigned = isLeaf(nextOffset) || maybeUnassigned[nextOffset];
        maybeUnassigned[offset] |= unassigned;
      }

      @Override
      public Boolean getResult() {
        final int variableDeclarationOffset = flow.getStartOffset(variable.getParent());
        return !maybeUnassigned[variableDeclarationOffset > -1 ? variableDeclarationOffset : 0];
      }
    }
    if (flow.getSize() == 0) return false;
    MyVisitor visitor = new MyVisitor();
    depthFirstSearch(flow, visitor);
    return visitor.getResult().booleanValue();
  }

  public static boolean isVariableDefinitelyNotAssigned(final PsiVariable variable, final ControlFlow flow) {
    class MyVisitor extends InstructionClientVisitor<Boolean> {
      // true if from this point below there may be branch with variable assignment
      final boolean[] maybeAssigned = new boolean[flow.getSize() + 1];

      @Override
      public void visitWriteVariableInstruction(WriteVariableInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        boolean assigned = instruction.variable == variable || maybeAssigned[nextOffset];
        maybeAssigned[offset] |= assigned;
      }

      @Override
      public void visitThrowToInstruction(ThrowToInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        boolean assigned = !isLeaf(nextOffset) && maybeAssigned[nextOffset];
        maybeAssigned[offset] |= assigned;
      }

      @Override
      public void visitConditionalThrowToInstruction(ConditionalThrowToInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        int throwToOffset = instruction.offset;
        boolean assigned = throwToOffset == nextOffset ? !isLeaf(nextOffset) && maybeAssigned[nextOffset] :
                           maybeAssigned[nextOffset];
        maybeAssigned[offset] |= assigned;
      }

      @Override
      public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();

        boolean assigned = maybeAssigned[nextOffset];

        maybeAssigned[offset] |= assigned;
      }

      @Override
      public Boolean getResult() {
        return !maybeAssigned[0];
      }
    }
    MyVisitor visitor = new MyVisitor();
    depthFirstSearch(flow, visitor);
    return visitor.getResult().booleanValue();
  }

  /**
   * @return min offset after sourceOffset which is definitely reachable from all references
   */
  public static int getMinDefinitelyReachedOffset(final ControlFlow flow, final int sourceOffset,
                                                  final List references) {
    class MyVisitor extends InstructionClientVisitor<Integer> {
      // set of exit posint reached from this offset
      final TIntHashSet[] exitPoints = new TIntHashSet[flow.getSize()];

      @Override
      public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();

        if (exitPoints[offset] == null) {
          exitPoints[offset] = new TIntHashSet();
        }
        if (isLeaf(nextOffset)) {
          exitPoints[offset].add(offset);
        }
        else if (exitPoints[nextOffset] != null) {
          exitPoints[offset].addAll(exitPoints[nextOffset].toArray());
        }
      }

      @Override
      public Integer getResult() {
        int minOffset = flow.getSize();
        int maxExitPoints = 0;
        nextOffset:
        for (int i = sourceOffset; i < exitPoints.length; i++) {
          TIntHashSet exitPointSet = exitPoints[i];
          final int size = exitPointSet == null ? 0 : exitPointSet.size();
          if (size > maxExitPoints) {
            // this offset should be reachable from all other references
            for (Object reference : references) {
              PsiElement element = (PsiElement)reference;
              final PsiElement statement = PsiUtil.getEnclosingStatement(element);
              if (statement == null) continue;
              final int endOffset = flow.getEndOffset(statement);
              if (endOffset == -1) continue;
              if (i != endOffset && !isInstructionReachable(flow, i, endOffset)) continue nextOffset;
            }
            minOffset = i;
            maxExitPoints = size;
          }
        }
        return minOffset;
      }
    }
    MyVisitor visitor = new MyVisitor();
    depthFirstSearch(flow, visitor);
    return visitor.getResult().intValue();
  }

  private static void depthFirstSearch(ControlFlow flow, InstructionClientVisitor visitor) {
    depthFirstSearch(flow, visitor, 0, flow.getSize());
  }

  private static void depthFirstSearch(ControlFlow flow, InstructionClientVisitor visitor, int startOffset, int endOffset) {
    visitor.processedInstructions = new boolean[endOffset];
    internalDepthFirstSearch(flow.getInstructions(), visitor, startOffset, endOffset);
  }

  private static void internalDepthFirstSearch(final List<Instruction> instructions, final InstructionClientVisitor clientVisitor, int offset, int endOffset) {
    final IntArrayList oldOffsets = new IntArrayList(instructions.size() / 2);
    final IntArrayList newOffsets = new IntArrayList(instructions.size() / 2);

    oldOffsets.add(offset);
    newOffsets.add(-1);

    // we can change instruction internal state here (e.g. CallInstruction.stack)
    synchronized (instructions) {
      final IntArrayList currentProcedureReturnOffsets = new IntArrayList();
      ControlFlowInstructionVisitor getNextOffsetVisitor = new ControlFlowInstructionVisitor() {
        @Override
        public void visitCallInstruction(CallInstruction instruction, int offset, int nextOffset) {
          instruction.execute(offset + 1);
          int newOffset = instruction.offset;
          // 'procedure' pointed by call instruction should be processed regardless of whether it was already visited or not
          // clear procedure text and return instructions aftewards
          int i;
          for (i = instruction.procBegin; i < instruction.procEnd || instructions.get(i) instanceof ReturnInstruction; i++) {
            clientVisitor.processedInstructions[i] = false;
          }
          clientVisitor.procedureEntered(instruction.procBegin, i);
          oldOffsets.add(offset);
          newOffsets.add(newOffset);

          oldOffsets.add(newOffset);
          newOffsets.add(-1);

          currentProcedureReturnOffsets.add(offset + 1);
        }

        @Override
        public void visitReturnInstruction(ReturnInstruction instruction, int offset, int nextOffset) {
          int newOffset = instruction.execute(false);
          if (newOffset != -1) {
            oldOffsets.add(offset);
            newOffsets.add(newOffset);

            oldOffsets.add(newOffset);
            newOffsets.add(-1);
          }
        }

        @Override
        public void visitBranchingInstruction(BranchingInstruction instruction, int offset, int nextOffset) {
          int newOffset = instruction.offset;
          oldOffsets.add(offset);
          newOffsets.add(newOffset);

          oldOffsets.add(newOffset);
          newOffsets.add(-1);
        }

        @Override
        public void visitConditionalBranchingInstruction(ConditionalBranchingInstruction instruction, int offset, int nextOffset) {
          int newOffset = instruction.offset;

          oldOffsets.add(offset);
          newOffsets.add(newOffset);

          oldOffsets.add(offset);
          newOffsets.add(offset + 1);

          oldOffsets.add(newOffset);
          newOffsets.add(-1);

          oldOffsets.add(offset + 1);
          newOffsets.add(-1);
        }

        @Override
        public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
          int newOffset = offset + 1;
          oldOffsets.add(offset);
          newOffsets.add(newOffset);

          oldOffsets.add(newOffset);
          newOffsets.add(-1);
        }
      };
      while (!oldOffsets.isEmpty()) {
        offset = oldOffsets.remove(oldOffsets.size() - 1);
        int newOffset = newOffsets.remove(newOffsets.size() - 1);

        if (offset >= endOffset) {
          continue;
        }
        Instruction instruction = instructions.get(offset);

        if (clientVisitor.processedInstructions[offset]) {
          if (newOffset != -1) {
            instruction.accept(clientVisitor, offset, newOffset);
          }
          // when traversing call instruction, we have traversed all procedure control flows, so pop return address
          if (!currentProcedureReturnOffsets.isEmpty() && currentProcedureReturnOffsets.get(currentProcedureReturnOffsets.size() - 1) - 1 == offset) {
            currentProcedureReturnOffsets.remove(currentProcedureReturnOffsets.size() - 1);
          }
          continue;
        }
        if (!currentProcedureReturnOffsets.isEmpty()) {
          int returnOffset = currentProcedureReturnOffsets.get(currentProcedureReturnOffsets.size() - 1);
          CallInstruction callInstruction = (CallInstruction)instructions.get(returnOffset - 1);
          // check if we inside procedure but 'return offset' stack is empty, so
          // we should push back to 'return offset' stack
          synchronized (callInstruction.stack) {
            if (callInstruction.procBegin <= offset && offset < callInstruction.procEnd + 2
                && (callInstruction.stack.size() == 0 || callInstruction.stack.peekReturnOffset() != returnOffset)) {
              callInstruction.stack.push(returnOffset, callInstruction);
            }
          }
        }

        clientVisitor.processedInstructions[offset] = true;
        instruction.accept(getNextOffsetVisitor, offset, newOffset);
      }
    }
  }

  private static boolean isInsideReturnStatement(PsiElement element) {
    while (element instanceof PsiExpression) element = element.getParent();
    return element instanceof PsiReturnStatement;
  }

  private static class CopyOnWriteList {
    private final List<VariableInfo> list;

    public CopyOnWriteList add(VariableInfo value) {
      CopyOnWriteList newList = new CopyOnWriteList();
      List<VariableInfo> list = getList();
      for (final VariableInfo variableInfo : list) {
        if (!value.equals(variableInfo)) {
          newList.list.add(variableInfo);
        }
      }
      newList.list.add(value);
      return newList;
    }

    public CopyOnWriteList remove(VariableInfo value) {
      CopyOnWriteList newList = new CopyOnWriteList();
      List<VariableInfo> list = getList();
      for (final VariableInfo variableInfo : list) {
        if (!value.equals(variableInfo)) {
          newList.list.add(variableInfo);
        }
      }
      return newList;
    }

    @NotNull
    public List<VariableInfo> getList() {
      return list;
    }

    public CopyOnWriteList() {
      this(Collections.<VariableInfo>emptyList());
    }

    public CopyOnWriteList(VariableInfo... infos) {
      this(Arrays.asList(infos));
    }

    public CopyOnWriteList(Collection<VariableInfo> infos) {
      list = new LinkedList<VariableInfo>(infos);
    }

    public CopyOnWriteList addAll(CopyOnWriteList addList) {
      CopyOnWriteList newList = new CopyOnWriteList();
      List<VariableInfo> list = getList();
      for (final VariableInfo variableInfo : list) {
        newList.list.add(variableInfo);
      }
      List<VariableInfo> toAdd = addList.getList();
      for (final VariableInfo variableInfo : toAdd) {
        if (!newList.list.contains(variableInfo)) {
          // no copy
          newList.list.add(variableInfo);
        }
      }
      return newList;
    }

    public static CopyOnWriteList add(@Nullable CopyOnWriteList list, @NotNull VariableInfo value) {
      return list == null ? new CopyOnWriteList(value) : list.add(value);
    }
  }

  public static class VariableInfo {
    private final PsiVariable variable;
    public final PsiElement expression;

    public VariableInfo(PsiVariable variable, PsiElement expression) {
      this.variable = variable;
      this.expression = expression;
    }

    public boolean equals(Object o) {
      return this == o || o instanceof VariableInfo && variable.equals(((VariableInfo)o).variable);
    }

    public int hashCode() {
      return variable.hashCode();
    }
  }

  private static void merge(int offset, CopyOnWriteList source, CopyOnWriteList[] target) {
    if (source != null) {
      CopyOnWriteList existing = target[offset];
      target[offset] = existing == null ? source : existing.addAll(source);
    }
  }

  /**
   * @return list of PsiReferenceExpression of usages of non-initialized local variables
   */
  public static List<PsiReferenceExpression> getReadBeforeWriteLocals(ControlFlow flow) {
    final InstructionClientVisitor<List<PsiReferenceExpression>> visitor = new ReadBeforeWriteClientVisitor(flow, true);
    depthFirstSearch(flow, visitor);
    return visitor.getResult();
  }

  public static List<PsiReferenceExpression> getReadBeforeWrite(ControlFlow flow) {
    final InstructionClientVisitor<List<PsiReferenceExpression>> visitor = new ReadBeforeWriteClientVisitor(flow, false);
    depthFirstSearch(flow, visitor);
    return visitor.getResult();
  }

  private static class ReadBeforeWriteClientVisitor extends InstructionClientVisitor<List<PsiReferenceExpression>> {
    // map of variable->PsiReferenceExpressions for all read before written variables for this point and below in control flow
    private final CopyOnWriteList[] readVariables;
    private final ControlFlow myFlow;
    private final boolean localVariablesOnly;

    public ReadBeforeWriteClientVisitor(ControlFlow flow, boolean localVariablesOnly) {
      myFlow = flow;
      this.localVariablesOnly = localVariablesOnly;
      readVariables = new CopyOnWriteList[myFlow.getSize() + 1];
    }

    @Override
    public void visitReadVariableInstruction(ReadVariableInstruction instruction, int offset, int nextOffset) {
      CopyOnWriteList readVars = readVariables[Math.min(nextOffset, myFlow.getSize())];
      final PsiVariable variable = instruction.variable;
      if (!localVariablesOnly || !isMethodParameter(variable)) {
        final PsiReferenceExpression expression = getEnclosingReferenceExpression(myFlow.getElement(offset), variable);
        if (expression != null) {
          readVars = CopyOnWriteList.add(readVars, new VariableInfo(variable, expression));
        }
      }
      merge(offset, readVars, readVariables);
    }

    @Override
    public void visitWriteVariableInstruction(WriteVariableInstruction instruction, int offset, int nextOffset) {
      CopyOnWriteList readVars = readVariables[Math.min(nextOffset, myFlow.getSize())];
      if (readVars == null) return;

      final PsiVariable variable = instruction.variable;
      if (!localVariablesOnly || !isMethodParameter(variable)) {
        readVars = readVars.remove(new VariableInfo(variable, null));
      }
      merge(offset, readVars, readVariables);
    }

    private static boolean isMethodParameter(@NotNull PsiVariable variable) {
      if (variable instanceof PsiParameter) {
        final PsiParameter parameter = (PsiParameter)variable;
        return !(parameter.getDeclarationScope() instanceof PsiForeachStatement);
      }
      return false;
    }

    @Override
    public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
      merge(offset, readVariables[Math.min(nextOffset, myFlow.getSize())], readVariables);
    }

    @Override
    public void visitCallInstruction(CallInstruction instruction, int offset, int nextOffset) {
      visitInstruction(instruction, offset, nextOffset);
      for (int i = instruction.procBegin; i <= instruction.procEnd; i++) {
        readVariables[i] = null;
      }
    }

    @Override
    public List<PsiReferenceExpression> getResult() {
      final CopyOnWriteList topReadVariables = readVariables[0];
      if (topReadVariables == null) return Collections.emptyList();

      final List<PsiReferenceExpression> result = new ArrayList<PsiReferenceExpression>();
      List<VariableInfo> list = topReadVariables.getList();
      for (final VariableInfo variableInfo : list) {
        result.add((PsiReferenceExpression)variableInfo.expression);
      }
      return result;
    }
  }

  public static final int NORMAL_COMPLETION_REASON = 1;
  public static final int RETURN_COMPLETION_REASON = 2;

  /**
   * return reasons.normalCompletion when  block can complete normally
   * reasons.returnCalled when  block can complete abruptly because of return statement executed
   */
  public static int getCompletionReasons(final ControlFlow flow, final int offset, final int endOffset) {
    class MyVisitor extends InstructionClientVisitor<Integer> {
      final boolean[] normalCompletion = new boolean[endOffset];
      final boolean[] returnCalled = new boolean[endOffset];

      @Override
      public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
        boolean ret = nextOffset < endOffset && returnCalled[nextOffset];
        boolean normal = nextOffset < endOffset && normalCompletion[nextOffset];
        final PsiElement element = flow.getElement(offset);
        boolean goToReturn = instruction instanceof GoToInstruction && ((GoToInstruction)instruction).isReturn;
        if (goToReturn || isInsideReturnStatement(element)) {
          ret = true;
        }
        else if (instruction instanceof ConditionalThrowToInstruction) {
          final int throwOffset = ((ConditionalThrowToInstruction)instruction).offset;
          boolean normalWhenThrow = throwOffset < endOffset && normalCompletion[throwOffset];
          boolean normalWhenNotThrow = offset == endOffset - 1 || normalCompletion[offset + 1];
          normal = normalWhenThrow || normalWhenNotThrow;
        }
        else if (!(instruction instanceof ThrowToInstruction) && nextOffset >= endOffset) {
          normal = true;
        }
        returnCalled[offset] |= ret;
        normalCompletion[offset] |= normal;
      }

      @Override
      public Integer getResult() {
        return (returnCalled[offset] ? RETURN_COMPLETION_REASON : 0) | (normalCompletion[offset] ? NORMAL_COMPLETION_REASON : 0);
      }
    }
    MyVisitor visitor = new MyVisitor();
    depthFirstSearch(flow, visitor, offset, endOffset);

    return visitor.getResult().intValue();
  }

  @NotNull
  public static Collection<VariableInfo> getInitializedTwice(@NotNull ControlFlow flow) {
    return getInitializedTwice(flow, 0, flow.getSize());
  }

  @NotNull
  public static Collection<VariableInfo> getInitializedTwice(@NotNull ControlFlow flow, int startOffset, int endOffset) {
    InitializedTwiceClientVisitor visitor = new InitializedTwiceClientVisitor(flow, startOffset);
    depthFirstSearch(flow, visitor, startOffset, endOffset);
    return visitor.getResult();
  }

  private static class InitializedTwiceClientVisitor extends InstructionClientVisitor<Collection<VariableInfo>> {
    // map of variable->PsiReferenceExpressions for all read and not written variables for this point and below in control flow
    private final CopyOnWriteList[] writtenVariables;
    private final CopyOnWriteList[] writtenTwiceVariables;
    private final ControlFlow myFlow;
    private final int myStartOffset;

    public InitializedTwiceClientVisitor(@NotNull ControlFlow flow, final int startOffset) {
      myFlow = flow;
      myStartOffset = startOffset;
      writtenVariables = new CopyOnWriteList[myFlow.getSize() + 1];
      writtenTwiceVariables = new CopyOnWriteList[myFlow.getSize() + 1];
    }

    @Override
    public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
      final int safeNextOffset = Math.min(nextOffset, myFlow.getSize());

      CopyOnWriteList writeVars = writtenVariables[safeNextOffset];
      CopyOnWriteList writeTwiceVars = writtenTwiceVariables[safeNextOffset];
      if (instruction instanceof WriteVariableInstruction) {
        final PsiVariable variable = ((WriteVariableInstruction)instruction).variable;

        final PsiElement latestWriteVarExpression = getLatestWriteVarExpression(writeVars, variable);

        if (latestWriteVarExpression == null) {
          final PsiElement expression = getExpression(myFlow.getElement(offset));
          writeVars = CopyOnWriteList.add(writeVars, new VariableInfo(variable, expression));
        }
        else {
          writeTwiceVars = CopyOnWriteList.add(writeTwiceVars, new VariableInfo(variable, latestWriteVarExpression));
        }
      }
      merge(offset, writeVars, writtenVariables);
      merge(offset, writeTwiceVars, writtenTwiceVariables);
    }

    @Nullable
    private static PsiElement getExpression(@NotNull PsiElement element) {
      if (element instanceof PsiAssignmentExpression && ((PsiAssignmentExpression)element).getLExpression() instanceof PsiReferenceExpression) {
        return ((PsiAssignmentExpression)element).getLExpression();
      }
      else if (element instanceof PsiPostfixExpression) {
        return ((PsiPostfixExpression)element).getOperand();
      }
      else if (element instanceof PsiPrefixExpression) {
        return ((PsiPrefixExpression)element).getOperand();
      }
      else if (element instanceof PsiDeclarationStatement) {
        //should not happen
        return element;
      }
      return null;
    }

    @Nullable
    private static PsiElement getLatestWriteVarExpression(@Nullable CopyOnWriteList writeVars, @Nullable PsiVariable variable) {
      if (writeVars == null) return null;

      for (final VariableInfo variableInfo : writeVars.getList()) {
        if (variableInfo.variable == variable) {
          return variableInfo.expression;
        }
      }
      return null;
    }

    @Override
    @NotNull
    public Collection<VariableInfo> getResult() {
      final CopyOnWriteList writtenTwiceVariable = writtenTwiceVariables[myStartOffset];
      if (writtenTwiceVariable == null) return Collections.emptyList();
      return writtenTwiceVariable.getList();
    }
  }

  /**
   * @return true if instruction at 'instructionOffset' is reachable from offset 'startOffset'
   */
  public static boolean isInstructionReachable(final ControlFlow flow, final int instructionOffset, final int startOffset) {
    class MyVisitor extends InstructionClientVisitor<Boolean> {
      boolean reachable;

      @Override
      public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
        if (nextOffset == instructionOffset) reachable = true;
      }

      @Override
      public Boolean getResult() {
        return reachable;
      }
    }

    MyVisitor visitor = new MyVisitor();
    depthFirstSearch(flow, visitor, startOffset, flow.getSize());

    return visitor.getResult().booleanValue();
  }

  public static boolean isVariableAssignedInLoop(@NotNull PsiReferenceExpression expression, PsiElement resolved) {
    if (!(expression.getParent() instanceof PsiAssignmentExpression)
        || ((PsiAssignmentExpression)expression.getParent()).getLExpression() != expression) {
      return false;
    }
    PsiExpression qualifier = expression.getQualifierExpression();
    if (qualifier != null && !(qualifier instanceof PsiThisExpression)) return false;

    if (!(resolved instanceof PsiVariable)) return false;
    PsiVariable variable = (PsiVariable)resolved;

    final PsiElement codeBlock = PsiUtil.getVariableCodeBlock(variable, expression);
    if (codeBlock == null) return false;
    final ControlFlow flow;
    try {
      flow = ControlFlowFactory.getInstance(codeBlock.getProject()).getControlFlow(codeBlock, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(), false);
    }
    catch (AnalysisCanceledException e) {
      return false;
    }
    final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression.getParent();
    int startOffset = flow.getStartOffset(assignmentExpression);
    return startOffset != -1 && isInstructionReachable(flow, startOffset, startOffset);
  }
}