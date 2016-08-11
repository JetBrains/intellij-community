/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.instructions.ConditionalGotoInstruction;
import com.intellij.codeInspection.dataFlow.instructions.GotoInstruction;
import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.codeInspection.dataFlow.instructions.ReturnInstruction;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.EmptyIterator;
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
        int[] to = next(fromIndex, myInstructions);
        for (int toIndex : to) {
          int[] froms = myIns.get(toIndex);
          if (froms == null) {
            froms = new int[]{fromIndex};
            myIns.put(toIndex, froms);
          }
          else {
            froms = ArrayUtil.append(froms, fromIndex);
            myIns.put(toIndex, froms);
          }
        }
      }
    }

    @Override
    public Collection<Instruction> getNodes() {
      return Arrays.asList(myFlow.getInstructions());
    }

    @Override
    public Iterator<Instruction> getIn(Instruction n) {
      int[] ins = myIns.get(n.getIndex());
      return indicesToInstructions(ins);
    }

    @Override
    public Iterator<Instruction> getOut(Instruction instruction) {
      int fromIndex = instruction.getIndex();
      int[] next = next(fromIndex, myInstructions);
      return indicesToInstructions(next);
    }

    @NotNull
    private Iterator<Instruction> indicesToInstructions(int[] next) {
      if (next == null) return EmptyIterator.getInstance();
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
  private static int[] next(int i, Instruction[] myInstructions) {
    Instruction instruction = myInstructions[i];
    if (instruction instanceof GotoInstruction) {
      return new int[]{((GotoInstruction)instruction).getOffset()};
    }
    if (instruction instanceof ReturnInstruction) {
      return ArrayUtil.EMPTY_INT_ARRAY;
    }
    if (instruction instanceof ConditionalGotoInstruction) {
      int offset = ((ConditionalGotoInstruction)instruction).getOffset();
      if (offset != i+1) {
        return new int[]{i + 1, offset};
      }
    }
    return i == myInstructions.length-1 ? ArrayUtil.EMPTY_INT_ARRAY : new int[]{i + 1};
  }


}
