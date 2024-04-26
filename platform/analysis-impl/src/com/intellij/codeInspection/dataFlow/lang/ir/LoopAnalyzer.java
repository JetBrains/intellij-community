// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.util.ArrayUtil;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.Graph;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntListIterator;
import org.jetbrains.annotations.NotNull;

import java.util.*;

final class LoopAnalyzer {
  private static final class MyGraph implements Graph<Instruction> {
    private final @NotNull ControlFlow myFlow;
    private final Instruction[] myInstructions;
    private final Int2ObjectMap<int[]> myIns = new Int2ObjectOpenHashMap<>();

    private MyGraph(@NotNull ControlFlow flow) {
      myFlow = flow;
      myInstructions = flow.getInstructions();
      for (Instruction instruction : myInstructions) {
        int fromIndex = instruction.getIndex();
        int[] to = instruction.getSuccessorIndexes();
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

    @Override
    public @NotNull Collection<Instruction> getNodes() {
      return Arrays.asList(myFlow.getInstructions());
    }

    @Override
    public @NotNull Iterator<Instruction> getIn(Instruction n) {
      int[] ins = myIns.get(n.getIndex());
      return indicesToInstructions(ins);
    }

    @Override
    public @NotNull Iterator<Instruction> getOut(Instruction instruction) {
      int[] next = instruction.getSuccessorIndexes();
      return indicesToInstructions(next);
    }

    private @NotNull Iterator<Instruction> indicesToInstructions(int[] next) {
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
    IntList sccs = builder.getSCCs();
    int tNumber = 0;
    int component = 0;
    for (IntListIterator iterator = sccs.iterator(); iterator.hasNext(); ) {
      int size = iterator.nextInt();
      int value = size > 1 ? ++component : 0;
      for (int i = 0; i < size; i++) {
        Instruction instruction = builder.getNodeByTNumber(tNumber + i);
        loop[instruction.getIndex()] = value;
      }
      tNumber += size;
    }
    return loop;
  }
}
