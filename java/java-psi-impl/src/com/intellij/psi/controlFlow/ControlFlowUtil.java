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
package com.intellij.psi.controlFlow;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.*;
import com.intellij.util.containers.IntArrayList;
import com.intellij.util.containers.IntStack;
import gnu.trove.THashMap;
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
    ArrayList<PsiVariable> result = new ArrayList<>(1);

    variables:
    for (PsiVariable psiVariable : writtenVariables) {

      final List<SSAInstructionState> queue = new ArrayList<>();
      queue.add(new SSAInstructionState(0, from));
      Set<SSAInstructionState> processedStates = new THashSet<>();

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

  public static boolean needVariableValueAt(final PsiVariable variable, final ControlFlow flow, final int offset) {
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
    final HashSet<PsiVariable> set = new HashSet<>();
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
    ArrayList<PsiVariable> array = new ArrayList<>();
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

  public static boolean isVariableUsed(ControlFlow flow, int start, int end, PsiVariable variable) {
    List<Instruction> instructions = flow.getInstructions();
    LOG.assertTrue(start >= 0, "flow start");
    LOG.assertTrue(end <= instructions.size(), "flow end");
    for (int i = start; i < end; i++) {
      Instruction instruction = instructions.get(i);
      if (instruction instanceof ReadVariableInstruction) {
        if (((ReadVariableInstruction)instruction).variable == variable) {
          return true;
        }
      }
      else if (instruction instanceof WriteVariableInstruction) {
        if (((WriteVariableInstruction)instruction).variable == variable) {
          return true;
        }
      }
    }
    return false;
  }

  private static int findSingleReadOffset(@NotNull ControlFlow flow, int startOffset, int endOffset, @NotNull PsiVariable variable) {
    List<Instruction> instructions = flow.getInstructions();
    if (startOffset < 0 || endOffset < 0 || endOffset > instructions.size()) return -1;

    int readOffset = -1;
    for (int i = startOffset; i < endOffset; i++) {
      Instruction instruction = instructions.get(i);
      if (instruction instanceof ReadVariableInstruction) {
        if (((ReadVariableInstruction)instruction).variable == variable) {
          if (readOffset < 0) {
            readOffset = i;
          }
          else {
            return -1;
          }
        }
      }
      else if (instruction instanceof WriteVariableInstruction) {
        if (((WriteVariableInstruction)instruction).variable == variable) {
          return -1;
        }
      }
    }
    return readOffset;
  }

  /**
   * If the variable occurs only once in the element and it's read access return that occurrence
   */
  public static PsiReferenceExpression findSingleReadOccurrence(@NotNull ControlFlow flow,
                                                                @NotNull PsiElement element,
                                                                @NotNull PsiVariable variable) {
    int readOffset = findSingleReadOffset(flow, flow.getStartOffset(element), flow.getEndOffset(element), variable);
    if (readOffset >= 0) {
      PsiElement readElement = flow.getElement(readOffset);
      readElement = PsiTreeUtil.findFirstParent(readElement, false, e -> e == element || e instanceof PsiReferenceExpression);
      if (readElement instanceof PsiReferenceExpression) {
        return (PsiReferenceExpression)readElement;
      }
    }
    return null;
  }

  public static boolean isVariableReadInFinally(@NotNull ControlFlow flow,
                                                @Nullable PsiElement startElement,
                                                @NotNull PsiElement enclosingCodeFragment,
                                                @NotNull PsiVariable variable) {
    for (PsiElement element = startElement; element != null && element != enclosingCodeFragment; element = element.getParent()) {
      if (element instanceof PsiCodeBlock) {
        final PsiElement parent = element.getParent();
        if (parent instanceof PsiTryStatement) {
          final PsiTryStatement tryStatement = (PsiTryStatement)parent;
          if (tryStatement.getTryBlock() == element) {
            final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
            if (finallyBlock != null) {
              final List<Instruction> instructions = flow.getInstructions();
              final int startOffset = flow.getStartOffset(finallyBlock);
              final int endOffset = flow.getEndOffset(finallyBlock);
              LOG.assertTrue(startOffset >= 0, "flow start");
              LOG.assertTrue(endOffset <= instructions.size(), "flow end");
              for (int i = startOffset; i < endOffset; i++) {
                final Instruction instruction = instructions.get(i);
                if (instruction instanceof ReadVariableInstruction && ((ReadVariableInstruction)instruction).variable == variable) {
                  return true;
                }
              }
            }
          }
        }
      }
    }
    return false;
  }

  public static List<PsiVariable> getInputVariables(ControlFlow flow, int start, int end) {
    List<PsiVariable> usedVariables = getUsedVariables(flow, start, end);
    ArrayList<PsiVariable> array = new ArrayList<>(usedVariables.size());
    for (PsiVariable variable : usedVariables) {
      if (needVariableValueAt(variable, flow, start)) {
        array.add(variable);
      }
    }
    return array;
  }

  @NotNull
  public static PsiVariable[] getOutputVariables(ControlFlow flow, int start, int end, int[] exitPoints) {
    Collection<PsiVariable> writtenVariables = getWrittenVariables(flow, start, end, false);
    ArrayList<PsiVariable> array = new ArrayList<>();
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
    final Collection<PsiStatement> exitStatements = new THashSet<>();
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

  /**
   * Detect throw instructions which might affect observable control flow via side effects with local variables.
   *
   * The side effect of exception thrown occurs when a local variable is written in the try block, and then accessed
   * in the finally section or in/after a catch section.
   *
   * Example:
   * <pre>
   * { // --- start of theOuterBlock ---
   *   Status status = STARTED;
   *   try { // --- start of theTryBlock ---
   *     status = PREPARING;
   *     doPrepare(); // may throw exception
   *     status = WORKING;
   *     doWork(); // may throw exception
   *     status = FINISHED;
   *   } // --- end of theTryBlock ---
   *   catch (Exception e) {
   *      LOG.error("Failed when " + status, e); // can get PREPARING or WORKING here
   *   }
   *   if (status == FINISHED) LOG.info("Finished"); // can get PREPARING or WORKING here in the case of exception
   * } // --- end of theOuterBlock ---
   * </pre>
   * In the example above {@code hasObservableThrowExitPoints(theTryBlock) == true},
   * because the resulting value of the "status" variable depends on the exceptions being thrown.
   * In the same example {@code hasObservableThrowExitPoints(theOuterBlock) == false},
   * because no outgoing variables here depend on the exceptions being thrown.
   */
  public static boolean hasObservableThrowExitPoints(final @NotNull ControlFlow flow,
                                                     final int flowStart,
                                                     final int flowEnd,
                                                     @NotNull PsiElement[] elements,
                                                     @NotNull PsiElement enclosingCodeFragment) {
    final List<Instruction> instructions = flow.getInstructions();
    class Worker {
      @NotNull
      private Map<PsiVariable, IntArrayList> getWritesOffsets() {
        final Map<PsiVariable, IntArrayList> writeOffsets = new THashMap<>();
        for (int i = flowStart; i < flowEnd; i++) {
          Instruction instruction = instructions.get(i);
          if (instruction instanceof WriteVariableInstruction) {
            final PsiVariable variable = ((WriteVariableInstruction)instruction).variable;
            if (variable instanceof PsiLocalVariable || variable instanceof PsiParameter) {
              IntArrayList offsets = writeOffsets.get(variable);
              if (offsets == null) writeOffsets.put(variable, offsets = new IntArrayList());
              offsets.add(i);
            }
          }
        }
        LOG.debug("writeOffsets:", writeOffsets);
        return writeOffsets;
      }

      @NotNull
      private Map<PsiVariable, IntArrayList> getVisibleReadsOffsets(Map<PsiVariable, IntArrayList> writeOffsets, PsiCodeBlock tryBlock) {
        final Map<PsiVariable, IntArrayList> visibleReadOffsets = new THashMap<>();
        for (PsiVariable variable : writeOffsets.keySet()) {
          if (!PsiTreeUtil.isAncestor(tryBlock, variable, true)) {
            visibleReadOffsets.put(variable, new IntArrayList());
          }
        }
        if (visibleReadOffsets.isEmpty()) return visibleReadOffsets;

        for (int i = 0; i < instructions.size(); i++) {
          final Instruction instruction = instructions.get(i);
          if (instruction instanceof ReadVariableInstruction) {
            final PsiVariable variable = ((ReadVariableInstruction)instruction).variable;
            final IntArrayList readOffsets = visibleReadOffsets.get(variable);
            if (readOffsets != null) {
              readOffsets.add(i);
            }
          }
        }
        LOG.debug("visibleReadOffsets:", visibleReadOffsets);
        return visibleReadOffsets;
      }

      @NotNull
      private Map<PsiVariable, Set<PsiElement>> getReachableAfterWrite(Map<PsiVariable, IntArrayList> writeOffsets,
                                                                       Map<PsiVariable, IntArrayList> visibleReadOffsets) {
        final Map<PsiVariable, Set<PsiElement>> afterWrite = new THashMap<>();
        for (PsiVariable variable : visibleReadOffsets.keySet()) {
          final Function<Integer, BitSet> calculator = getReachableInstructionsCalculator();
          final BitSet collectedOffsets = new BitSet(flowEnd);
          for (final int writeOffset : writeOffsets.get(variable).toArray()) {
            LOG.assertTrue(writeOffset >= flowStart, "writeOffset");
            final BitSet reachableOffsets = calculator.fun(writeOffset);
            collectedOffsets.or(reachableOffsets);
          }
          Set<PsiElement> throwSources = afterWrite.get(variable);
          if (throwSources == null) afterWrite.put(variable, throwSources = new THashSet<>());
          for (int i = flowStart; i < flowEnd; i++) {
            if (collectedOffsets.get(i)) {
              throwSources.add(flow.getElement(i));
            }
          }
          final List<PsiElement> subordinates = new ArrayList<>();
          for (PsiElement element : throwSources) {
            if (throwSources.contains(element.getParent())) {
              subordinates.add(element);
            }
          }
          throwSources.removeAll(subordinates);
        }
        LOG.debug("afterWrite:", afterWrite);
        return afterWrite;
      }

      @NotNull
      private IntArrayList getCatchOrFinallyOffsets(List<PsiTryStatement> tryStatements, List<PsiClassType> thrownExceptions) {
        final IntArrayList catchOrFinallyOffsets = new IntArrayList();
        for (PsiTryStatement tryStatement : tryStatements) {
          final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
          if (finallyBlock != null) {
            int offset = flow.getStartOffset(finallyBlock);
            if (offset >= 0) {
              catchOrFinallyOffsets.add(offset - 2); // -2 is an adjustment for rethrow-after-finally
            }
          }
          for (PsiCatchSection catchSection : tryStatement.getCatchSections()) {
            final PsiCodeBlock catchBlock = catchSection.getCatchBlock();
            final PsiParameter parameter = catchSection.getParameter();
            if (catchBlock != null && parameter != null) {
              for (PsiClassType throwType : thrownExceptions) {
                if (isCaughtExceptionType(throwType, parameter.getType())) {
                  int offset = flow.getStartOffset(catchBlock);
                  if (offset >= 0) {
                    catchOrFinallyOffsets.add(offset - 1); // -1 is an adjustment for catch block initialization
                  }
                }
              }
            }
          }
        }
        return catchOrFinallyOffsets;
      }

      private boolean isAnyReadOffsetReachableFrom(IntArrayList readOffsets, IntArrayList fromOffsets) {
        if (readOffsets != null && !readOffsets.isEmpty()) {
          final int[] readOffsetsArray = readOffsets.toArray();
          for (int j = 0; j < fromOffsets.size(); j++) {
            int fromOffset = fromOffsets.get(j);
            if (areInstructionsReachable(flow, readOffsetsArray, fromOffset)) {
              LOG.debug("reachableFromOffset:", fromOffset);
              return true;
            }
          }
        }
        return false;
      }

      private Function<Integer, BitSet> getReachableInstructionsCalculator() {
        final ControlFlowGraph graph = new ControlFlowGraph(flow.getSize()) {
          @Override
          void addArc(int offset, int nextOffset) {
            nextOffset = promoteThroughGotoChain(flow, nextOffset);
            if (nextOffset >= flowStart && nextOffset < flowEnd) {
              super.addArc(offset, nextOffset);
            }
          }
        };
        graph.buildFrom(flow);

        return startOffset -> {
          BitSet visitedOffsets = new BitSet(flowEnd);
          graph.depthFirstSearch(startOffset, visitedOffsets);
          return visitedOffsets;
        };
      }
    }

    final Worker worker = new Worker();
    final Map<PsiVariable, IntArrayList> writeOffsets = worker.getWritesOffsets();
    if (writeOffsets.isEmpty()) return false;

    final PsiElement commonParent = elements.length != 1 ? PsiTreeUtil.findCommonParent(elements) : elements[0].getParent();
    final List<PsiTryStatement> tryStatements = collectTryStatementStack(commonParent, enclosingCodeFragment);
    if (tryStatements.isEmpty()) return false;
    final PsiCodeBlock tryBlock = tryStatements.get(0).getTryBlock();
    if (tryBlock == null) return false;

    final Map<PsiVariable, IntArrayList> visibleReadOffsets = worker.getVisibleReadsOffsets(writeOffsets, tryBlock);
    if (visibleReadOffsets.isEmpty()) return false;

    final Map<PsiVariable, Set<PsiElement>> afterWrite = worker.getReachableAfterWrite(writeOffsets, visibleReadOffsets);
    if (afterWrite.isEmpty()) return false;

    for (Map.Entry<PsiVariable, Set<PsiElement>> entry : afterWrite.entrySet()) {
      final PsiVariable variable = entry.getKey();
      final PsiElement[] psiElements = entry.getValue().toArray(PsiElement.EMPTY_ARRAY);
      final List<PsiClassType> thrownExceptions = ExceptionUtil.getThrownExceptions(psiElements);

      if (!thrownExceptions.isEmpty()) {
        final IntArrayList catchOrFinallyOffsets = worker.getCatchOrFinallyOffsets(tryStatements, thrownExceptions);
        if (worker.isAnyReadOffsetReachableFrom(visibleReadOffsets.get(variable), catchOrFinallyOffsets)) {
          return true;
        }
      }
    }
    return false;
  }

  @Nullable
  private static PsiTryStatement getEnclosingTryStatementHavingCatchOrFinally(@Nullable PsiElement startElement,
                                                                              @NotNull PsiElement enclosingCodeFragment) {
    for (PsiElement element = startElement; element != null && element != enclosingCodeFragment; element = element.getParent()) {
      if (element instanceof PsiCodeBlock) {
        final PsiElement parent = element.getParent();
        if (parent instanceof PsiTryStatement) {
          final PsiTryStatement tryStatement = (PsiTryStatement)parent;
          if (tryStatement.getTryBlock() == element &&
              (tryStatement.getFinallyBlock() != null || tryStatement.getCatchBlocks().length != 0)) {
            return tryStatement;
          }
        }
      }
    }
    return null;
  }

  @NotNull
  private static List<PsiTryStatement> collectTryStatementStack(@Nullable PsiElement startElement,
                                                                @NotNull PsiElement enclosingCodeFragment) {
    final List<PsiTryStatement> stack = new ArrayList<>();
    for (PsiTryStatement tryStatement = getEnclosingTryStatementHavingCatchOrFinally(startElement, enclosingCodeFragment);
         tryStatement != null;
         tryStatement = getEnclosingTryStatementHavingCatchOrFinally(tryStatement, enclosingCodeFragment)) {
      stack.add(tryStatement);
    }
    return stack;
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
      myAffectedReturns = new ArrayList<>();
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

  @NotNull
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
          if (element instanceof PsiBreakStatement) {
            PsiStatement exitedStatement = ((PsiBreakStatement)element).findExitedStatement();
            if (exitedStatement == null || flow.getStartOffset(exitedStatement) < startOffset) {
              isNormal = false;
            }
          }
          else if (element instanceof PsiContinueStatement) {
            PsiStatement continuedStatement = ((PsiContinueStatement)element).findContinuedStatement();
            if (continuedStatement == null || flow.getStartOffset(continuedStatement) < startOffset) {
              isNormal = false;
            }
          }
        }
        canCompleteNormally[offset] |= isNormal;
      }

      @Override
      public void visitConditionalThrowToInstruction(ConditionalThrowToInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        if (offset > endOffset) return;
        int throwToOffset = instruction.offset;
        boolean isNormal;
        if (throwToOffset == nextOffset) {
          isNormal = throwToOffset <= endOffset && !isLeaf(nextOffset) && canCompleteNormally[nextOffset];
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

          final PsiElement unreachableParent = getUnreachableExpressionParent(element);
          if (unreachableParent != null) return unreachableParent;

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
          final PsiElement enclosingStatement = getEnclosingUnreachableStatement(element);
          return enclosingStatement != null ? enclosingStatement : element;
        }
      }
      return null;
    }

    @Nullable
    private static PsiElement getUnreachableExpressionParent(@Nullable PsiElement element) {
      if (element instanceof PsiExpression) {
        final PsiElement expression = PsiTreeUtil.findFirstParent(element, e -> !(e.getParent() instanceof PsiParenthesizedExpression));
        if (expression != null) {
          final PsiElement parent = expression.getParent();
          if (parent instanceof PsiExpressionStatement) {
            return getUnreachableStatementParent(parent);
          }
          if (parent instanceof PsiIfStatement && ((PsiIfStatement)parent).getCondition() == expression ||
              parent instanceof PsiSwitchStatement && ((PsiSwitchStatement)parent).getExpression() == expression ||
              parent instanceof PsiWhileStatement && ((PsiWhileStatement)parent).getCondition() == expression ||
              parent instanceof PsiForeachStatement && ((PsiForeachStatement)parent).getIteratedValue() == expression) {
            return parent;
          }
        }
      }
      return null;
    }

    @Nullable
    private static PsiElement getEnclosingUnreachableStatement(@NotNull PsiElement statement) {
      final PsiElement parent = statement.getParent();
      if (parent instanceof PsiDoWhileStatement && ((PsiDoWhileStatement)parent).getBody() == statement) {
        return parent;
      }
      if (parent instanceof PsiCodeBlock && PsiTreeUtil.getNextSiblingOfType(parent.getFirstChild(), PsiStatement.class) == statement) {
        final PsiBlockStatement blockStatement = ObjectUtils.tryCast(parent.getParent(), PsiBlockStatement.class);
        if (blockStatement != null) {
          final PsiElement blockParent = blockStatement.getParent();
          if (blockParent instanceof PsiDoWhileStatement && ((PsiDoWhileStatement)blockParent).getBody() == blockStatement) {
            return blockParent;
          }
        }
      }
      return getUnreachableStatementParent(statement);
    }

    @Nullable
    private static PsiElement getUnreachableStatementParent(@NotNull PsiElement statement) {
      final PsiElement parent = statement.getParent();
      if (parent instanceof PsiForStatement && ((PsiForStatement)parent).getInitialization() == statement) {
        return parent;
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
        && isUnqualified((PsiReferenceExpression)element)
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

  private static boolean isUnqualified(PsiReferenceExpression element) {
    if (element.isQualified()) {
      final PsiExpression qualifierExpression = element.getQualifierExpression();
      return qualifierExpression instanceof PsiThisExpression && ((PsiThisExpression)qualifierExpression).getQualifier() == null;
    }
    return true;
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
   * Returns true if the value the variable has at start is later referenced without going through stop instruction
   *
   * @param flow ControlFlow to analyze
   * @param start the point at which variable value is created
   * @param stop the stop-point
   * @param variable the variable to examine
   * @return true if the value the variable has at start is later referenced without going through stop instruction
   */
  public static boolean isValueUsedWithoutVisitingStop(final ControlFlow flow, final int start, final int stop, final PsiVariable variable) {
    if(start == stop) return false;

    class MyVisitor extends InstructionClientVisitor<Boolean> {
      // true if value the variable has at given offset maybe referenced without going through stop instruction
      final boolean[] maybeReferenced = new boolean[flow.getSize() + 1];

      @Override
      public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
        if (offset == stop) {
          maybeReferenced[offset] = false;
          return;
        }
        if(instruction instanceof WriteVariableInstruction && ((WriteVariableInstruction)instruction).variable == variable) {
          maybeReferenced[offset] = false;
          return;
        }
        if (maybeReferenced[offset]) return;
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();

        boolean nextState = maybeReferenced[nextOffset];
        maybeReferenced[offset] =
          nextState || (instruction instanceof ReadVariableInstruction && ((ReadVariableInstruction)instruction).variable == variable);
      }

      @Override
      public Boolean getResult() {
        return maybeReferenced[start];
      }
    }
    MyVisitor visitor = new MyVisitor();
    depthFirstSearch(flow, visitor, start, flow.getSize());
    return visitor.getResult().booleanValue();
  }

  /**
   * Checks if the control flow instruction at given offset accesses (reads or writes) given variable
   *
   * @param flow control flow
   * @param offset offset inside given control flow
   * @param variable a variable the access to which is to be checked
   * @return true if the given instruction is actually a variable access
   */
  public static boolean isVariableAccess(ControlFlow flow, int offset, PsiVariable variable) {
    Instruction instruction = flow.getInstructions().get(offset);
    return instruction instanceof ReadVariableInstruction && ((ReadVariableInstruction)instruction).variable == variable ||
           instruction instanceof WriteVariableInstruction && ((WriteVariableInstruction)instruction).variable == variable;
  }

  public static class ControlFlowEdge {
    public final int myFrom;
    public final int myTo;

    public ControlFlowEdge(int from, int to) {
      myFrom = from;
      myTo = to;
    }

    @Override
    public String toString() {
      return myFrom+"->"+myTo;
    }
  }

  /**
   * Returns control flow edges which are potentially reachable from start instruction
   *
   * @param flow control flow to analyze
   * @param start starting instruction offset
   * @return a list of edges
   */
  public static List<ControlFlowEdge> getEdges(ControlFlow flow, int start) {
    final List<ControlFlowEdge> list = new ArrayList<>();
    depthFirstSearch(flow, new InstructionClientVisitor<Void>() {
      @Override
      public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
        list.add(new ControlFlowEdge(offset, nextOffset));
      }

      @Override
      public Void getResult() {
        return null;
      }
    }, start, flow.getSize());
    return list;
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

  private static void internalDepthFirstSearch(final List<Instruction> instructions,
                                               final InstructionClientVisitor clientVisitor,
                                               int startOffset,
                                               int endOffset) {

    final WalkThroughStack walkThroughStack = new WalkThroughStack(instructions.size() / 2);
    walkThroughStack.push(startOffset);

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
          for (i = instruction.procBegin;
               i < clientVisitor.processedInstructions.length &&
               (i < instruction.procEnd || i < instructions.size() && instructions.get(i) instanceof ReturnInstruction); i++) {
            clientVisitor.processedInstructions[i] = false;
          }
          clientVisitor.procedureEntered(instruction.procBegin, i);
          walkThroughStack.push(offset, newOffset);
          walkThroughStack.push(newOffset);

          currentProcedureReturnOffsets.add(offset + 1);
        }

        @Override
        public void visitReturnInstruction(ReturnInstruction instruction, int offset, int nextOffset) {
          int newOffset = instruction.execute(false);
          if (newOffset != -1) {
            walkThroughStack.push(offset, newOffset);
            walkThroughStack.push(newOffset);
          }
        }

        @Override
        public void visitBranchingInstruction(BranchingInstruction instruction, int offset, int nextOffset) {
          int newOffset = instruction.offset;
          walkThroughStack.push(offset, newOffset);
          walkThroughStack.push(newOffset);
        }

        @Override
        public void visitConditionalBranchingInstruction(ConditionalBranchingInstruction instruction, int offset, int nextOffset) {
          int newOffset = instruction.offset;

          walkThroughStack.push(offset, newOffset);
          walkThroughStack.push(offset, offset + 1);
          walkThroughStack.push(newOffset);
          walkThroughStack.push(offset + 1);
        }

        @Override
        public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
          int newOffset = offset + 1;
          walkThroughStack.push(offset, newOffset);
          walkThroughStack.push(newOffset);
        }
      };
      while (!walkThroughStack.isEmpty()) {
        final int offset = walkThroughStack.peekOldOffset();
        final int newOffset = walkThroughStack.popNewOffset();

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

  private static class WalkThroughStack {
    private int[] oldOffsets;
    private int[] newOffsets;
    private int size;

    WalkThroughStack(int initialSize) {
      if (initialSize < 2) initialSize = 2;
      oldOffsets = new int[initialSize];
      newOffsets = new int[initialSize];
    }

    /**
     * Push an arc of the graph (oldOffset -> newOffset)
     */
    void push(int oldOffset, int newOffset) {
      if (size >= newOffsets.length) {
        oldOffsets = ArrayUtil.realloc(oldOffsets, size * 3 / 2);
        newOffsets = ArrayUtil.realloc(newOffsets, size * 3 / 2);
      }
      oldOffsets[size] = oldOffset;
      newOffsets[size] = newOffset;
      size++;
    }

    /**
     * Push a node of the graph. The node is represented as an arc with newOffset==-1
     */
    void push(int offset) {
      push(offset, -1);
    }

    /**
     * Should be used in pair with {@link #popNewOffset()}
     */
    int peekOldOffset() {
      return oldOffsets[size - 1];
    }

    /**
     * Should be used in pair with {@link #peekOldOffset()}
     */
    int popNewOffset() {
      return newOffsets[--size];
    }

    boolean isEmpty() {
      return size == 0;
    }

    @Override
    public String toString() {
      StringBuilder s = new StringBuilder();
      for (int i = 0; i < size; i++) {
        if (s.length() != 0) s.append(' ');
        if (newOffsets[i] != -1) {
          s.append('(').append(oldOffsets[i]).append("->").append(newOffsets[i]).append(')');
        }
        else {
          s.append('[').append(oldOffsets[i]).append(']');
        }
      }
      return s.toString();
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
      this(Collections.emptyList());
    }

    public CopyOnWriteList(VariableInfo... infos) {
      this(Arrays.asList(infos));
    }

    public CopyOnWriteList(Collection<VariableInfo> infos) {
      list = new LinkedList<>(infos);
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
    return getReadBeforeWrite(flow, 0);
  }

  public static List<PsiReferenceExpression> getReadBeforeWrite(ControlFlow flow, int startOffset) {
    if (startOffset < 0 || startOffset >= flow.getSize()) {
      return Collections.emptyList();
    }
    final ReadBeforeWriteClientVisitor visitor = new ReadBeforeWriteClientVisitor(flow, false);
    depthFirstSearch(flow, visitor);
    return visitor.getResult(startOffset);
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
      return getResult(0);
    }

    public List<PsiReferenceExpression> getResult(int startOffset) {
      final CopyOnWriteList topReadVariables = readVariables[startOffset];
      if (topReadVariables == null) return Collections.emptyList();

      final List<PsiReferenceExpression> result = new ArrayList<>();
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
  public static boolean isInstructionReachable(@NotNull final ControlFlow flow, final int instructionOffset, final int startOffset) {
    return areInstructionsReachable(flow, new int[]{instructionOffset}, startOffset);
  }

  private static boolean areInstructionsReachable(@NotNull final ControlFlow flow,
                                                 @NotNull final int[] instructionOffsets,
                                                 final int startOffset) {
    class MyVisitor extends InstructionClientVisitor<Boolean> {
      boolean reachable;

      @Override
      public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
        reachable |= ArrayUtil.indexOf(instructionOffsets, nextOffset) >= 0;
      }

      @Override
      public Boolean getResult() {
        return reachable;
      }
    }

    if (startOffset != 0 && hasCalls(flow)) {
      // Additional computations are required to take into account CALL and RETURN instructions in the case where
      // the start offset isn't the beginning of the control flow, because we couldn't know the correct state
      // of the call stack if we started traversal of the control flow from an offset in the middle.
      return areInstructionsReachableWithCalls(flow, instructionOffsets, startOffset);
    }
    MyVisitor visitor = new MyVisitor();
    depthFirstSearch(flow, visitor, startOffset, flow.getSize());

    return visitor.getResult().booleanValue();
  }

  private static boolean hasCalls(ControlFlow flow) {
    for (Instruction instruction : flow.getInstructions()) {
      if (instruction instanceof CallInstruction) {
        return true;
      }
    }
    return false;
  }

  private abstract static class ControlFlowGraph extends InstructionClientVisitor<Void> {
    // The graph is sparse: simple instructions have 1 next offset, branching - 2 next offsets, RETURN may have many (one per call)
    final int[][] nextOffsets;

    ControlFlowGraph(int size) {
      nextOffsets = new int[size][];
    }

    @Override
    public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
      if (nextOffset > size()) nextOffset = size();
      addArc(offset, nextOffset);
    }

    void addArc(int offset, int nextOffset) {
      if (nextOffsets[offset] == null) {
        nextOffsets[offset] = new int[]{nextOffset, -1};
      }
      else {
        int[] targets = nextOffsets[offset];
        if (ArrayUtil.indexOf(targets, nextOffset) < 0) {
          int freeIndex = ArrayUtil.indexOf(targets, -1);
          if (freeIndex >= 0) {
            targets[freeIndex] = nextOffset;
          }
          else {
            int oldLength = targets.length;
            nextOffsets[offset] = targets = ArrayUtil.realloc(targets, oldLength * 3 / 2);
            Arrays.fill(targets, oldLength, targets.length, -1);
            targets[oldLength] = nextOffset;
          }
        }
      }
    }

    @NotNull
    int[] getNextOffsets(int offset) {
      return nextOffsets[offset] != null ? nextOffsets[offset] : ArrayUtil.EMPTY_INT_ARRAY;
    }

    int size() {
      return nextOffsets.length;
    }

    @Override
    public String toString() {
      StringBuilder s = new StringBuilder();
      for (int i = 0; i < nextOffsets.length; i++) {
        int[] targets = nextOffsets[i];
        if (targets != null && targets.length != 0 && targets[0] != -1) {
          if (s.length() != 0) s.append(' ');
          s.append('(').append(i).append("->");
          for (int j = 0; j < targets.length && targets[j] != -1; j++) {
            if (j != 0) s.append(",");
            s.append(targets[j]);
          }
          s.append(')');
        }
      }
      return s.toString();
    }

    boolean depthFirstSearch(final int startOffset) {
      return depthFirstSearch(startOffset, new BitSet(size()));
    }

    boolean depthFirstSearch(final int startOffset, final BitSet visitedOffsets) {
      // traverse the graph starting with the startOffset
      IntStack walkThroughStack = new IntStack(Math.max(size() / 2, 2));
      visitedOffsets.clear();
      walkThroughStack.push(startOffset);
      while (!walkThroughStack.empty()) {
        int currentOffset = walkThroughStack.pop();
        if (currentOffset < size() && !visitedOffsets.get(currentOffset)) {
          visitedOffsets.set(currentOffset);
          int[] nextOffsets = getNextOffsets(currentOffset);
          for (int nextOffset : nextOffsets) {
            if (nextOffset == -1) break;
            if (isComplete(currentOffset, nextOffset)) {
              return true;
            }
            walkThroughStack.push(nextOffset);
          }
        }
      }
      return false;
    }

    @Override
    public Void getResult() {
      return null;
    }

    boolean isComplete(int offset, int nextOffset) {
      return false;
    }

    void buildFrom(ControlFlow flow) {
      // traverse the whole flow in order to collect the graph edges
      ControlFlowUtil.depthFirstSearch(flow, this, 0, flow.getSize());
    }
  }

  private static boolean areInstructionsReachableWithCalls(@NotNull final ControlFlow flow,
                                                           @NotNull final int[] instructionOffsets,
                                                           final int startOffset) {
    ControlFlowGraph graph = new ControlFlowGraph(flow.getSize()) {
      boolean isComplete(int offset, int nextOffset) {
        return ArrayUtil.indexOf(instructionOffsets, nextOffset) >= 0;
      }
    };
    graph.buildFrom(flow);
    return graph.depthFirstSearch(startOffset);
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

  public static boolean isCaughtExceptionType(@NotNull PsiClassType throwType, @NotNull PsiType catchType) {
    return catchType.isAssignableFrom(throwType) || mightBeAssignableFromSubclass(throwType, catchType);
  }

  private static boolean mightBeAssignableFromSubclass(@NotNull final PsiClassType throwType, @NotNull PsiType catchType) {
    if (catchType instanceof PsiDisjunctionType) {
      for (PsiType catchDisjunction : ((PsiDisjunctionType)catchType).getDisjunctions()) {
        if (throwType.isAssignableFrom(catchDisjunction)) {
          return true;
        }
      }
      return false;
    }
    return throwType.isAssignableFrom(catchType);
  }
}