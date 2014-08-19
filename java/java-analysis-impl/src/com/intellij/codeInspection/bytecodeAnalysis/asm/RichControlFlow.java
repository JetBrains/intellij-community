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
package com.intellij.codeInspection.bytecodeAnalysis.asm;

import com.intellij.codeInspection.bytecodeAnalysis.asm.ControlFlowGraph.Edge;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIterator;

/**
 * @author lambdamix
 */
public final class RichControlFlow {
  public final ControlFlowGraph controlFlow;
  public final DFSTree dfsTree;

  public RichControlFlow(ControlFlowGraph controlFlow, DFSTree dfsTree) {
    this.controlFlow = controlFlow;
    this.dfsTree = dfsTree;
  }

  // Tarjan. Testing flow graph reducibility.
  // Journal of Computer and System Sciences 9.3 (1974): 355-365.
  public boolean reducible() {
    if (dfsTree.back.isEmpty()) {
      return true;
    }
    int size = controlFlow.transitions.length;
    boolean[] loopEnters = dfsTree.loopEnters;
    TIntHashSet[] cycleIncomings = new TIntHashSet[size];
    // really this may be array, since dfs already ensures no duplicates
    TIntArrayList[] nonCycleIncomings = new TIntArrayList[size];
    int[] collapsedTo = new int[size];
    int[] queue = new int[size];
    int top;
    for (int i = 0; i < size; i++) {
      if (loopEnters[i]) {
        cycleIncomings[i] = new TIntHashSet();
      }
      nonCycleIncomings[i] = new TIntArrayList();
      collapsedTo[i] = i;
    }

    // from whom back connections
    for (Edge edge : dfsTree.back) {
      cycleIncomings[edge.to].add(edge.from);
    }
    // from whom ordinary connections
    for (Edge edge : dfsTree.nonBack) {
      nonCycleIncomings[edge.to].add(edge.from);
    }

    for (int w = size - 1; w >= 0 ; w--) {
      top = 0;
      // NB - it is modified later!
      TIntHashSet p = cycleIncomings[w];
      if (p == null) {
        continue;
      }
      TIntIterator iter = p.iterator();
      while (iter.hasNext()) {
        queue[top++] = iter.next();
      }

      while (top > 0) {
        int x = queue[--top];
        TIntArrayList incoming = nonCycleIncomings[x];
        for (int i = 0; i < incoming.size(); i++) {
          int y1 = collapsedTo[incoming.getQuick(i)];
          if (!dfsTree.isDescendant(y1, w)) {
            return false;
          }
          if (y1 != w && p.add(y1)) {
            queue[top++] = y1;
          }
        }
      }

      iter = p.iterator();
      while (iter.hasNext()) {
        collapsedTo[iter.next()] = w;
      }
    }

    return true;
  }
}
