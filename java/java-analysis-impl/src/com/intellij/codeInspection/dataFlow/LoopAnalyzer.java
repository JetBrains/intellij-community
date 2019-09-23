// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.instructions.ConditionalGotoInstruction;
import com.intellij.codeInspection.dataFlow.instructions.ControlTransferInstruction;
import com.intellij.codeInspection.dataFlow.instructions.GotoInstruction;
import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.Graph;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NotNull;

import java.util.*;

class LoopAnalyzer {
  private static class MyGraph implements Graph<Instruction> {
    @NotNull private final ControlFlow myFlow;
    private final Instruction[] myInstructions;
    private final TIntObjectHashMap<int[]> myIns = new TIntObjectHashMap<>();

    private MyGraph(@NotNull ControlFlow flow) {
      myFlow = flow;
      myInstructions = flow.getInstructions();
      for (Instruction instruction : myInstructions) {
        int fromIndex = instruction.getIndex();
        int[] to = getSuccessorIndices(fromIndex, myInstructions);
        for (int toIndex : to) {
          int[] froms = myIns.get(toIndex);
          if (froms == null) {
            froms = new int[]{fromIndex};
          }
          else {
            froms = ArrayUtil.append(froms, fromIndex);
          }
          myIns.put(toIndex, froms);
        }
      }
    }

    @NotNull
    @Override
    public Collection<Instruction> getNodes() {
      return Arrays.asList(myFlow.getInstructions());
    }

    @NotNull
    @Override
    public Iterator<Instruction> getIn(Instruction n) {
      int[] ins = myIns.get(n.getIndex());
      return indicesToInstructions(ins);
    }

    @NotNull
    @Override
    public Iterator<Instruction> getOut(Instruction instruction) {
      int fromIndex = instruction.getIndex();
      int[] next = getSuccessorIndices(fromIndex, myInstructions);
      return indicesToInstructions(next);
    }

    @NotNull
    private Iterator<Instruction> indicesToInstructions(int[] next) {
      if (next == null) return Collections.emptyIterator();
      List<Instruction> out = new ArrayList<>(next.length);
      for (int i : next) {
        out.add(myInstructions[i]);
      }
      return out.iterator();
    }
  }



  static int[] calcInLoop(ControlFlow controlFlow) {
    final int[] loop = new int[controlFlow.getInstructionCount()]; // loop[i] = loop number(strongly connected component number) of i-th instruction or 0 if outside loop

    MyGraph graph = new MyGraph(controlFlow);
    final DFSTBuilder<Instruction> builder = new DFSTBuilder<>(graph);
    TIntArrayList sccs = builder.getSCCs();
    sccs.forEach(new TIntProcedure() {
      private int myTNumber;
      private int component;

      @Override
      public boolean execute(int size) {
        int value = size > 1 ? ++component : 0;
        for (int i = 0; i < size; i++) {
          Instruction instruction = builder.getNodeByTNumber(myTNumber + i);
          loop[instruction.getIndex()] = value;
        }
        myTNumber += size;
        return true;
      }
    });

    return loop;
  }

  @NotNull
  static int[] getSuccessorIndices(int i, Instruction[] myInstructions) {
    Instruction instruction = myInstructions[i];
    if (instruction instanceof GotoInstruction) {
      return new int[]{((GotoInstruction)instruction).getOffset()};
    }
    if (instruction instanceof ControlTransferInstruction) {
      return ArrayUtil.toIntArray(((ControlTransferInstruction)instruction).getPossibleTargetIndices());
    }
    if (instruction instanceof ConditionalGotoInstruction) {
      int offset = ((ConditionalGotoInstruction)instruction).getOffset();
      if (offset != i+1) {
        return new int[]{i + 1, offset};
      }
    }
    return i == myInstructions.length-1 ? ArrayUtilRt.EMPTY_INT_ARRAY : new int[]{i + 1};
  }


}
